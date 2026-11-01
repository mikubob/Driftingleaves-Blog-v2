package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.AdminUserCreateDTO;
import com.xuan.dto.AdminUserQueryDTO;
import com.xuan.dto.AdminUserUpdateDTO;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.LoginDTO;
import com.xuan.dto.PasswordClearDTO;
import com.xuan.dto.PasswordSetDTO;
import com.xuan.dto.ProfilePatchDTO;
import com.xuan.dto.RegisterDTO;
import com.xuan.dto.UserRolesDTO;
import com.xuan.entity.SysUser;
import com.xuan.result.PageResult;
import com.xuan.vo.AdminUserProfileVO;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.MeVO;
import com.xuan.vo.OAuthBindingVO;
import com.xuan.vo.SessionVO;
import com.xuan.vo.UserAdminVO;
import com.xuan.vo.UserProfileVO;

import java.util.List;

/**
 * 本站身份从会话取得，后台传入的目标 ID 只能表示操作对象，不能代替操作者身份。
 */
public interface IUserService extends IService<SysUser> {

    /** 检查后台和业务权限后分页，列表和总数用同样条件，全部已分配角色都返回，不带逐条版本。 */
    PageResult<UserAdminVO> adminList(AdminUserQueryDTO query, Long roleId);

    /** 检查后台和业务权限后，同一快照里取资料、角色和整体版本，用户不存在返回 404。 */
    AdminUserProfileVO adminDetail(long userId);

    /** 后台创建密码账号不分默认角色，唯一键冲突就全部回滚。 */
    UserAdminVO adminCreate(AdminUserCreateDTO request);

    /** 先拿保护锁，再按 ID 锁用户，锁内重查身份、权限和版本，禁用前先撤销会话。 */
    AdminUserProfileVO adminUpdate(long userId, AdminUserUpdateDTO request, String revision);

    /** 同一保护锁事务里完整替换启用角色，保留最后管理员，提交前撤销目标用户全部会话。 */
    AdminUserProfileVO adminAssignRoles(long userId, UserRolesDTO request, String revision);

    /** 整批核对用户版本后仅修改启用状态，保留最后管理员；实际禁用时撤销会话，失败整批回滚。 */
    BatchVersionsVO adminSetStatusBatch(List<BatchItemDTO> items, int status);

    /** 整批校验版本和内容引用后物理删除，级联关联并撤销全部会话，保留最后管理员及历史记录。 */
    void adminDeleteBatch(List<BatchItemDTO> items);

    /** 用户和 member 关联一起创建，注册成功不自动登录。 */
    MeVO register(RegisterDTO request);

    /** 先锁用户再验密码，按登录端指定的秒数发独立 Token、记录登录时间。 */
    SessionVO login(LoginDTO request, int timeoutSeconds);

    /** 同一用户锁内取资料，并用全部角色关联一起算整体版本。 */
    UserProfileVO me();

    /** 没传的字段不改，同一行锁事务里查版本并只写允许的资料。 */
    UserProfileVO patch(ProfilePatchDTO request, String revision);

    /** 最近认证和旧密码都通过后才能改密码，提交前撤销全部会话。 */
    void setPassword(PasswordSetDTO request);

    /** 清密码后要保留至少一种配置可用的平台登录，提交前撤销全部会话。 */
    void clearPassword(PasswordClearDTO request);

    /** 只返回当前配置可用的平台绑定和创建时间。 */
    List<OAuthBindingVO> bindings();

    /** 用户锁内检查不能删掉最后登录方式，删除后撤销全部会话。 */
    void unbind(String source);

    /** 拦截器和绑定等操作用它查当前账号，不能只信登录时的状态。 */
    long requireCurrent();

    /** 用验证过的平台身份查找或首次注册，并发唯一键冲突后再查成功写入的记录。 */
    long resolveOAuth(String source, String uuid, String nickname, String avatar);

    /** 绑定在原用户锁内重查会话，不创建或切换其他账号。 */
    OAuthBindingVO bindOAuth(long userId, String tokenHash, String source, String uuid);

    /** 票据消费后，用户锁内重查原身份仍绑定再发会话，兑换等待不能刷新认证时间。 */
    SessionVO loginOAuth(long userId, String source, String uuid, long authenticatedAt, int timeoutSeconds);
}
