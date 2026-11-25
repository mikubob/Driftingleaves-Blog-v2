package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * 修改菜单要填全，可空字段也不能漏；menuCode 不能在这里改。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 父级ID
    private Long parentId;

    // 菜单名称
    @NotNull
    @TextLength(min = 1, max = 64)
    private String menuName;

    // 菜单类型
    @NotNull
    @Pattern(regexp = "D|M|B", message = "请选择目录、页面或按钮")
    private String menuType;

    // 路由名称
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "须以字母开头，只能包含字母、数字或下划线，最多64位")
    private String routeName;

    // 路由路径
    @TextLength(max = 255)
    private String path;

    // 前端组件路径
    @TextLength(max = 255)
    private String component;

    // 图标
    @TextLength(max = 64)
    private String icon;

    // 权限ID
    private Long permissionId;

    // 排序权重
    @NotNull
    private Integer sortOrder;

    // 是否显示 0-隐藏 1-显示
    @NotNull
    @Min(0)
    @Max(1)
    private Integer isVisible;

    // 是否缓存页面 0-否 1-是
    @NotNull
    @Min(0)
    @Max(1)
    private Integer keepAlive;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
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

    /**
     * 检查更新菜单时是否显式提交了全部可空字段，避免漏传后清空原值。
     */
    @AssertTrue(message = "菜单设置未完整提交，请重新打开菜单编辑窗口后重试。")
    public boolean isCompleteReplace() {
        return present.size() == 6;
    }

    /**
     * 去掉路由、组件和图标字段的首尾空白，空白内容按清空处理。
     */
    private String clean(String value) {
        return value == null || value.strip().isEmpty() ? null : value.strip();
    }
}
