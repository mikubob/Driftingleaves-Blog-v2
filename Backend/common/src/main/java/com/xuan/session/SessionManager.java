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

/**
 * 这里管理本站 Token、最近认证和退出登录，数据库提交失败时也要撤销刚发出的 Token。
 */
@Component
@RequiredArgsConstructor
public class SessionManager {

    private final Clock clock;

    /** 调用前要在事务里锁住用户；数据库没提交成功，就撤销这次创建的 Token。 */
    public String create(long userId, long authenticatedAt, String method) {
        // 1. 先生成本次 Token，后面签发或提交出错时才能找到它并清理。
        String token = TokenUtils.random();

        // 2. 签发前先登记事务回调，数据库没提交就撤销本次会话。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void afterCompletion(int status) {
                // 只有回滚或提交结果不确定时才撤销，已经提交成功的 Token 保留。
                if (status != STATUS_COMMITTED) {
                    StpUtil.logoutByTokenValue(token);
                }
            }
        });

        // 3. 每次创建独立 Token，固定有效 7200 秒，不滑动续期，也不复用旧 Token。
        StpUtil.getStpLogic().createLoginSession(userId, new SaLoginParameter().setToken(token)
                .setTimeout(7200).setActiveTimeout(-1).setIsShare(false));

        // 4. 认证时间和方式跟着各自的 Token 保存，不能用一端登录刷新另一端的认证时间。
        SaSession session = StpUtil.getStpLogic().getTokenSessionByToken(token);
        session.set("authenticatedAt", authenticatedAt);
        session.set("method", method);
        return token;
    }

    /**
     * 读取当前登录用户 ID；无有效登录时由 Sa-Token 拒绝。
     */
    public long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /**
     * 读取当前请求的本站 Token，不从客户端 userId 推断身份。
     */
    public String currentToken() {
        return StpUtil.getTokenValue();
    }

    /** 写事务拿到用户锁后，再确认原 Token 还有效。 */
    public void requireActive(String token, long userId) {
        Object actual = StpUtil.getStpLogic().getLoginIdByToken(token);
        if (actual == null || !actual.toString().equals(Long.toString(userId))) {
            throw new BusinessException(401, "UNAUTHENTICATED", "请重新登录");
        }
    }

    /** 原 Token 和认证信息都要重查，不能只靠浏览器 Cookie 恢复身份。 */
    public long requireRecent(String token, long userId, boolean requireOAuth) {
        // 1. 先重查 Token 归属和会话，已退出或属于别人的 Token 都不能用。
        Object loginId = StpUtil.getStpLogic().getLoginIdByToken(token);
        SaSession session = loginId == null ? null : StpUtil.getStpLogic().getTokenSessionByToken(token, false);
        if (loginId == null || !Objects.equals(loginId.toString(), Long.toString(userId)) || session == null) {
            throw new BusinessException(401, "UNAUTHENTICATED", "请重新登录");
        }

        // 2. 再看这枚 Token 是否在五分钟内认证过，需要时还要确认它来自 OAuth 登录。
        Object value = session.get("authenticatedAt");
        long at = value instanceof Number number ? number.longValue() : 0;
        long age = clock.millis() - at;
        if (age < 0 || age > 300_000 || (requireOAuth && !"OAUTH".equals(session.get("method")))) {
            throw new BusinessException(403, "REAUTH_REQUIRED", "请在五分钟内重新认证");
        }
        return at;
    }

    /** OAuth 流程只保存 Token 摘要，回到用户的会话里找仍然有效的原 Token。 */
    public String originalToken(long userId, String tokenHash) {
        // 1. 只查这个用户现有的会话，不能从 Cookie 或客户端 userId 恢复身份。
        for (String token : StpUtil.getTokenValueListByLoginId(userId)) {
            if (TokenUtils.digest(token).equals(tokenHash)) {
                // 2. 找到摘要后仍要重查归属和最近认证，摘要相同不代表会话还有效。
                requireRecent(token, userId, false);
                return token;
            }
        }
        throw new BusinessException(401, "UNAUTHENTICATED", "请重新登录");
    }

    /** 长连接按原会话引用核验，不刷新有效期，也不要求五分钟内重新认证。 */
    public void requireOriginalActive(long userId, String tokenHash, long expiresAt) {
        if (clock.millis() >= expiresAt) {
            throw new BusinessException(401, "UNAUTHENTICATED", "请重新登录");
        }
        for (String token : StpUtil.getTokenValueListByLoginId(userId)) {
            if (TokenUtils.digest(token).equals(tokenHash)) {
                requireActive(token, userId);
                return;
            }
        }
        throw new BusinessException(401, "UNAUTHENTICATED", "请重新登录");
    }

    /** 只读取剩余秒数，在线心跳不能延长本站会话。 */
    public long remainingSeconds(String token) {
        return StpUtil.getStpLogic().getTokenTimeout(token);
    }

    /**
     * 撤销该用户的所有本站会话，安全变更后要求重新登录。
     */
    public void revokeAll(long userId) {
        StpUtil.logout(userId);
    }

    /**
     * 只注销当前 Token；未传或已失效时也按退出成功处理。
     */
    public void logout() {
        // 只撤销 Header 里的当前 Token；没传或早已失效时也算退出成功。
        String token = currentToken();
        if (token != null && !token.isBlank()) {
            StpUtil.logoutByTokenValue(token);
        }
    }

}
