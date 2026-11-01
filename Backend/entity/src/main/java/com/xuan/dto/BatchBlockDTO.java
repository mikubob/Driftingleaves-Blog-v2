package com.xuan.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;

/** 封禁结束时间必须明确填写，null才表示永久。 */
@Getter
public class BatchBlockDTO extends BatchRevisionDTO {
    private String blockedUntil;
    @Getter(lombok.AccessLevel.NONE)
    private boolean supplied;

    /** 区分明确的永久限制与漏传字段。 */
    public void setBlockedUntil(String value) {
        supplied = true;
        blockedUntil = value;
    }

    /** 防止漏填结束时间被误当作永久封禁。 */
    @AssertTrue(message = "blockedUntil必须显式出现")
    public boolean isComplete() {
        return supplied;
    }
}
