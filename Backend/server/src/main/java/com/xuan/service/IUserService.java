package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.LoginDTO;
import com.xuan.dto.AdminUserCreateDTO;
import com.xuan.dto.AdminUserQueryDTO;
import com.xuan.dto.AdminUserUpdateDTO;
import com.xuan.dto.UserRolesDTO;
import com.xuan.result.PageResult;
import com.xuan.vo.AdminUserProfileVO;
import com.xuan.vo.UserAdminVO;
import com.xuan.dto.PasswordClearDTO;
import com.xuan.dto.PasswordSetDTO;
import com.xuan.dto.ProfilePatchDTO;
import com.xuan.dto.RegisterDTO;
import com.xuan.entity.SysUser;
import com.xuan.vo.MeVO;
import com.xuan.vo.OAuthBindingVO;
import com.xuan.vo.SessionVO;
import com.xuan.vo.UserProfileVO;
import java.util.List;

/** 本站身份及后台用户业务；操作者来自会话，后台目标 ID 不能充当身份。 */
public interface IUserService extends IService<SysUser> {
    /** 双权限检查后按相同过滤条件分页，完整返回已分配角色，不提供逐条版本。 */
    PageResult<UserAdminVO> adminList(AdminUserQueryDTO query, Long roleId);
    /** 双权限检查后从一致快照读取用户资料、角色和聚合版本，不存在返回 404。 */
    AdminUserProfileVO adminDetail(long userId);
    /** 后台创建密码账号，不分配默认角色；唯一冲突整笔回滚。 */
    UserAdminVO adminCreate(AdminUserCreateDTO request);
    /** 保护锁后按 ID 顺序锁用户，锁内复核身份、权限及版本；禁用前完成会话撤销。 */
    AdminUserProfileVO adminUpdate(long userId, AdminUserUpdateDTO request, String revision);
    /** 同一保护锁事务完整替换启用角色集合，保护最后管理员并在提交前撤销目标全部会话。 */
    AdminUserProfileVO adminAssignRoles(long userId, UserRolesDTO request, String revision);
    /** 用户与 member 同事务创建；不自动登录。 */
    MeVO register(RegisterDTO request);
    /** 锁定用户后验证密码、签发独立 Token 并提交登录时间。 */
    SessionVO login(LoginDTO request);
    /** 同一用户锁下取得资料与包含全部角色关联的聚合版本。 */
    UserProfileVO me();
    /** 缺失字段保持；同一行锁事务校验版本并写白名单资料。 */
    UserProfileVO patch(ProfilePatchDTO request, String revision);
    /** 最近认证及旧密码验证成功后修改密码，提交前撤销全部会话。 */
    void setPassword(PasswordSetDTO request);
    /** 保留至少一种配置可用的平台登录方式，提交前撤销全部会话。 */
    void clearPassword(PasswordClearDTO request);
    /** 只返回当前配置可用的平台绑定及其创建时间。 */
    List<OAuthBindingVO> bindings();
    /** 用户行锁内保护最后登录方式，删除后撤销全部会话。 */
    void unbind(String source);
    /** 供条件鉴权和拦截器检查当前账号，不信登录时状态快照。 */
    long requireCurrent();
    /** 已验证平台身份的首次注册或查找；并发唯一冲突重新查询赢家。 */
    long resolveOAuth(String source, String uuid, String nickname, String avatar);
    /** BIND 在原用户锁内重新检查会话，不创建或切换其他账号。 */
    OAuthBindingVO bindOAuth(long userId, String tokenHash, String source, String uuid);
    /** 票据消费后，用户锁内重查原身份仍绑定并签发会话；认证时间不因兑换延迟刷新。 */
    SessionVO loginOAuth(long userId, String source, String uuid, long authenticatedAt);
}
