package com.xuan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.List;

/**
 * 单条和多条共用的资源选择，不能自动去重或截断。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BatchRevisionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 选中的批量项列表
    @NotNull
    @Size(min = 1, max = 100)
    private List<@NotNull @Valid BatchItemDTO> items;

    /**
     * 重复目标会使版本和结果对应不清，因此在入口拒绝。
     */
    @AssertTrue(message = "同一条记录不能重复选择，请取消重复项后重试。")
    public boolean isUnique() {
        if (items == null) {
            return true;
        }
        var seen = new HashSet<String>();
        for (BatchItemDTO item : items) {
            if (item != null && !seen.add(item.getId())) {
                return false;
            }
        }
        return true;
    }
}
