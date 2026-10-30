package com.xuan.mapper;

import com.xuan.entity.SysMenu;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户、角色、权限都要有效，不能靠角色名跳过权限检查。
 */
public interface AuthorizationMapper {

    /**
     * 查询启用用户的启用角色码，按字典序返回；不能用显示名称鉴权，也不给 admin 隐含通配权限。
     *
     * @param userId 当前会话的本站用户 ID
     * @return 有效角色码列表，无有效角色时为空列表
     */
    List<String> roleCodes(@Param("userId") long userId);

    /**
     * 取用户已分配的全部角色 ID 并升序排列，禁用角色也参与用户整体版本，但不能拿它判断有效权限。
     *
     * @param userId 当前会话的本站用户 ID
     * @return 完整角色关联 ID 列表
     */
    List<Long> roleIds(@Param("userId") long userId);

    /**
     * 查询有效用户、角色和权限，保留每个角色的范围，业务层再逐权限合并 ALL 和 SELF。
     *
     * @param userId 当前会话的本站用户 ID
     * @return 权限与授予角色范围的明细列表，不在 SQL 中合并为用户全局范围
     */
    List<Grant> grants(@Param("userId") long userId);

    /**
     * 先按排序值和 ID 取有效角色授予的启用菜单，业务层在同一读取事务里再查权限、祖先链并去掉空目录。
     *
     * @param userId 当前会话的本站用户 ID
     * @return 去重后的菜单候选列表，包含已授权的隐藏项和按钮
     */
    List<MenuGrant> menus(@Param("userId") long userId);

    /**
     * 一行就是一个有效角色授予的一项权限和范围。
     */
    @Data
    class Grant {

        private String permissionCode;
        /** 只把本角色的 ALL 或 SELF 合并到当前 permissionCode，不能影响别的权限。 */
        private String dataScope;
    }

    /**
     * 菜单查询结果里的 permissionCode 仍从权限字典读取。
     */
    @Getter
    @Setter
    class MenuGrant extends SysMenu {

        private String permissionCode;
    }
}
