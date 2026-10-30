package com.xuan;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xuan.config.WebJsonConfig;
import com.xuan.dto.OAuthAuthorizeDTO;
import com.xuan.dto.PasswordSetDTO;
import com.xuan.dto.ProfilePatchDTO;
import com.xuan.dto.RegisterDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 实际解析和检查 Web 输入，不复制生产解析算法来验证自己。 */
class JsonContractTest {
    private final JsonMapper json;

    JsonContractTest() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new WebJsonConfig().webJsonCustomizer().customize(builder);
        json = builder.build();
    }

    @Test
    void rejectsUnknownDuplicateWrongTypesAndNumericIds() {
        for (String body : List.of("{\"username\":\"leaf\",\"password\":\"12345678\",\"nickname\":\"叶\",\"roleIds\":[]}",
                "{\"username\":\"leaf\",\"username\":\"other\"}", "{\"username\":12}")) {
            assertThatThrownBy(() -> json.readValue(body, RegisterDTO.class)).isInstanceOf(RuntimeException.class);
        }
        for (String body : List.of("{\"id\":1}", "{\"id\":\"01\"}", "{\"id\":\"9223372036854775808\"}",
                "{\"id\":\"0\"}", "{\"id\":-1}", "{\"id\":true}")) {
            assertThatThrownBy(() -> json.readValue(body, IdInput.class)).isInstanceOf(RuntimeException.class);
        }
        assertThat(json.readValue("{\"id\":\"9223372036854775807\"}", IdInput.class).id()).isEqualTo(Long.MAX_VALUE);
        assertThatThrownBy(() -> json.readValue("{\"operation\":0}", OAuthAuthorizeDTO.class)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> json.readValue("{\"operation\":\"UNKNOWN\"}", OAuthAuthorizeDTO.class)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void preservesNullsNumericPagesBigIntegersAndMillisecondUtc() {
        assertThat(json.writeValueAsString(Result.ok(null))).isEqualTo("{\"code\":\"OK\",\"msg\":\"成功\",\"data\":null,\"errors\":[]}");
        String value = json.writeValueAsString(new Output(Long.MAX_VALUE, new BigInteger("18446744073709551615"),
                LocalDateTime.of(2026, 10, 30, 1, 2, 3)));
        assertThat(value).contains("\"id\":\"9223372036854775807\"", "\"count\":\"18446744073709551615\"", "2026-10-30T01:02:03.000Z");
        PageResult<String> page = PageResult.from(new Page<String>(9, 10, 0).setRecords(List.of()));
        assertThat(json.writeValueAsString(page)).contains("\"page\":9", "\"pageSize\":10", "\"total\":\"0\"", "\"records\":[]");
    }

    @Test
    void patchPresenceAndUnicodePasswordBoundaries() {
        ProfilePatchDTO patch = json.readValue("{\"email\":null}", ProfilePatchDTO.class);
        assertThat(patch.hasEmail()).isTrue();
        assertThat(patch.hasNickname()).isFalse();
        assertThat(patch.getEmail()).isNull();
        assertThatThrownBy(() -> json.readValue("{\"emailPresent\":true}", ProfilePatchDTO.class)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> json.readValue("{\"currentPasswordPresent\":true}", PasswordSetDTO.class)).isInstanceOf(RuntimeException.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new RegisterDTO("leaf", "密".repeat(24), "😀".repeat(64)))).isEmpty();
            assertThat(validator.validate(new RegisterDTO("leaf", "密".repeat(25), "叶"))).isNotEmpty();
            assertThat(validator.validate(new RegisterDTO("leaf", "1234567", "叶"))).isNotEmpty();
            assertThat(validator.validate(json.readValue("{}", ProfilePatchDTO.class))).isNotEmpty();
            assertThat(validator.validate(json.readValue("{\"nickname\":null}", ProfilePatchDTO.class))).isNotEmpty();
        }
    }

    record IdInput(Long id) { }
    record Output(Long id, BigInteger count, LocalDateTime time) { }
}
