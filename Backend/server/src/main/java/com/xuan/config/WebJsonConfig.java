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

/** MVC 专用 Jackson 3 协议：严格类型、字符串 ID 和 UTC 毫秒时间。 */
@Configuration
public class WebJsonConfig {
    @Bean
    public JsonMapperBuilderCustomizer webJsonCustomizer() {
        return builder -> {
            // 1. Long ID 和 BigInteger 计数统一输出字符串，避免浏览器整数精度丢失。
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(BigInteger.class, ToStringSerializer.instance);
            // 2. 输入 ID 仅接受合法十进制字符串，不允许 JSON 数字等类型宽松转换。
            module.addDeserializer(Long.class, new ValueDeserializer<>() {
                @Override
                public Long deserialize(JsonParser parser, DeserializationContext context) {
                    // 2.1 先检查 JSON 类型和正整数文本格式，排除前导零等非法表示。
                    if (parser.currentToken() != JsonToken.VALUE_STRING
                            || !parser.getString().matches("[1-9][0-9]{0,18}")) {
                        return context.reportInputMismatch(Long.class, "ID须为正十进制字符串");
                    }
                    // 2.2 再检查 Long 的实际范围，溢出按输入错误返回。
                    try {
                        return Long.valueOf(parser.getString());
                    } catch (NumberFormatException exception) {
                        return context.reportInputMismatch(Long.class, "ID超出范围");
                    }
                }
            });
            // 3. 数据库 UTC 时间按固定毫秒格式输出，不受部署机器本地时区影响。
            module.addSerializer(LocalDateTime.class, new ValueSerializer<>() {
                private final DateTimeFormatter format = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'");

                @Override
                public void serialize(LocalDateTime value, JsonGenerator generator, SerializationContext context) {
                    generator.writeString(format.format(value.atOffset(ZoneOffset.UTC)));
                }
            });
            // 4. 统一拒绝未知字段、重复属性、数字枚举及标量隐式转换。
            builder.addModule(module)
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                            DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES,
                            DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                    .withCoercionConfig(LogicalType.Textual,
                            config -> config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
        };
    }
}
