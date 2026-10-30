package com.xuan.service.impl;

import com.xuan.utils.TokenUtils;
import com.xuan.dto.OAuthAuthorizeDTO.Operation;
import com.xuan.exception.BusinessException;
import com.xuan.properties.AuthProperties;
import com.xuan.service.IOAuthService;
import com.xuan.service.IUserService;
import com.xuan.model.OAuthFlow;
import com.xuan.model.OAuthTicket;
import com.xuan.client.OAuthProviderClient;
import com.xuan.store.RedisOAuthStore;
import com.xuan.session.SessionManager;
import com.xuan.vo.OAuthAuthorizeVO;
import com.xuan.vo.OAuthBindingVO;
import com.xuan.vo.OAuthBindResultVO;
import com.xuan.vo.OAuthLoginResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/** 平台边界与本站会话分离；先原子消费，再换码或写库，失败不恢复票据。 */
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
     * 创建与浏览器、平台和操作绑定的 300 秒一次性授权流程。
     * <p>BIND 额外保存当前用户、原 Token 摘要和最近认证时间；不接受客户端指定目标身份。</p>
     *
     * @param source 服务端启用的平台标识
     * @param operation 登录或绑定操作
     * @param browser HTTP 入口提供的浏览器随机标识，仅用于关联流程
     * @return 平台授权地址及流程有效秒数
     */
    @Override
    public OAuthAuthorizeVO authorize(String source, Operation operation, String browser) {
        // 1. 只允许服务端配置完整的平台参与授权。
        providers.requireEnabled(source);
        Long userId = null;
        String tokenHash = null;
        long authenticatedAt = 0;
        // 2. BIND 绑定当前用户、原 Token 摘要与最近认证时间；LOGIN 不借用本站身份。
        if (operation == Operation.BIND) {
            userId = users.requireCurrent();
            authenticatedAt = sessions.requireRecent(sessions.currentToken(), userId, false);
            tokenHash = TokenUtils.digest(sessions.currentToken());
        }
        // 3. 生成不可预测的 state，并关联当前浏览器和操作，支持同浏览器多流程。
        String state = TokenUtils.random();
        OAuthFlow flow = new OAuthFlow(TokenUtils.digest(browser), source, operation, userId, tokenHash,
                authenticatedAt, clock.millis());
        // 4. 保留 JustAuth 自身的 state 校验缓存，另存 300 秒的本站一次性流程。
        String url = providers.authorize(source, state);
        store.save("state", state, json.writeValueAsString(flow), Duration.ofSeconds(300));
        return new OAuthAuthorizeVO(url, 300);
    }

    /**
     * 验证并原子消费授权流程，取得平台身份后执行本站注册、身份解析或绑定。
     * <p>可信流程成功时签发 60 秒 ticket；消费后的业务或事务失败回跳固定错误页，
     * 不可信浏览器或 state 关系直接报错。处理结束清理 JustAuth state，不恢复已消费流程。</p>
     *
     * @param source 回调路径中的平台标识
     * @param state 平台返回的流程随机值
     * @param code 平台授权码，取消或失败时可缺失
     * @param error 平台取消或失败标记，仅判断是否存在，不透传其内容
     * @param browser 当前浏览器 Cookie 中的随机标识
     * @return 固定前端完成页地址，fragment 仅包含 ticket 或白名单业务错误码
     */
    @Override
    public String callback(String source, String state, String code, String error, String browser) {
        // 1. 先校验平台、浏览器和流程关联，不匹配时不消费合法 state。
        providers.requireEnabled(source);
        String raw = store.read("state", state);
        OAuthFlow flow = json.readValue(raw, OAuthFlow.class);
        requireBrowser(flow.browserHash(), browser);
        if (!source.equals(flow.source())) {
            throw RedisOAuthStore.invalid();
        }
        // 2. BIND 从服务端保存的摘要重查原会话，回调无需携带本站 Bearer。
        if (flow.operation() == Operation.BIND) {
            sessions.originalToken(flow.userId(), flow.tokenHash());
        }
        // 3. 换码前原子消费本站流程，并发或重放不能再次调用平台。
        store.consume("state", state, raw);
        try {
            // 4.1 平台取消也消耗本次流程，失败后必须重新发起。
            if (error != null) {
                throw new BusinessException(400, "OAUTH_DENIED", "平台授权已取消");
            }
            // 4.2 由平台验证授权码并取得身份，异常只转换为脱敏业务错误。
            OAuthProviderClient.Identity identity;
            try {
                identity = providers.verify(source, state, code);
            } catch (DataAccessException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new BusinessException(502, "OAUTH_UPSTREAM_ERROR", "第三方授权失败，请重新发起");
            }
            // 4.3 只检查身份合法性，合法 uuid 保持原值，不 trim 或改变大小写。
            if (identity == null || identity.uuid() == null || identity.uuid().isBlank()
                    || identity.uuid().codePointCount(0, identity.uuid().length()) > 255) {
                throw new BusinessException(502, "OAUTH_UPSTREAM_ERROR", "第三方身份无效");
            }
            // 5. 在取得平台身份时记录认证时间，再执行本站登录身份解析或绑定事务。
            long at = clock.millis();
            long userId;
            OAuthBindingVO binding = null;
            if (flow.operation() == Operation.LOGIN) {
                userId = users.resolveOAuth(source, identity.uuid(), identity.nickname(), identity.avatar());
            } else {
                userId = flow.userId();
                binding = users.bindOAuth(userId, flow.tokenHash(), source, identity.uuid());
            }
            // 6. 只签发 60 秒结果票据并回跳固定完成页，不在 URL 中返回本站 Token。
            String ticket = TokenUtils.random();
            OAuthTicket result = new OAuthTicket(flow.browserHash(), flow.source(), flow.operation(), userId,
                    flow.tokenHash(), flow.operation() == Operation.LOGIN ? at : flow.authenticatedAt(), binding, identity.uuid());
            store.save("ticket", ticket, json.writeValueAsString(result), Duration.ofSeconds(60));
            return properties.getFrontendCompleteUrl() + "#ticket=" + ticket;
        } catch (BusinessException exception) {
            return properties.getFrontendCompleteUrl() + "#error=" + exception.getCode();
        } catch (DataAccessException | TransactionException exception) {
            // 已消费的可信流程包含事务提交失败，统一回跳固定完成页且不签发票据。
            return properties.getFrontendCompleteUrl() + "#error=SERVICE_UNAVAILABLE";
        } finally {
            // 7. 无论成功或失败均清理 JustAuth 校验值，不恢复已消费的本站流程。
            store.deleteState(state);
        }
    }

    /**
     * 校验浏览器并原子消费结果票据，按 LOGIN 或 BIND 返回对应结果。
     * <p>BIND 必须携带发起时同一个有效 Token；LOGIN 在用户锁内重查绑定并签发会话。
     * 票据消费后不因后续失败或响应丢失而恢复。</p>
     *
     * @param ticket 固定完成页交回的一次性结果票据
     * @param browser 当前浏览器 Cookie 中的随机标识
     * @return LOGIN 的本站会话结果，或 BIND 的绑定结果
     */
    @Override
    public Object exchange(String ticket, String browser) {
        // 1. 读取尚未过期的票据并检查浏览器，错误浏览器不能消费他人的结果。
        String raw = store.read("ticket", ticket);
        OAuthTicket result = json.readValue(raw, OAuthTicket.class);
        requireBrowser(result.browserHash(), browser);
        // 2. BIND 必须携带发起时同一个 Token，相同用户的其他 Token 也不能替代。
        if (result.operation() == Operation.BIND) {
            String token = sessions.currentToken();
            if (token == null || !Objects.equals(result.tokenHash(), TokenUtils.digest(token))) {
                throw RedisOAuthStore.invalid();
            }
            sessions.requireActive(token, result.userId());
        }
        // 3. 原子消费结果后才处理兑换，响应丢失也不能重用票据。
        store.consume("ticket", ticket, raw);
        // 4. LOGIN 在用户锁内重查绑定并签发会话；BIND 只确认身份并返回绑定结果。
        if (result.operation() == Operation.LOGIN) {
            return new OAuthLoginResultVO("LOGIN", users.loginOAuth(result.userId(), result.source(), result.uuid(), result.authenticatedAt()));
        }
        if (users.requireCurrent() != result.userId()) {
            throw RedisOAuthStore.invalid();
        }
        return new OAuthBindResultVO("BIND", result.binding());
    }

    /**
     * 验证浏览器标识格式及其与服务端流程摘要的关联，不消费流程。
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
