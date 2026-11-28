package com.xuan.mapper;

import com.xuan.entity.SysMenu;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 用户授权数据访问接口
 */
@Mapper
public interface AuthorizationMapper {

    /**
     * 查询启用用户的有效角色编码列表，按字典序排列。
     *
     * @param userId 用户 ID
     * @return 有效角色编码列表
     */
    List<String> roleCodes(@Param("userId") long userId);

    /**
     * 查询用户已分配的全部角色 ID 列表（含禁用角色，用于计算用户整体版本），按 ID 升序排列。
     *
     * @param userId 用户 ID
     * @return 角色 ID 列表
     */
    List<Long> roleIds(@Param("userId") long userId);

    /**
     * 查询用户有效角色授予的权限及对应数据范围明细
     *
     * @param userId 用户 ID
     * @return 权限与数据范围明细列表
     */
    List<Grant> grants(@Param("userId") long userId);

    /**
     * 查询用户有效角色授予的启用菜单列表，按排序值与菜单 ID 排列。
     *
     * @param userId 用户 ID
     * @return 菜单授权候选列表
     */
    List<MenuGrant> menus(@Param("userId") long userId);

    /**
     * 批量查询一组用户的讨论区作者与管理员徽标，仅统计启用用户与启用角色。
     *
     * @param userIds 用户 ID 集合
     * @return 用户徽标列表
     */
    List<DiscussionBadge> discussionBadgesByUsers(@Param("userIds") Collection<Long> userIds);

    /**
     * 讨论区用户作者与管理员徽标投影
     */
    @Data
    class DiscussionBadge {

        private Long userId;
        private boolean siteAuthor;
        private boolean siteAdmin;
    }

    /**
     * 角色权限与数据范围明细
     */
    @Data
    class Grant {

        /**
         * 权限编码
         */
        private String permissionCode;

        /**
         * 本条角色授权对应的数据范围（ALL 或 SELF）
         */
        private String dataScope;
    }

    /**
     * 带关联权限编码的菜单记录
     */
    @Getter
    @Setter
    class MenuGrant extends SysMenu {

        /**
         * 菜单关联的权限编码
         */
        private String permissionCode;
    }
}
