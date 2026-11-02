package com.xuan.cache;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 转换公开缓存数据，按查询方法的返回类型读取 JSON，不接受缓存指定其他 Java 类型。 */
@Component
public class PublicCacheSerializer implements RedisSerializer<Object> {
    // Web 的字符串 ID 规则不参与内部 VO 往返，时间仍使用 Jackson 3 的原生支持。
    private final JsonMapper json = JsonMapper.builder().build();

    /** 将查询结果转成缓存 JSON，空值不写入。 */
    @Override
    public byte[] serialize(Object value) {
        return value == null ? new byte[0] : json.writeValueAsBytes(value);
    }

    /** 读取缓存 JSON，只使用当前公开方法声明的返回类型。 */
    @Override
    public Object deserialize(byte[] bytes) {
        var frame = PublicCacheContext.current();
        if (frame == null) {
            throw new SerializationException("缓存读取缺少公开查询类型");
        }
        return bytes == null || bytes.length == 0 ? null : json.readValue(bytes, json.constructType(frame.type()));
    }
}
