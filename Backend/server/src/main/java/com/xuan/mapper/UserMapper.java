package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.entity.SysUser;
import com.xuan.entity.SysRole;
import com.xuan.dto.AdminUserQueryDTO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 用户只写约定的字段，加锁和撤销会话由业务层统一处理。 */
public interface UserMapper extends BaseMapper<SysUser> {
    /**
     * 按字面关键词、状态和全部角色关联筛选，创建时间相同时再按 ID 倒序排。
     * @param page 分页插件使用的请求页码和页大小
     * @param query 已经检查过的关键词及状态
     * @param roleId 可空角色过滤，不因角色禁用而忽略关联
     * @return 内部用户分页，不直接序列化实体
     */
    IPage<SysUser> adminPage(IPage<SysUser> page, @Param("query") AdminUserQueryDTO query,
                           @Param("roleId") Long roleId);

    /**
     * 所有管理写事务先锁初始化角色这一行，串行检查最后管理员。
     * @return role_id=1；缺失时为 null，调用方须拒绝写入
     */
    Long lockAdminGuard();

    /**
     * 用户锁之后，再按 ID 升序锁角色，整体替换前检查是否存在和启用。
     * @param roleIds 非空且排序后的角色 ID
     * @return 当前存在的角色实体，包含禁用项
     */
    List<SysRole> lockRoles(@Param("roleIds") List<Long> roleIds);

    /**
     * 只替换后台资料和状态，可空字段传 NULL 时也要清空。
     * @param user 已锁定且通过版本与权限复核的目标用户
     * @return 更新影响行数
     */
    int updateAdmin(@Param("user") SysUser user);

    /**
     * 清掉用户全部角色关联，调用前要在事务里拿到保护锁和用户锁。
     * @param userId 已锁定的目标用户
     * @return 删除关联数
     */
    int deleteRoles(@Param("userId") long userId);

    /**
     * 用完整的两列主键插入角色关联，调用方已锁住用户和所有启用角色。
     * @param userId 目标用户 ID
     * @param roleIds 非空且不重复的已经检查过的角色 ID
     * @return 插入关联数
     */
    int insertRoles(@Param("userId") long userId, @Param("roleIds") List<Long> roleIds);

    /**
     * 检查这次修改后还剩多少有效管理员，调用方必须先拿到固定保护锁。
     * @param permissions 当前 SQL 的全部初始系统权限及后台入口码
     * @return 启用且关联启用 ALL admin 角色、具有全部指定启用权限的用户数
     */
    long effectiveAdminCount(@Param("permissions") List<String> permissions);
    /**
     * 按用户名锁住用户，再验证密码；必须在事务内调用，禁用用户也先查出，凭据通过后再由业务层查状态。
     * @param username 已完成入口格式校验及首尾空白处理的用户名
     * @return 内部用户实体，包含密码哈希；不存在时为 null
     */
    SysUser lockByUsername(@Param("username") String username);
    /**
     * 按本站用户 ID 锁住这一行，登录、密码、绑定和解绑都共用它，事务、身份和状态由调用方检查。
     * @param userId 服务端确认的本站用户 ID
     * @return 用户实体；不存在时为 null
     */
    SysUser lockById(@Param("userId") long userId);
    /**
     * 只更新昵称、邮箱、头像和简介，NULL 也要写；调用方负责用户行锁和版本检查。
     * @param user 已在原资料上应用 PATCH 出现性语义的用户数据
     * @return 更新影响行数
     */
    int updateProfile(@Param("user") SysUser user);
    /**
     * 更新本站密码哈希，传 null 就清除；最近认证、旧密码、最后登录方式和撤销会话由业务层处理。
     * @param userId 已锁定的用户 ID
     * @param hash 新 BCrypt 哈希；清除密码时为 null
     * @return 更新影响行数
     */
    int updatePassword(@Param("userId") long userId, @Param("hash") String hash);
    /**
     * 用数据库当前毫秒时间更新最近登录，连接用 UTC，调用要和用户锁、会话签发在同一事务。
     * @param userId 已确认启用且持有行锁的用户 ID
     * @return 更新影响行数
     */
    int updateLastLogin(@Param("userId") long userId);
    /**
     * 给新用户分配启用的 member 角色，写 user_id、role_id 关联；角色缺失或禁用就不插入，调用方必须回滚注册。
     * @param userId 当前注册事务创建的用户 ID
     * @return 成功插入为 1，未找到可用 member 为 0
     */
    int assignMember(@Param("userId") long userId);
}
