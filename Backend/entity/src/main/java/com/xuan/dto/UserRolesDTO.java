package com.xuan.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;

/** 完整角色集合；空数组清空，不接受重复或空 ID。 */
public record UserRolesDTO(@NotNull @Size(max = 100) List<@NotNull Long> roleIds) {
    @AssertTrue(message = "roleIds不能重复")
    public boolean isUnique() {
        return roleIds == null || new HashSet<>(roleIds).size() == roleIds.size();
    }
}
