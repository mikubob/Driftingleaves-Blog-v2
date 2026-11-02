package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.AdminEmailCodeDTO;
import com.xuan.dto.AdminLoginDTO;
import com.xuan.dto.AdminUserCreateDTO;
import com.xuan.dto.AdminUserQueryDTO;
import com.xuan.dto.AdminUserUpdateDTO;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.EmailCodeDTO;
import com.xuan.dto.EmailLoginDTO;
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
 * 用户业务接口，当前用户取登录会话，目标id只用于指定操作对象。
 */
public interface IUserService extends IService<SysUser> {

    /** 分页查询用户，先检查后台入口和查询权限；列表和总数使用相同条件，返回全部已分配角色，不带每条记录的版本。 */
    PageResult<UserAdminVO> adminList(AdminUserQueryDTO query, Long roleId);

    /** 根据id查询用户详情及版本，先检查后台入口和查询权限，再在同一个只读事务中读取资料和角色；用户不存在返回 404。 */
    AdminUserProfileVO adminDetail(long userId);

    /** 创建带密码的用户，不分配默认角色，用户名或邮箱重复时全部回滚。 */
    UserAdminVO adminCreate(AdminUserCreateDTO request);

    /** 根据id更新用户，先拿保护锁，再按id锁住用户；加锁后重新检查身份、权限和版本，禁用前先撤销会话。 */
    AdminUserProfileVO adminUpdate(long userId, AdminUserUpdateDTO request, String revision);

    /** 根据id更新用户角色，在同一个保护锁事务中完整替换为启用的角色；保留最后一个有效管理员，提交前撤销该用户的全部会话。 */
    AdminUserProfileVO adminAssignRoles(long userId, UserRolesDTO request, String revision);

    /** 批量启用或禁用用户，先检查每个用户的版本，只修改启用状态；保留最后一个有效管理员，实际禁用时撤销会话，任一失败则整批回滚。 */
    BatchVersionsVO adminSetStatusBatch(List<BatchItemDTO> items, int status);

    /** 批量删除用户，先检查全部版本和内容引用，再删除用户及关联、撤销全部会话；保留最后一个有效管理员和历史记录。 */
    void adminDeleteBatch(List<BatchItemDTO> items);

    /** 注册用户并分配 member 角色，用户和角色关联一起提交，注册成功后不自动登录。 */
    MeVO register(RegisterDTO request);

    /** 使用用户名和密码登录，先锁住用户再检查密码，按登录端指定的有效秒数生成独立 Token，并记录登录时间。 */
    SessionVO login(LoginDTO request, int timeoutSeconds);

    /** 注册和邮箱登录分别发送验证码，SMTP 接受后才返回成功。 */
    void sendEmailCode(EmailCodeDTO request);

    /** 发送后台登录验证码，先锁住用户，检查用户名、密码、当前邮箱和后台权限。 */
    void sendAdminEmailCode(AdminEmailCodeDTO request);

    /** 按当前绑定邮箱锁用户，消费邮件验证码后签发前台会话。 */
    SessionVO loginEmail(EmailLoginDTO request);

    /** 登录后台，在同一个用户锁内检查用户名、密码、当前邮箱、邮件验证码和后台权限。 */
    SessionVO loginAdmin(AdminLoginDTO request);

    /** 查询当前用户资料，在用户锁内读取资料和全部角色，并一起计算版本。 */
    UserProfileVO me();

    /** 修改当前用户资料，加锁并检查版本，只更新允许修改的字段，没传的字段保持不变。 */
    UserProfileVO patch(ProfilePatchDTO request, String revision);

    /** 设置或修改当前用户密码，先检查最近认证，已有密码时还要检查旧密码，提交前撤销全部会话。 */
    void setPassword(PasswordSetDTO request);

    /** 清除当前用户密码，至少保留一种已配置且可用的第三方登录方式，提交前撤销全部会话。 */
    void clearPassword(PasswordClearDTO request);

    /** 查询当前用户的第三方绑定，只返回当前可用的平台和绑定时间。 */
    List<OAuthBindingVO> bindings();

    /** 解除第三方绑定，在用户锁内确认仍有其他登录方式，解绑后撤销全部会话。 */
    void unbind(String source);

    /** 检查当前登录用户，供拦截器和绑定等操作使用，不能只依赖登录时的账号状态。 */
    long requireCurrent();

    /** 根据第三方身份查询已绑定用户，未绑定时要求先完成邮箱注册，不自动创建账号。 */
    long resolveOAuth(String source, String uuid, String nickname, String avatar);

    /** 绑定第三方账号，在原用户锁内重新检查会话，不创建或切换其他账号。 */
    OAuthBindingVO bindOAuth(long userId, String tokenHash, String source, String uuid);

    /** 使用第三方账号登录，一次性使用票据后，在用户锁内确认原身份仍已绑定，再创建会话；兑换等待不能刷新认证时间。 */
    SessionVO loginOAuth(long userId, String source, String uuid, long authenticatedAt, int timeoutSeconds);
}
