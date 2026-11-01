package com.xuan.dto;

import jakarta.validation.constraints.AssertTrue;

/** 一个显式选中的资源；版本在权限和范围检查后校验。 */
public record BatchItemDTO(String id, String revision) {
    /** ID 始终使用规范的正十进制字符串，避免溢出和精度损失。 */
    @AssertTrue(message = "id须为合法的正十进制ID字符串")
    public boolean isIdValid() {
        try {
            long value = Long.parseLong(id);
            return value > 0 && Long.toString(value).equals(id);
        } catch (NumberFormatException error) {
            return false;
        }
    }
}
