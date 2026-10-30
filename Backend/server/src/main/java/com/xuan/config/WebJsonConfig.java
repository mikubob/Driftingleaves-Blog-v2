package com.xuan.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;
import tools.jackson.databind.type.LogicalType;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** MVC 用 Jackson 3 严格检查输入，ID 返回字符串，时间按 UTC 毫秒格式返回。 */
@Configuration
public class WebJsonConfig {
    @Bean
    public JsonMapperBuilderCustomizer webJsonCustomizer() {
        return builder -> {
            // 1. Long ID 和 BigInteger 计数都返回字符串，避免浏览器丢失整数精度。
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(BigInteger.class, ToStringSerializer.instance);
            // 2. 输入 ID 只能是合法十进制字符串，不能把 JSON 数字自动转过来。
            module.addDeserializer(Long.class, new ValueDeserializer<>() {
                @Override
                public Long deserialize(JsonParser parser, DeserializationContext context) {
                    // 2.先查 JSON 类型和正整数格式，带前导零的也不能收。
                    if (parser.currentToken() != JsonToken.VALUE_STRING
                            || !parser.getString().matches("[1-9][0-9]{0,18}")) {
                        return context.reportInputMismatch(Long.class, "ID须为正十进制字符串");
                    }
                    // 2.再查是否超过 Long 范围，溢出就按输入错误处理。
                    try {
                        return Long.valueOf(parser.getString());
                    } catch (NumberFormatException exception) {
                        return context.reportInputMismatch(Long.class, "ID超出范围");
                    }
                }
            });
            // 3. 数据库里的 UTC 时间按固定毫秒格式返回，不跟随机器时区变化。
            module.addSerializer(LocalDateTime.class, new ValueSerializer<>() {
                private final DateTimeFormatter format = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'");

                @Override
                public void serialize(LocalDateTime value, JsonGenerator generator, SerializationContext context) {
                    generator.writeString(format.format(value.atOffset(ZoneOffset.UTC)));
                }
            });
            // 4. 未知字段、重复属性、数字枚举和隐式类型转换都要拒绝。
            builder.addModule(module)
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                            DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES,
                            DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                    .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                    .withCoercionConfig(LogicalType.Textual,
                            config -> config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
        };
    }
}
