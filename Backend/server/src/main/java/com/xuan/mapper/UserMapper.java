package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.entity.SysUser;
import org.apache.ibatis.annotations.Param;

/** 用户写入使用显式白名单，锁与会话撤销由 Service 协调。 */
public interface UserMapper extends BaseMapper<SysUser> {
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
