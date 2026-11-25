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

/**
 * 创建菜单只收这些字段，父节点要明确传入；按钮默认隐藏，其余按菜单约定取默认值。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 父级ID
    private Long parentId;

    // 菜单编码
    @NotNull
    @Pattern(regexp = "[A-Za-z0-9:_-]{1,64}", message = "只能包含字母、数字、冒号、下划线或连字符，最多64位")
    private String menuCode;

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
    @Builder.Default
    private Integer sortOrder = 0;

    // 是否显示 0-隐藏 1-显示
    @Min(0)
    @Max(1)
    private Integer isVisible;

    // 是否缓存页面 0-否 1-是
    @NotNull
    @Min(0)
    @Max(1)
    @Builder.Default
    private Integer keepAlive = 0;

    // 状态
    @NotNull
    @Min(0)
    @Max(1)
    @Builder.Default
    private Integer status = 1;

    // 是否传入父级ID
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean parentPresent;

    // 是否传入显示状态
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
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

    /**
     * 获取菜单可见状态。未传时，按钮默认隐藏，其他菜单默认显示。
     */
    public Integer getIsVisible() {
        int defaultVisibility = "B".equals(menuType) ? 0 : 1;
        return visiblePresent ? isVisible : defaultVisibility;
    }

    /**
     * 检查是否提交了上级菜单；可见状态可以省略，但传入时必须有值。
     */
    @AssertTrue(message = "上级菜单和显示设置未完整提交，请重新打开菜单编辑窗口后重试。")
    public boolean isCompleteCreate() {
        return parentPresent && (!visiblePresent || isVisible != null);
    }

    /**
     * 去掉路由、组件和图标字段的首尾空白，空白内容转为 null。
     */
    private String clean(String value) {
        return value == null || value.strip().isEmpty() ? null : value.strip();
    }
}
