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
 * 角色数组表示全部角色，空数组就是清空，不能有重复或空 ID。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRolesDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 角色ID列表
    @NotNull
    @Size(max = 100)
    private List<@NotNull Long> roleIds;

    /**
     * 检查提交的角色 ID 是否重复。
     */
    @AssertTrue(message = "同一角色不能重复选择。")
    public boolean isUnique() {
        return roleIds == null || new HashSet<>(roleIds).size() == roleIds.size();
    }
}
