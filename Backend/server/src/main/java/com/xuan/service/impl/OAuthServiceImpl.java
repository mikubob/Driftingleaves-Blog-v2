package com.xuan.service.impl;

import com.xuan.dto.OAuthAuthorizeDTO.Operation;
import com.xuan.exception.BusinessException;
import com.xuan.properties.AuthProperties;
import com.xuan.service.IOAuthService;
import com.xuan.service.IUserService;
import com.xuan.service.OAuthProviderGateway;
import com.xuan.service.RedisOAuthStore;
import com.xuan.service.SessionService;
import com.xuan.vo.OAuthAuthorizeVO;
import com.xuan.vo.OAuthBindingVO;
import com.xuan.vo.OAuthBindResultVO;
import com.xuan.vo.OAuthLoginResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/** 平台边界与本站会话分离；先原子消费，再换码或写库，失败不恢复票据。 */
@Service
@RequiredArgsConstructor
public class OAuthServiceImpl implements IOAuthService {
    private final OAuthProviderGateway providers;
    private final RedisOAuthStore store;
    private final IUserService users;
    private final SessionService sessions;
    private final AuthProperties properties;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Clock clock;

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
            tokenHash = SessionService.digest(sessions.currentToken());
        }
        // 3. 生成不可预测的 state，并关联当前浏览器和操作，支持同浏览器多流程。
        String state = SessionService.random();
        Flow flow = new Flow(SessionService.digest(browser), source, operation, userId, tokenHash,
                authenticatedAt, clock.millis());
        // 4. 保留 JustAuth 自身的 state 校验缓存，另存 300 秒的本站一次性流程。
        String url = providers.authorize(source, state);
        store.save("state", state, json.writeValueAsString(flow), Duration.ofSeconds(300));
        return new OAuthAuthorizeVO(url, 300);
    }

    @Override
    public String callback(String source, String state, String code, String error, String browser) {
        // 1. 先校验平台、浏览器和流程关联，不匹配时不消费合法 state。
        providers.requireEnabled(source);
        String raw = store.read("state", state);
        Flow flow = json.readValue(raw, Flow.class);
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
            OAuthProviderGateway.Identity identity;
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
            String ticket = SessionService.random();
            Ticket result = new Ticket(flow.browserHash(), flow.source(), flow.operation(), userId,
                    flow.tokenHash(), flow.operation() == Operation.LOGIN ? at : flow.authenticatedAt(), binding, identity.uuid());
            store.save("ticket", ticket, json.writeValueAsString(result), Duration.ofSeconds(60));
            return properties.getFrontendCompleteUrl() + "#ticket=" + ticket;
        } catch (BusinessException exception) {
            return properties.getFrontendCompleteUrl() + "#error=" + exception.getCode();
        } catch (DataAccessException exception) {
            return properties.getFrontendCompleteUrl() + "#error=SERVICE_UNAVAILABLE";
        } finally {
            // 7. 无论成功或失败均清理 JustAuth 校验值，不恢复已消费的本站流程。
            store.deleteState(state);
        }
    }

    @Override
    public Object exchange(String ticket, String browser) {
        // 1. 读取尚未过期的票据并检查浏览器，错误浏览器不能消费他人的结果。
        String raw = store.read("ticket", ticket);
        Ticket result = json.readValue(raw, Ticket.class);
        requireBrowser(result.browserHash(), browser);
        // 2. BIND 必须携带发起时同一个 Token，相同用户的其他 Token 也不能替代。
        if (result.operation() == Operation.BIND) {
            String token = sessions.currentToken();
            if (token == null || !Objects.equals(result.tokenHash(), SessionService.digest(token))) {
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

    private void requireBrowser(String expected, String browser) {
        if (browser == null || !browser.matches("[A-Za-z0-9_-]{43}")
                || !expected.equals(SessionService.digest(browser))) {
            throw RedisOAuthStore.invalid();
        }
    }

    /** 服务端流程不保存原始 Token，只保存关联摘要；创建时间由 Redis TTL 约束。 */
    public record Flow(String browserHash, String source, Operation operation, Long userId,
                       String tokenHash, long authenticatedAt, long createdAt) { }

    /** 结果只含已验证身份与绑定投影，不含平台 Token/code。 */
    public record Ticket(String browserHash, String source, Operation operation, Long userId,
                         String tokenHash, long authenticatedAt, OAuthBindingVO binding, String uuid) { }
}
