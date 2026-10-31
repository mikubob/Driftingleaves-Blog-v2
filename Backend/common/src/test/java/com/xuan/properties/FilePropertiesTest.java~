package com.xuan.properties;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 实际检查附件 origin 配置，不访问网络或存储。
 */
class FilePropertiesTest {
    @Test
    void acceptsConcreteHttpOriginsAndRejectsPathsCredentialsAndInvalidValues() {
        FileProperties properties = new FileProperties();
        assertThat(properties.isOriginsValid()).isTrue();
        properties.setPublicOrigins(List.of("https://blog.example.test", "http://localhost:8080"));
        assertThat(properties.isOriginsValid()).isTrue();
        for (String value : Arrays.asList(null, "", "*", "https://*.example.test", "/relative", "file:///tmp",
                "https://user:pass@example.test", "https://example.test/", "https://example.test/path",
                "https://example.test?query=1", "https://example.test#fragment", "not a URI")) {
            properties.setPublicOrigins(Arrays.asList(value));
            assertThat(properties.isOriginsValid()).as("无效origin配置").isFalse();
        }
    }
}
