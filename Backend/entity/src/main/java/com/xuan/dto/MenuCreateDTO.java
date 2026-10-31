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

/**
 * 创建菜单只收这些字段，父节点要明确传入；按钮默认隐藏，其余按菜单约定取默认值。
 */
@Getter
public class MenuCreateDTO {

    private Long parentId;

    @NotNull
    @Pattern(regexp = "[A-Za-z0-9:_-]{1,64}")
    private String menuCode;

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

    @Setter
    private Long permissionId;

    @NotNull
    @Setter
    private Integer sortOrder = 0;

    @Min(0)
    @Max(1)
    private Integer isVisible;

    @NotNull
    @Min(0)
    @Max(1)
    @Setter
    private Integer keepAlive = 0;

    @NotNull
    @Min(0)
    @Max(1)
    @Setter
    private Integer status = 1;

    @Getter(AccessLevel.NONE)
    private boolean parentPresent;

    @Getter(AccessLevel.NONE)
    private boolean visiblePresent;

    public void setParentId(Long value) {
        parentPresent = true;
        parentId = value;
    }

    public void setMenuCode(String value) {
        menuCode = value == null ? null : value.strip();
    }

    public void setMenuName(String value) {
        menuName = value == null ? null : value.strip();
    }

    public void setMenuType(String value) {
        menuType = value == null ? null : value.strip();
    }

    public void setRouteName(String value) {
        routeName = clean(value);
    }

    public void setPath(String value) {
        path = clean(value);
    }

    public void setComponent(String value) {
        component = clean(value);
    }

    public void setIcon(String value) {
        icon = clean(value);
    }

    public void setIsVisible(Integer value) {
        visiblePresent = true;
        isVisible = value;
    }

    public Integer getIsVisible() {
        int defaultVisibility = "B".equals(menuType) ? 0 : 1;
        return visiblePresent ? isVisible : defaultVisibility;
    }

    @AssertTrue(message = "parentId必须显式提供，isVisible不可为null")
    public boolean isCompleteCreate() {
        return parentPresent && (!visiblePresent || isVisible != null);
    }

    private String clean(String value) {
        return value == null || value.strip().isEmpty() ? null : value.strip();
    }
}
