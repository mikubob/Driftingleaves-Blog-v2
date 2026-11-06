package com.xuan.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;

/**
 * 角色数组表示全部角色，空数组就是清空，不能有重复或空 ID。
 */
public record UserRolesDTO(@NotNull @Size(max = 100) List<@NotNull Long> roleIds) {

    /** 检查提交的角色 ID 是否重复。 */
    @AssertTrue(message = "同一角色不能重复选择。")
    public boolean isUnique() {
        return roleIds == null || new HashSet<>(roleIds).size() == roleIds.size();
    }
}
