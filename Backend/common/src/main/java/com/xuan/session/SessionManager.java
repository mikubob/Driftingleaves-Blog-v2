package com.xuan.session;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.xuan.exception.BusinessException;
import com.xuan.utils.TokenUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Clock;
import java.util.Objects;

/** 本站会话生命周期管理器；维护 Token 级最近认证、会话撤销及数据库失败时的签发补偿。 */
@Component
@RequiredArgsConstructor
public class SessionManager {
    private final Clock clock;

    /** 需在用户行锁事务中调用；提交失败时撤销已创建 Token。 */
    public String create(long userId, long authenticatedAt, String method) {
        // 1. 预先生成本次 Token，使后续签发或数据库提交失败时能定位并清理。
        String token = TokenUtils.random();
        // 2. 在签发前登记事务完成回调，数据库未提交则撤销本次会话。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                // 仅在回滚或提交结果未确认时补偿，不影响已经成功提交的 Token。
                if (status != STATUS_COMMITTED) {
                    StpUtil.logoutByTokenValue(token);
                }
            }
        });
        // 3. 创建绝对有效 7200 秒、不滑动续期且不复用的本站会话。
        StpUtil.getStpLogic().createLoginSession(userId, new SaLoginParameter().setToken(token)
                .setTimeout(7200).setActiveTimeout(-1).setIsShare(false));
        // 4. 最近认证时间和认证方式保存在 Token 级会话，多端之间互不刷新。
        SaSession session = StpUtil.getStpLogic().getTokenSessionByToken(token);
        session.set("authenticatedAt", authenticatedAt);
        session.set("method", method);
        return token;
    }

    public long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    public String currentToken() {
        return StpUtil.getTokenValue();
    }

    /** 写事务在取得用户锁后再次确认 Token 尚有效。 */
    public void requireActive(String token, long userId) {
        Object actual = StpUtil.getStpLogic().getLoginIdByToken(token);
        if (actual == null || !actual.toString().equals(Long.toString(userId))) {
            throw new BusinessException(401, "UNAUTHENTICATED", "请重新登录");
        }
    }

    /** 原 Token 与认证信息均重新查询，不能仅凭浏览器 Cookie 恢复身份。 */
    public long requireRecent(String token, long userId, boolean requireOAuth) {
        // 1. 重查 Token 当前归属及其会话，先排除已注销或属于其他用户的凭据。
        Object loginId = StpUtil.getStpLogic().getLoginIdByToken(token);
        SaSession session = loginId == null ? null : StpUtil.getStpLogic().getTokenSessionByToken(token, false);
        if (loginId == null || !Objects.equals(loginId.toString(), Long.toString(userId)) || session == null) {
            throw new BusinessException(401, "UNAUTHENTICATED", "请重新登录");
        }
        // 2. 按当前 Token 的认证时间检查五分钟窗口，必要时限定为 OAuth 认证。
        Object value = session.get("authenticatedAt");
        long at = value instanceof Number number ? number.longValue() : 0;
        long age = clock.millis() - at;
        if (age < 0 || age > 300_000 || (requireOAuth && !"OAUTH".equals(session.get("method")))) {
            throw new BusinessException(403, "REAUTH_REQUIRED", "请在五分钟内重新认证");
        }
        return at;
    }

    /** OAuth 只持有会话摘要，通过用户会话集合寻找仍有效的原 Token。 */
    public String originalToken(long userId, String tokenHash) {
        // 1. 只在目标用户现存会话中匹配摘要，不从 Cookie 或客户端 userId 恢复身份。
        for (String token : StpUtil.getTokenValueListByLoginId(userId)) {
            if (TokenUtils.digest(token).equals(tokenHash)) {
                // 2. 命中后仍重查归属和最近认证，摘要匹配不能代替会话有效性检查。
                requireRecent(token, userId, false);
                return token;
            }
        }
        throw new BusinessException(401, "UNAUTHENTICATED", "请重新登录");
    }

    public void revokeAll(long userId) {
        StpUtil.logout(userId);
    }

    public void logout() {
        // 只撤销 Header 中提供的当前 Token；缺失或已失效保持幂等。
        String token = currentToken();
        if (token != null && !token.isBlank()) {
            StpUtil.logoutByTokenValue(token);
        }
    }

}
