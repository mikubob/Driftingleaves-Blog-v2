package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.entity.SysUser;
import com.xuan.entity.SysRole;
import com.xuan.dto.AdminUserQueryDTO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 用户写入使用显式白名单，锁与会话撤销由 Service 协调。 */
public interface UserMapper extends BaseMapper<SysUser> {
    /**
     * 按字面关键词、状态及完整角色关联分页，排序固定为创建时间及 ID 倒序。
     * @param page 分页插件使用的请求页码和页大小
     * @param query 已校验的关键词及状态
     * @param roleId 可空角色过滤，不因角色禁用而忽略关联
     * @return 内部用户分页，不直接序列化实体
     */
    IPage<SysUser> adminPage(IPage<SysUser> page, @Param("query") AdminUserQueryDTO query,
                           @Param("roleId") Long roleId);

    /**
     * 取得初始化角色行作为所有管理写事务的第一把锁。
     * @return role_id=1；缺失时为 null，调用方须拒绝写入
     */
    Long lockAdminGuard();

    /**
     * 在用户锁之后按 ID 升序锁角色，供整体替换校验存在性及启用状态。
     * @param roleIds 非空且排序后的角色 ID
     * @return 当前存在的角色实体，包含禁用项
     */
    List<SysRole> lockRoles(@Param("roleIds") List<Long> roleIds);

    /**
     * 替换后台资料和状态白名单，可空值明确写入 NULL。
     * @param user 已锁定且通过版本与权限复核的目标用户
     * @return 更新影响行数
     */
    int updateAdmin(@Param("user") SysUser user);

    /**
     * 删除用户的完整角色集合，须在保护锁和用户锁事务中调用。
     * @param userId 已锁定的目标用户
     * @return 删除关联数
     */
    int deleteRoles(@Param("userId") long userId);

    /**
     * 插入完整复合键集合，调用方已锁定用户及全部启用角色。
     * @param userId 目标用户 ID
     * @param roleIds 非空且不重复的已校验角色 ID
     * @return 插入关联数
     */
    int insertRoles(@Param("userId") long userId, @Param("roleIds") List<Long> roleIds);

    /**
     * 检查事务写入后的有效管理员数量；必须持有固定保护锁。
     * @param permissions 当前 SQL 的全部初始系统权限及后台入口码
     * @return 启用且关联启用 ALL admin 角色、具有全部指定启用权限的用户数
     */
    long effectiveAdminCount(@Param("permissions") List<String> permissions);
    /**
     * 按用户名查询并锁定用户，供密码登录串行校验凭据。
     * <p>调用方须处于业务事务中；查询不提前过滤禁用用户，凭据确认后的状态检查由 Service 负责。</p>
     *
     * @param username 已完成入口格式校验及首尾空白处理的用户名
     * @return 内部用户实体，包含密码哈希；不存在时为 null
     */
    SysUser lockByUsername(@Param("username") String username);
    /**
     * 按本站用户 ID 取得用户行锁。
     * <p>密码、绑定、解绑和登录共用此锁；调用方负责事务、身份及状态检查。</p>
     *
     * @param userId 服务端确认的本站用户 ID
     * @return 用户实体；不存在时为 null
     */
    SysUser lockById(@Param("userId") long userId);
    /**
     * 仅更新昵称、邮箱、头像和简介四项资料。
     * <p>可空字段按传入值写入 NULL；行锁与 If-Revision 校验由调用服务完成。</p>
     *
     * @param user 已在原资料上应用 PATCH 出现性语义的用户数据
     * @return 更新影响行数
     */
    int updateProfile(@Param("user") SysUser user);
    /**
     * 为指定用户更新或清除本站密码哈希。
     * <p>hash 为 null 表示清除密码；最近认证、旧密码、最后登录方式及会话撤销由 Service 协调。</p>
     *
     * @param userId 已锁定的用户 ID
     * @param hash 新 BCrypt 哈希；清除密码时为 null
     * @return 更新影响行数
     */
    int updatePassword(@Param("userId") long userId, @Param("hash") String hash);
    /**
     * 使用数据库当前毫秒时间更新最近登录时间。
     * <p>连接使用 UTC，调用应与用户锁及本站会话签发处于同一业务事务。</p>
     *
     * @param userId 已确认启用且持有行锁的用户 ID
     * @return 更新影响行数
     */
    int updateLastLogin(@Param("userId") long userId);
    /**
     * 为新用户分配当前启用的 member 角色。
     * <p>按 user_id 与 role_id 写入复合键关联；角色不存在或禁用时不插入，调用方须使注册事务失败。</p>
     *
     * @param userId 当前注册事务创建的用户 ID
     * @return 成功插入为 1，未找到可用 member 为 0
     */
    int assignMember(@Param("userId") long userId);
}
