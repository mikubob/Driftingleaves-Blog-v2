package com.xuan.session;

import cn.hutool.core.util.StrUtil;
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
 * 登录会话管理，处理 Token、最近认证和退出登录，数据库提交失败时撤销新 Token。
 */
@Component
@RequiredArgsConstructor
public class SessionManager {

    public static final int BLOG_TIMEOUT_SECONDS = 3 * 24 * 60 * 60;
    public static final int ADMIN_TIMEOUT_SECONDS = 7 * 24 * 60 * 60;

    private final Clock clock;

    /** 创建登录 Token，调用前必须在事务中锁住用户，事务未提交成功时撤销新 Token。 */
    public String create(long userId, long authenticatedAt, String method, int timeoutSeconds) {
        // 1. 先生成本次 Token，后面签发或提交出错时才能找到它并清理。
        String token = TokenUtils.random();

        // 2. 签发前先登记事务回调，数据库没提交就撤销本次会话。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 事务未成功提交时，撤销本次创建的 Token。
             */
            @Override
            public void afterCompletion(int status) {
                // 只有回滚或提交结果不确定时才撤销，已经提交成功的 Token 保留。
                if (status != STATUS_COMMITTED) {
                    StpUtil.logoutByTokenValue(token);
                }
            }
        });

        // 3. 按登录端设置固定有效期，不滑动续期，也不复用旧 Token。
        StpUtil.getStpLogic().createLoginSession(userId, new SaLoginParameter().setToken(token)
                .setTimeout(timeoutSeconds).setActiveTimeout(-1).setIsShare(false));

        // 4. 认证时间和方式跟着各自的 Token 保存，不能用一端登录刷新另一端的认证时间。
        SaSession session = StpUtil.getStpLogic().getTokenSessionByToken(token);
        session.set("authenticatedAt", authenticatedAt);
        session.set("method", method);
        return token;
    }

    /**
     * 获取当前登录用户id，未登录时由 Sa-Token 拒绝。
     */
    public long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /**
     * 获取当前请求的 Token，不使用客户端 userId 判断身份。
     */
    public String currentToken() {
        return StpUtil.getTokenValue();
    }

    /** 检查后台登录，只接受后台账密或邮箱认证，前台会话不能代替。 */
    public void requireAdminAuthentication() {
        String token = currentToken();
        SaSession session = token == null ? null : StpUtil.getStpLogic().getTokenSessionByToken(token, false);
        Object method = session == null ? null : session.get("method");
        if (!"ADMIN_PASSWORD".equals(method) && !"ADMIN_EMAIL".equals(method)) {
            throw new BusinessException(403, "FORBIDDEN", "请使用账号密码或邮箱验证码登录后台");
        }
    }

    /** 检查原 Token 是否有效，写事务必须在拿到用户锁后调用。 */
    public void requireActive(String token, long userId) {
        Object actual = StpUtil.getStpLogic().getLoginIdByToken(token);
        if (actual == null || !actual.toString().equals(Long.toString(userId))) {
            throw new BusinessException(401, "UNAUTHENTICATED", "请重新登录");
        }
    }

    /** 检查最近认证，重新核对原 Token 和认证信息，不能只依赖浏览器 Cookie。 */
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

    /** 根据 Token 摘要查找原会话，只接受该用户仍有效的 Token。 */
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

    /** 检查长连接的原登录会话，不延长有效期，不要求五分钟内重新认证。 */
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

    /** 获取会话剩余秒数，在线心跳不延长登录时间。 */
    public long remainingSeconds(String token) {
        return StpUtil.getStpLogic().getTokenTimeout(token);
    }

    /**
     * 撤销用户的全部登录会话，安全设置变更后需要重新登录。
     */
    public void revokeAll(long userId) {
        StpUtil.logout(userId);
    }

    /**
     * 退出当前登录，未传 Token 或 Token 已失效时也返回成功。
     */
    public void logout() {
        // 只撤销 Header 里的当前 Token；没传或早已失效时也算退出成功。
        String token = currentToken();
        if (!StrUtil.nullToEmpty(token).isBlank()) {
            StpUtil.logoutByTokenValue(token);
        }
    }

}
