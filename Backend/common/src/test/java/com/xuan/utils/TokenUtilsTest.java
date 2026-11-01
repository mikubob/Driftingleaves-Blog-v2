package com.xuan.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/** 保证替换摘要实现后，已有 Redis 键和业务版本不变。 */
class TokenUtilsTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "中文🙂", "token\u0000with-control"})
    void digestMatchesExistingUtf8Sha256(String value) throws Exception {
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));

        assertThat(TokenUtils.digest(value)).isEqualTo(expected).matches("[0-9a-f]{64}");
    }

    @Test
    void digestStillRejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> TokenUtils.digest(null));
    }
}
