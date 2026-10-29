package com.xuan.mapper;

import com.xuan.entity.SysMenu;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/** 有效用户、角色和权限连接；不使用角色名绕过权限。 */
public interface AuthorizationMapper {
    /**
     * 查询启用用户关联的启用角色码，按字典序返回。
     * <p>不以角色显示名称鉴权，也不为 admin 角色增加隐式通配能力。</p>
     *
     * @param userId 当前会话的本站用户 ID
     * @return 有效角色码列表，无有效角色时为空列表
     */
    List<String> roleCodes(@Param("userId") long userId);
    /**
     * 查询用户分配的全部角色 ID，按 ID 升序返回。
     * <p>包含已分配但禁用的角色，供用户聚合版本计算；不能用于判断当前有效权限。</p>
     *
     * @param userId 当前会话的本站用户 ID
     * @return 完整角色关联 ID 列表
     */
    List<Long> roleIds(@Param("userId") long userId);
    /**
     * 查询有效用户通过有效角色取得的启用权限及角色数据范围。
     * <p>保留不同角色对同一权限的多行授权，供 Service 按该权限分别合并 ALL/SELF。</p>
     *
     * @param userId 当前会话的本站用户 ID
     * @return 权限与授予角色范围的明细列表，不在 SQL 中合并为用户全局范围
     */
    List<Grant> grants(@Param("userId") long userId);
    /**
     * 查询有效角色授予的启用菜单候选项，按排序值和 ID 返回。
     * <p>此处仅提供候选数据；权限交集、完整祖先链及空目录过滤由 Service 在同一读取事务中完成。</p>
     *
     * @param userId 当前会话的本站用户 ID
     * @return 去重后的菜单候选列表，包含已授权的隐藏项和按钮
     */
    List<MenuGrant> menus(@Param("userId") long userId);

    /** 一行表示某有效角色明确授予的权限及其范围。 */
    @Data
    class Grant {
        private String permissionCode;
        /** 本行授予角色的 ALL/SELF 范围，仅参与当前 permissionCode 的合并。 */
        private String dataScope;
    }

    /** 菜单投影；permissionCode 仍由权限字典取得。 */
    @Getter
    @Setter
    class MenuGrant extends SysMenu {
        private String permissionCode;
    }
}
