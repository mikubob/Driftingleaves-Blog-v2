package com.xuan.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;

/**
 * 权限和菜单一起完整替换，任意一组不合法就全部拒绝。
 */
public record RoleGrantDTO(@NotNull @Size(max = 1000) List<@NotNull Long> permissionIds,
                           @NotNull @Size(max = 1000) List<@NotNull Long> menuIds) {

    /** 检查授权列表，权限 ID 和菜单 ID 在各自列表中不能重复。 */
    @AssertTrue(message = "同一权限或菜单不能重复选择，请取消重复项后重试。")
    public boolean isUnique() {
        return (permissionIds == null || new HashSet<>(permissionIds).size() == permissionIds.size())
                && (menuIds == null || new HashSet<>(menuIds).size() == menuIds.size());
    }
}
