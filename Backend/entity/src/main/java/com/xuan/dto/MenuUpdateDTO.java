package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * 修改菜单要填全，可空字段也不能漏；menuCode 不能在这里改。
 */
@Getter
public class MenuUpdateDTO {

    private Long parentId;

    @NotNull
    @TextLength(min = 1, max = 64)
    private String menuName;

    @NotNull
    @Pattern(regexp = "D|M|B")
    private String menuType;

    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}")
    private String routeName;

    @TextLength(max = 255)
    private String path;

    @TextLength(max = 255)
    private String component;

    @TextLength(max = 64)
    private String icon;
    private Long permissionId;

    @NotNull
    @Setter
    private Integer sortOrder;

    @NotNull
    @Min(0)
    @Max(1)
    @Setter
    private Integer isVisible;

    @NotNull
    @Min(0)
    @Max(1)
    @Setter
    private Integer keepAlive;

    @NotNull
    @Min(0)
    @Max(1)
    @Setter
    private Integer status;

    @Getter(AccessLevel.NONE)
    private final Set<String> present = new HashSet<>();

    public void setParentId(Long value) {
        present.add("parentId");
        parentId = value;
    }

    public void setMenuName(String value) {
        menuName = value == null ? null : value.strip();
    }

    public void setMenuType(String value) {
        menuType = value == null ? null : value.strip();
    }

    public void setRouteName(String value) {
        present.add("routeName");
        routeName = clean(value);
    }

    public void setPath(String value) {
        present.add("path");
        path = clean(value);
    }

    public void setComponent(String value) {
        present.add("component");
        component = clean(value);
    }

    public void setIcon(String value) {
        present.add("icon");
        icon = clean(value);
    }

    public void setPermissionId(Long value) {
        present.add("permissionId");
        permissionId = value;
    }

    @AssertTrue(message = "parentId/routeName/path/component/icon/permissionId必须显式提供，可为null")
    public boolean isCompleteReplace() {
        return present.size() == 6;
    }

    private String clean(String value) {
        return value == null || value.strip().isEmpty() ? null : value.strip();
    }
}
