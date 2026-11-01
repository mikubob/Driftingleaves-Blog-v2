package com.xuan.service.impl;

import cn.hutool.core.util.StrUtil;
import com.xuan.client.OAuthProviderClient;
import com.xuan.dto.OAuthAuthorizeDTO.Operation;
import com.xuan.exception.BusinessException;
import com.xuan.model.OAuthFlow;
import com.xuan.model.OAuthTicket;
import com.xuan.properties.AuthProperties;
import com.xuan.service.IOAuthService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.store.RedisOAuthStore;
import com.xuan.utils.TokenUtils;
import com.xuan.vo.OAuthAuthorizeVO;
import com.xuan.vo.OAuthBindResultVO;
import com.xuan.vo.OAuthBindingVO;
import com.xuan.vo.OAuthLoginResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * 平台验证和本站会话分开处理，先一次性消费流程再换码或写库，失败不把票据放回去。
 */
@Service
@RequiredArgsConstructor
public class OAuthServiceImpl implements IOAuthService {

    private final OAuthProviderClient providers;
    private final RedisOAuthStore store;
    private final IUserService users;
    private final SessionManager sessions;
    private final AuthProperties properties;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Clock clock;

    /**
     * 创建 300 秒有效的一次性流程，关联浏览器、平台和操作；绑定还要存当前用户、原 Token 摘要和认证时间，不能自选目标用户。
     *
     * @param source 服务端启用的平台标识
     * @param operation 登录或绑定操作
     * @param browser HTTP 入口提供的浏览器随机标识，仅用于关联流程
     * @return 平台授权地址及流程有效秒数
     */
    @Override
    public OAuthAuthorizeVO authorize(String source, Operation operation, String browser) {
        // 1. 先确认平台配置齐全，才能发起授权。
        providers.requireEnabled(source);
        Long userId = null;
        String tokenHash = null;
        long authenticatedAt = 0;

        // 2. 绑定记录当前用户、原 Token 摘要和最近认证时间，登录流程不借用本站身份。
        if (operation == Operation.BIND) {
            userId = users.requireCurrent();
            authenticatedAt = sessions.requireRecent(sessions.currentToken(), userId, false);
            tokenHash = TokenUtils.digest(sessions.currentToken());
        }

        // 3. 生成不可预测的 state，关联当前浏览器和操作，同一浏览器可以同时有多个流程。
        String state = TokenUtils.random();
        OAuthFlow flow = new OAuthFlow(TokenUtils.digest(browser), source, operation, userId, tokenHash,
                authenticatedAt, clock.millis());

        // 4. JustAuth 的 state 缓存照常保留，另存 300 秒有效的本站一次性流程。
        String url = providers.authorize(source, state);
        store.save("state", state, json.writeValueAsString(flow), Duration.ofSeconds(300));
        return new OAuthAuthorizeVO(url, 300);
    }

    /**
     * 核对并一次性消费流程后再取平台身份，识别已有绑定或绑定当前用户；可信流程成功发 60 秒 ticket，失败跳固定错误页，不可信关系直接报错，最后清理 JustAuth state，不能恢复流程。
     *
     * @param source 回调路径中的平台标识
     * @param state 平台返回的流程随机值
     * @param code 平台授权码，取消或失败时可缺失
     * @param error 平台取消或失败标记，仅判断是否存在，不透传其内容
     * @param browser 当前浏览器 Cookie 中的随机标识
     * @return 固定前端完成页地址，fragment 仅包含 ticket 或允许的业务错误码
     */
    @Override
    public String callback(String source, String state, String code, String error, String browser) {
        // 1. 先查平台、浏览器和流程关联，不匹配时不能消费合法 state。
        providers.requireEnabled(source);
        String raw = store.read("state", state);
        OAuthFlow flow = json.readValue(raw, OAuthFlow.class);
        requireBrowser(flow.browserHash(), browser);
        if (!source.equals(flow.source())) {
            throw RedisOAuthStore.invalid();
        }

        // 2. 绑定按服务端保存的摘要重查原会话，平台回调不用带本站 Bearer。
        if (flow.operation() == Operation.BIND) {
            sessions.originalToken(flow.userId(), flow.tokenHash());
        }

        // 3. 换码前先一次性消费本站流程，并发或重放不能再调一次平台。
        store.consume("state", state, raw);
        try {
            // 4. 平台取消也要消耗这次流程，失败后重新发起。
            if (error != null) {
                throw new BusinessException(400, "OAUTH_DENIED", "平台授权已取消");
            }

            // 4. 由平台验证授权码并返回身份，异常只转成去掉敏感信息的业务错误。
            OAuthProviderClient.Identity identity;
            try {
                identity = providers.verify(source, state, code);
            } catch (DataAccessException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new BusinessException(502, "OAUTH_UPSTREAM_ERROR", "第三方授权失败，请重新发起");
            }

            // 4. 这里只查身份是否合法，合法 uuid 原样保留，不能去空白或改大小写。
            if (identity == null || StrUtil.nullToEmpty(identity.uuid()).isBlank()
                    || identity.uuid().codePointCount(0, identity.uuid().length()) > 255) {
                throw new BusinessException(502, "OAUTH_UPSTREAM_ERROR", "第三方身份无效");
            }

            // 5. 取得平台身份时记下认证时间，再执行本站身份识别或绑定事务。
            long at = clock.millis();
            long userId;
            OAuthBindingVO binding = null;
            if (flow.operation() == Operation.LOGIN) {
                userId = users.resolveOAuth(source, identity.uuid(), identity.nickname(), identity.avatar());
            } else {
                userId = flow.userId();
                binding = users.bindOAuth(userId, flow.tokenHash(), source, identity.uuid());
            }

            // 6. 只发 60 秒有效的结果票据，再跳固定完成页，URL 里不能带本站 Token。
            String ticket = TokenUtils.random();
            OAuthTicket result = new OAuthTicket(flow.browserHash(), flow.source(), flow.operation(), userId,
                    flow.tokenHash(), flow.operation() == Operation.LOGIN ? at : flow.authenticatedAt(), binding,
                    identity.uuid());
            store.save("ticket", ticket, json.writeValueAsString(result), Duration.ofSeconds(60));
            return properties.getFrontendCompleteUrl() + "#ticket=" + ticket;
        } catch (BusinessException exception) {
            return properties.getFrontendCompleteUrl() + "#error=" + exception.getCode();
        } catch (DataAccessException | TransactionException exception) {
            // 可信流程消费后，业务或提交失败都跳固定完成页，不能再发票据。
            return properties.getFrontendCompleteUrl() + "#error=SERVICE_UNAVAILABLE";
        } finally {
            // 7. 成功和失败都要清掉 JustAuth 校验值，已消费的本站流程不能恢复。
            store.deleteState(state);
        }
    }

    /**
     * 核对浏览器后一次性消费结果票据；绑定必须带发起时同一枚有效 Token，登录在用户锁内重查绑定再发会话，失败或响应丢失都不恢复票据。
     *
     * @param ticket 固定完成页交回的一次性结果票据
     * @param browser 当前浏览器 Cookie 中的随机标识
     * @param timeoutSeconds 兑换端决定的本站会话有效秒数，不接受前端指定
     * @return LOGIN 的本站会话结果，或 BIND 的绑定结果
     */
    @Override
    public Object exchange(String ticket, String browser, int timeoutSeconds) {
        // 1. 先取未过期票据并核对浏览器，不能让其他浏览器消耗结果。
        String raw = store.read("ticket", ticket);
        OAuthTicket result = json.readValue(raw, OAuthTicket.class);
        requireBrowser(result.browserHash(), browser);

        // 2. 绑定必须带发起时同一枚 Token，就算是同一用户的另一枚也不行。
        if (result.operation() == Operation.BIND) {
            String token = sessions.currentToken();
            if (token == null || !Objects.equals(result.tokenHash(), TokenUtils.digest(token))) {
                throw RedisOAuthStore.invalid();
            }
            sessions.requireActive(token, result.userId());
        }

        // 3. 一次性消费后才处理兑换，响应丢失也不能重复用票据。
        store.consume("ticket", ticket, raw);

        // 4. 登录在用户锁内重查绑定并发会话，绑定只核对身份、返回绑定结果。
        if (result.operation() == Operation.LOGIN) {
            if (timeoutSeconds != SessionManager.BLOG_TIMEOUT_SECONDS) {
                throw new BusinessException(403, "FORBIDDEN", "后台登录须同时验证密码和邮箱");
            }
            return new OAuthLoginResultVO("LOGIN",
                    users.loginOAuth(result.userId(), result.source(), result.uuid(), result.authenticatedAt(),
                            timeoutSeconds));
        }
        if (users.requireCurrent() != result.userId()) {
            throw RedisOAuthStore.invalid();
        }
        return new OAuthBindResultVO("BIND", result.binding());
    }

    /**
     * 这里只查浏览器标识的格式及其与服务端流程摘要是否对应，不消费流程。
     *
     * @param expected 流程或票据保存的浏览器摘要
     * @param browser 当前请求的浏览器随机标识
     * @throws BusinessException 标识缺失、格式不合法或浏览器不匹配
     */
    private void requireBrowser(String expected, String browser) {
        if (browser == null || !browser.matches("[A-Za-z0-9_-]{43}")
                || !expected.equals(TokenUtils.digest(browser))) {
            throw RedisOAuthStore.invalid();
        }
    }

}
