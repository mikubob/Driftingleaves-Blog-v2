package com.xuan.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.List;

/**
 * 不要求编辑版本的清理动作，只接收显式选择的ID。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchIdsDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 选中ID列表
    @NotNull
    @Size(min = 1, max = 100)
    private List<String> ids;

    /**
     * 逐项检查范围和唯一性，不接受数字、空值或前导零。
     */
    @AssertTrue(message = "所选记录无效或重复，请刷新列表后重新选择。")
    public boolean isValidIds() {
        if (ids == null) {
            return true;
        }
        var seen = new HashSet<String>();
        for (String id : ids) {
            if (!BatchItemDTO.builder().id(id).build().isIdValid() || !seen.add(id)) {
                return false;
            }
        }
        return true;
    }
}
