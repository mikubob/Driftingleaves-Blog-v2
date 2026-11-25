package com.xuan.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 一个显式选中的资源；版本在权限和范围检查后校验。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchItemDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 资源ID
    private String id;

    // 资源版本号
    private String revision;

    /**
     * ID 始终使用规范的正十进制字符串，避免溢出和精度损失。
     */
    @AssertTrue(message = "所选记录无效，请刷新列表后重新选择。")
    public boolean isIdValid() {
        try {
            long value = Long.parseLong(id);
            return value > 0 && Long.toString(value).equals(id);
        } catch (NumberFormatException error) {
            return false;
        }
    }
}
