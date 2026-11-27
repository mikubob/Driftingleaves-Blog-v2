package com.xuan.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.io.Serial;

/**
 * 封禁结束时间必须明确填写，null才表示永久。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BatchBlockDTO extends BatchRevisionDTO {

    @Serial
    private static final long serialVersionUID = 1L;

    // 拉黑截止时间
    private String blockedUntil;

    // 是否显式传入字段
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean supplied;

    /**
     * 区分明确的永久限制与漏传字段。
     */
    public void setBlockedUntil(String value) {
        supplied = true;
        blockedUntil = value;
    }

    /**
     * 防止漏填结束时间被误当作永久封禁。
     */
    @AssertTrue(message = "请选择封禁结束时间，或明确勾选永久封禁。")
    public boolean isComplete() {
        return supplied;
    }
}
