package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * 创建和修改共用资料字段，可空字段用null清空；状态不随资料修改。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SkillWriteDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 名称
    @NotNull
    @TextLength(min = 1, max = 64)
    private String name;

    // 描述
    @TextLength(min = 1, max = 500)
    private String description;

    // 图标
    @TextLength(min = 1, max = 2048)
    private String icon;

    // 排序权重
    @NotNull
    private Integer sortOrder;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Set<String> present = new HashSet<>();

    public void setName(String value) {
        present.add("name");
        name = value == null ? null : value.strip();
    }

    public void setDescription(String value) {
        present.add("description");
        description = value == null ? null : value.strip();
    }

    public void setIcon(String value) {
        present.add("icon");
        icon = value == null ? null : value.strip();
    }

    public void setSortOrder(Integer value) {
        present.add("sortOrder");
        sortOrder = value;
    }

    /**
     * 创建和替换都必须提供可空字段，防止漏传被当作清空。
     */
    @AssertTrue(message = "表单内容未完整提交，请重新打开编辑窗口后重试。")
    public boolean isComplete() {
        return present.size() == 4;
    }
}
