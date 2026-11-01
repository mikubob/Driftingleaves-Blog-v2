package com.xuan;

import com.xuan.client.MailClient;
import com.xuan.exception.BusinessException;
import com.xuan.properties.AuthProperties;
import com.xuan.store.EmailCodeStore;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** 只替换 SMTP 传输，验证码生成、Redis 保存及消费仍执行真实实现。 */
final class EmailTestSupport {

    private static final Pattern CODE = Pattern.compile("验证码：([0-9]{6})");

    private EmailTestSupport() {
    }

    /** 历史业务夹具通过真实发送逻辑准备验证码，不复制 Redis 键或验证码算法。 */
    static String issue(StringRedisTemplate redis, AuthProperties auth, String email, String purpose) {
        AtomicReference<String> message = new AtomicReference<>();
        new EmailCodeStore(redis, auth, (to, subject, text) -> message.set(text)).send(email, purpose);
        return code(message.get());
    }

    /** 从测试收件箱读取邮件内容，不能从 HTTP 响应获取答案。 */
    static String code(String text) {
        var matcher = CODE.matcher(text);
        if (!matcher.find()) {
            throw new AssertionError("未收到六位邮件验证码");
        }
        return matcher.group(1);
    }

    static class Mailbox implements MailClient {

        final Map<String, String> messages = new ConcurrentHashMap<>();
        boolean fail;

        @Override
        public void send(String to, String subject, String text) {
            if (fail) {
                throw new BusinessException(503, "SERVICE_UNAVAILABLE", "邮件服务暂不可用");
            }
            messages.put(to, text);
        }
    }
}
