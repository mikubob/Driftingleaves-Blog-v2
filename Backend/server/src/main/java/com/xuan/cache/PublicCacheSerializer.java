package com.xuan.cache;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 只按声明的公开查询返回类型读 JSON，不接受 Redis 内容指定任意 Java 类型。 */
@Component
public class PublicCacheSerializer implements RedisSerializer<Object> {
    // Web 的字符串 ID 规则不参与内部 VO 往返，时间仍使用 Jackson 3 的原生支持。
    private final JsonMapper json = JsonMapper.builder().build();
    @Override public byte[] serialize(Object value) {
        return value == null ? new byte[0] : json.writeValueAsBytes(value);
    }
    @Override public Object deserialize(byte[] bytes) {
        var frame = PublicCacheScope.current();
        if (frame == null) { throw new SerializationException("缓存读取缺少公开查询类型"); }
        return bytes == null || bytes.length == 0 ? null : json.readValue(bytes, json.constructType(frame.type));
    }
}
