package com.xuan.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;

/** 不要求编辑版本的清理动作，只接收显式选择的ID。 */
public record BatchIdsDTO(@NotNull @Size(min = 1, max = 100) List<String> ids) {
    /** 逐项检查范围和唯一性，不接受数字、空值或前导零。 */
    @AssertTrue(message = "所选记录无效或重复，请刷新列表后重新选择。")
    public boolean isValidIds() {
        if (ids == null) {
            return true;
        }
        var seen = new HashSet<String>();
        for (String id : ids) {
            if (!new BatchItemDTO(id, null).isIdValid() || !seen.add(id)) {
                return false;
            }
        }
        return true;
    }
}
