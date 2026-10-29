package com.xuan.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;

/** 权限和菜单共同完整替换，任一集合无效则整笔拒绝。 */
public record RoleGrantDTO(@NotNull @Size(max = 1000) List<@NotNull Long> permissionIds,
                           @NotNull @Size(max = 1000) List<@NotNull Long> menuIds) {
    @AssertTrue(message = "permissionIds和menuIds不能重复")
    public boolean isUnique() {
        return (permissionIds == null || new HashSet<>(permissionIds).size() == permissionIds.size())
                && (menuIds == null || new HashSet<>(menuIds).size() == menuIds.size());
    }
}
