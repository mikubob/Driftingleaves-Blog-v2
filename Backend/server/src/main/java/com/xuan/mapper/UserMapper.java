package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.AdminUserQueryDTO;
import com.xuan.entity.SysRole;
import com.xuan.entity.SysUser;
import com.xuan.vo.UserRoleVO;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 用户数据访问接口
 */
@Mapper
public interface UserMapper extends BaseMapper<SysUser> {

    /**
     * 发生唯一键冲突后，检查用户名和邮箱冲突的字段名，修改时排除当前用户。
     *
     * @param user 待校验的用户资料
     * @return 冲突字段名列表（username 或 email）
     */
    List<String> findIdentityConflicts(@Param("user") SysUser user);

    /**
     * 分页查询后台用户列表，按创建时间与用户 ID 倒序排列。
     *
     * @param page 分页参数
     * @param query 查询条件
     * @param roleId 角色 ID 筛选，可为空
     * @return 用户分页结果
     */
    IPage<SysUser> adminPage(IPage<SysUser> page,
            @Param("query") AdminUserQueryDTO query,
            @Param("roleId") Long roleId);

    /**
     * 查询用户已分配的全部角色（含禁用角色），按角色 ID 升序排列。
     *
     * @param userId 用户 ID
     * @return 角色列表
     */
    List<UserRoleVO> assignedRoles(@Param("userId") long userId);

    /**
     * 批量查询多个用户已分配的全部角色（含禁用角色），按用户 ID 与角色 ID 升序排列。
     *
     * @param userIds 用户 ID 集合
     * @return 带用户 ID 的角色列表
     */
    List<UserRoleRow> assignedRolesByUsers(@Param("userIds") Collection<Long> userIds);

    /**
     * 锁定初始化管理员角色行（role_id = 1），用于串行保护最后一个有效管理员。
     *
     * @return 锁定的角色 ID，缺失时返回 null
     */
    Long lockAdminGuard();

    /**
     * 按角色 ID 升序加共享锁查询候选角色列表
     *
     * @param roleIds 角色 ID 列表
     * @return 存在的角色列表
     */
    List<SysRole> lockRoles(@Param("roleIds") List<Long> roleIds);

    /**
     * 完整更新后台用户资料和状态，显式写入可空字段以支持清空。
     *
     * @param user 待更新的用户实体
     * @return 受影响行数
     */
    int updateAdmin(@Param("user") SysUser user);

    /**
     * 检查用户是否存在文章、文件、评论或留言等内容引用
     *
     * @param userId 用户 ID
     * @return 是否存在引用
     */
    boolean hasContentReferences(@Param("userId") long userId);

    /**
     * 清空指定用户的全部角色关联
     *
     * @param userId 用户 ID
     * @return 删除行数
     */
    int deleteRoles(@Param("userId") long userId);

    /**
     * 批量插入用户与角色的复合主键关联
     *
     * @param userId 用户 ID
     * @param roleIds 角色 ID 列表
     * @return 插入行数
     */
    int insertRoles(@Param("userId") long userId, @Param("roleIds") List<Long> roleIds);

    /**
     * 统计当前有效管理员数量，调用方须先持有管理保护锁。
     *
     * @param permissions 有效管理员必须具备的全部权限编码列表
     * @return 有效管理员人数
     */
    long effectiveAdminCount(@Param("permissions") List<String> permissions);

    /**
     * 按用户名加行锁查询用户记录，调用方须处于写事务内。
     *
     * @param username 用户名
     * @return 锁定的用户实体，不存在时返回 null
     */
    SysUser lockByUsername(@Param("username") String username);

    /**
     * 按邮箱加行锁查询用户记录，调用方须处于写事务内。
     *
     * @param email 邮箱地址
     * @return 锁定的用户实体，不存在时返回 null
     */
    SysUser lockByEmail(@Param("email") String email);

    /**
     * 按用户 ID 加行锁查询用户记录，调用方须处于写事务内。
     *
     * @param userId 用户 ID
     * @return 锁定的用户实体，不存在时返回 null
     */
    SysUser lockById(@Param("userId") long userId);

    /**
     * 更新本人允许修改的资料字段，显式写入可空字段以支持清空。
     *
     * @param user 待更新的用户资料实体
     * @return 受影响行数
     */
    int updateProfile(@Param("user") SysUser user);

    /**
     * 更新用户密码哈希，传入 null 时清除本站密码。
     *
     * @param userId 用户 ID
     * @param hash BCrypt 密码哈希，清除密码时为 null
     * @return 受影响行数
     */
    int updatePassword(@Param("userId") long userId, @Param("hash") String hash);

    /**
     * 更新用户最近登录时间、IP 与归属地
     *
     * @param userId 用户 ID
     * @param ip 可信客户端 IP
     * @param location 归属地描述
     * @return 受影响行数
     */
    int updateLastLogin(@Param("userId") long userId,
            @Param("ip") String ip,
            @Param("location") String location);

    /**
     * 为新注册用户分配启用状态的 member 角色
     *
     * @param userId 用户 ID
     * @return 插入行数（成功为 1，member 角色不可用时为 0）
     */
    int assignMember(@Param("userId") long userId);

    /**
     * 批量查询用户角色时的关联投影
     */
    @Getter
    @Setter
    class UserRoleRow extends UserRoleVO {

        private Long userId;
    }
}
