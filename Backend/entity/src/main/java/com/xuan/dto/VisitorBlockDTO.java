package com.xuan.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.AccessLevel;

/** blockedUntil必须出现，null永久；实际UTC时间在业务入口检查。 */
@Getter
public class VisitorBlockDTO {
    private String blockedUntil;
    @Getter(AccessLevel.NONE)
    private boolean supplied;
    public void setBlockedUntil(String value) { supplied = true; blockedUntil = value; }
    /** 防止漏传字段被误当永久限制。 */
    @AssertTrue(message = "blockedUntil必须显式出现")
    public boolean isComplete() { return supplied; }
}
