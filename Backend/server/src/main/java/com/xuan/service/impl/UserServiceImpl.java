package com.xuan.service.impl;

import com.xuan.annotation.OperationLog;
import com.xuan.utils.TokenUtils;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xuan.dto.AdminUserCreateDTO;
import com.xuan.dto.AdminUserQueryDTO;
import com.xuan.dto.AdminUserUpdateDTO;
import com.xuan.dto.UserRolesDTO;
import com.xuan.entity.SysRole;
import com.xuan.result.PageResult;
import com.xuan.security.AuthorizationProvider;
import com.xuan.vo.AdminUserProfileVO;
import com.xuan.vo.UserAdminVO;
import com.xuan.dto.LoginDTO;
import com.xuan.dto.PasswordClearDTO;
import com.xuan.dto.PasswordSetDTO;
import com.xuan.dto.ProfilePatchDTO;
import com.xuan.dto.RegisterDTO;
import com.xuan.entity.SysUser;
import com.xuan.entity.SysUserOauth;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.AuthorizationMapper;
import com.xuan.mapper.OAuthBindingMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.service.IUserService;
import com.xuan.client.OAuthProviderClient;
import com.xuan.session.SessionManager;
import com.xuan.utils.UrlPolicy;
import com.xuan.vo.MeVO;
import com.xuan.vo.OAuthBindingVO;
import com.xuan.vo.SessionVO;
import com.xuan.vo.UserProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

/** 身份相关写入都锁住 sys_user，Redis 撤销会话成功后才能提交数据库。 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, SysUser> implements IUserService {
    private final UserMapper users;
    private final OAuthBindingMapper oauth;
    private final AuthorizationMapper authorization;
    private final AuthorizationProvider permissions;
    private final BCryptPasswordEncoder encoder;
    private final SessionManager sessions;
    private final OAuthProviderClient providers;
    private final PlatformTransactionManager transactionManager;
    private final JsonMapper json;
    private final Clock clock;

    /**
     * 同一快照里分页取全站用户和完整角色，只返回约定资料，不直接返回内部实体。
     * @param query 已完成入口校验的分页、字面关键词和状态过滤
     * @param roleId 已经检查过的可空角色 ID，包含禁用角色的关联
     * @return 同过滤条件的分页统计和脱敏资料
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<UserAdminVO> adminList(AdminUserQueryDTO query, Long roleId) {
        requireAdmin("sys:user:query");
        return PageResult.from(users.adminPage(new Page<>(query.page(), query.pageSize()), query, roleId)
                .convert(this::adminView));
    }

    /**
     * 详情和角色在同一快照里取，确保资料和整体版本对得上。
     * @param userId 全局目标用户 ID，不作为操作者身份
     * @return 脱敏详情及带引号版本；不存在返回 404
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminUserProfileVO adminDetail(long userId) {
        requireAdmin("sys:user:query");
        SysUser user = users.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "NOT_FOUND", "用户不存在");
        }
        return adminProfile(user);
    }

    /**
     * 保护锁和操作者行锁内重查后台、业务权限，创建没有角色的本站密码账号。
     * @param request 已经检查过的创建字段；不接受授权和审计字段
     * @return 回查数据库审计时间后的脱敏资料，不创建会话
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "create", target = "sys:user", permission = "sys:user:create")
    public UserAdminVO adminCreate(AdminUserCreateDTO request) {
        // 1. 管理写入先拿保护锁，再锁操作者，锁内重新检查身份和权限。
        lockAdminTarget(sessions.currentUserId(), "sys:user:create");
        UrlPolicy.requireValid(request.getAvatar());
        // 2. 只写允许创建的字段，不能沿用公开注册自动分配 member 的做法。
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(encoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setAvatar(request.getAvatar());
        user.setIntro(request.getIntro());
        user.setStatus(request.getStatus());
        users.insert(user);
        return adminView(users.selectById(user.getUserId()));
    }

    /**
     * 保护锁和用户锁内完整替换资料、状态；禁用管理员后也要留下有效管理入口。
     * @param userId 目标用户 ID
     * @param request 已经检查过的完整资料，不含密码、用户名和角色
     * @param revision 详情整体版本，缺失、格式错误和过期分别返回 428、400、412
     * @return 更新后的资料及版本；撤销失败时数据库整笔回滚
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "update", target = "sys:user", permission = "sys:user:update", targetIdArgument = 0)
    public AdminUserProfileVO adminUpdate(long userId, AdminUserUpdateDTO request, String revision) {
        // 1. 先锁保护点，再按 ID 锁用户，然后重查身份、权限和版本，管理入口的检查才不会并发漏掉。
        SysUser user = lockAdminTarget(userId, "sys:user:update");
        checkRevision(user, revision);
        UrlPolicy.requireValid(request.getAvatar());
        boolean disabling = user.getStatus() == 1 && request.getStatus() == 0;
        boolean admin = authorization.roleIds(userId).contains(1L);
        // 2. 只替换资料和状态，用户名、密码和已有角色关联都不改。
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setAvatar(request.getAvatar());
        user.setIntro(request.getIntro());
        user.setStatus(request.getStatus());
        users.updateAdmin(user);
        // 3. 先确认修改后仍有有效管理员，再撤销会话；Redis 失败就不提交数据库。
        if (disabling) {
            if (admin) {
                requireRemainingAdmin();
            }
            sessions.revokeAll(userId);
        }
        return adminProfile(users.selectById(userId));
    }

    /**
     * 同一事务整体替换角色，所有角色都得存在且启用，不能只保存一部分。
     * @param userId 目标用户 ID
     * @param request 已经检查过的格式和重复项的完整集合，空集合清空
     * @param revision 用户整体版本，角色变更与本人资料共用此版本
     * @return 新资料及版本；保护检查或 Redis 撤销失败使替换整笔回滚
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "grant", target = "sys:user", permission = "sys:user:assignRole", targetIdArgument = 0)
    public AdminUserProfileVO adminAssignRoles(long userId, UserRolesDTO request, String revision) {
        // 1. 先拿固定保护锁和升序用户锁，再查当前权限及整体版本。
        SysUser user = lockAdminTarget(userId, "sys:user:assignRole");
        checkRevision(user, revision);
        List<Long> next = request.roleIds().stream().sorted().toList();
        // 2. 用户锁之后按 ID 升序锁角色，有缺失或禁用项就整笔失败。
        if (!next.isEmpty()) {
            List<SysRole> roles = users.lockRoles(next);
            if (roles.size() != next.size() || roles.stream().anyMatch(role -> role.getStatus() != 1)) {
                throw new BusinessException(400, "VALIDATION_ERROR", "roleIds须全部存在且启用");
            }
        }
        List<Long> previous = authorization.roleIds(userId);
        if (!previous.equals(next)) {
            // 3. 清掉旧关联再插入新关联，随后检查最后管理员；目标集合相同则保持不变。
            users.deleteRoles(userId);
            if (!next.isEmpty()) {
                users.insertRoles(userId, next);
            }
            if (user.getStatus() == 1 && previous.contains(1L) && !next.contains(1L)) {
                requireRemainingAdmin();
            }
            // 4. 权限和菜单实时查询，不用旧缓存；全部会话撤销成功后才能提交。
            sessions.revokeAll(userId);
        }
        return adminProfile(users.selectById(userId));
    }

    /**
     * 按数据库最新状态查操作者启用、会话有效、后台入口和业务权限，不能按角色名直接放行。
     * @param permission 当前操作所需业务权限，全局操作不按 SELF 收缩
     */
    private void requireAdmin(String permission) {
        long actor = requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        permissions.requireScope(actor, "admin:access");
        permissions.requireScope(actor, permission);
    }

    /**
     * 管理写入先锁固定保护行，再升序锁操作者和目标用户，最后重查身份和权限。
     * @param userId 目标用户 ID，创建时传操作者自身
     * @param permission 必要持有的具体业务权限
     * @return 已锁定的目标用户，不存在时在权限检查后返回 404
     */
    private SysUser lockAdminTarget(long userId, String permission) {
        if (users.lockAdminGuard() == null) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "管理员保护角色未初始化");
        }
        long actor = sessions.currentUserId();
        SysUser target = null;
        for (long id : LongStream.of(actor, userId).distinct().sorted().toArray()) {
            SysUser locked = users.lockById(id);
            if (id == userId) {
                target = locked;
            }
        }
        requireAdmin(permission);
        if (target == null) {
            throw new BusinessException(404, "NOT_FOUND", "用户不存在");
        }
        return target;
    }

    /**
     * 检查修改后还剩多少有效管理员，调用方必须先拿到固定角色保护锁。
     * @throws BusinessException 本次变更将失去最后有效管理员，触发数据库回滚
     */
    private void requireRemainingAdmin() {
        if (users.effectiveAdminCount(AuthorizationProvider.ADMIN_PERMISSIONS) == 0) {
            throw new BusinessException(409, "LAST_ADMIN", "必须保留至少一个有效管理员");
        }
    }

    /**
     * 目标用户锁内先查整体版本，再改状态或撤销 Redis 会话。
     * @param user 已锁定的当前用户
     * @param revision 客户端原样携带的版本
     */
    private void checkRevision(SysUser user, String revision) {
        if (revision == null) {
            throw new BusinessException(428, "PRECONDITION_REQUIRED", "缺少If-Revision");
        }
        if (!revision.matches("\"[0-9a-f]{64}\"")) {
            throw new BusinessException(400, "VALIDATION_ERROR", "If-Revision格式错误");
        }
        if (!revision.equals(profile(user).revision())) {
            throw new BusinessException(412, "REVISION_CONFLICT", "资料已修改，请重新获取");
        }
    }

    /**
     * 同一事务快照或用户锁内读取后台资料，版本和本人资料接口共用。
     * @param user 当前内部实体
     * @return 脱敏资料及仅由业务字段和完整角色集合生成的版本
     */
    private AdminUserProfileVO adminProfile(SysUser user) {
        return new AdminUserProfileVO(adminView(user), profile(user).revision());
    }

    /**
     * 只返回约定的后台用户资料，全部已分配角色都保留，包括禁用角色。
     * @param user 当前内部用户实体
     * @return 不含密码哈希、第三方身份和 Token 的后台资料
     */
    private UserAdminVO adminView(SysUser user) {
        return new UserAdminVO(user.getUserId(), user.getUsername(), user.getNickname(), user.getEmail(),
                user.getAvatar(), user.getIntro(), user.getStatus(), user.getPasswordHash() != null,
                user.getLastLoginTime(), user.getCreateTime(), user.getUpdateTime(), authorization.roleIds(user.getUserId()));
    }

    /**
     * 注册启用用户并分配 member，用户和角色关联要一起提交。
     * @param request 已完成入口校验的注册资料，不含角色或状态
     * @return 数据库生成审计时间后的脱敏资料，不自动创建会话
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MeVO register(RegisterDTO request) {
        // 1. 只写注册允许的字段，密码用随机盐编码，账号状态由服务端确定。
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setNickname(request.nickname());
        user.setPasswordHash(encoder.encode(request.password()));
        user.setStatus(1);
        // 2. 用户和 member 关联在同一事务创建，初始化角色缺了就全部回滚。
        createMember(user);
        // 3. 回查数据库生成的时间，只返回用户资料，不自动登录。
        return view(users.selectById(user.getUserId()));
    }

    /**
     * 锁住用户后再验密码和状态、发会话、改最近登录时间；用户不存在、没密码或密码错误都报认证失败，事务没提交就撤销这次 Token。
     * @param request 已完成入口校验的本站登录凭据
     * @return 独立本站会话及脱敏用户资料
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SessionVO login(LoginDTO request) {
        // 1. 先锁用户再验凭据，避免旧密码验证通过后越过并发退出登录的时点又发 Token。
        SysUser user = users.lockByUsername(request.username());
        // 2. 用户不存在、没密码或密码输错统一报失败，不能透露账号类型。
        if (user == null || user.getPasswordHash() == null
                || !encoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(401, "AUTH_FAILED", "账号或密码错误");
        }
        // 3. 凭据确认后再查账号启用状态，然后发会话、更新登录时间。
        enabled(user);
        return createSession(user, clock.millis(), "PASSWORD");
    }

    /**
     * 从当前会话取用户，并实时检查用户还在且已启用。
     * @return 当前启用用户 ID
     * @throws BusinessException 用户不存在或账号已禁用
     */
    @Override
    public long requireCurrent() {
        // 身份从本站会话取，账号状态实时查，不能只信登录时读到的资料。
        long userId = sessions.currentUserId();
        enabled(users.selectById(userId));
        return userId;
    }

    /**
     * 同一用户锁事务里取本人资料和全部角色关联，再计算对应整体版本。
     * @return 脱敏资料及带引号的 X-Revision 值
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserProfileVO me() {
        return profile(lockCurrent());
    }

    /**
     * 本人行锁内核对整体版本，只改允许的资料字段；没传保持、null 清空可空字段，保存失败整笔回滚。
     * @param request 已完成入口校验并保留字段出现性的 PATCH 请求
     * @param revision 原样携带的带引号 If-Revision，缺失时返回 428
     * @return 更新后的脱敏资料及新整体版本
     * @throws BusinessException 版本格式错误、版本过期或头像 URL 不合法
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserProfileVO patch(ProfilePatchDTO request, String revision) {
        // 1. 先确认本人身份并锁住用户，再读取这次写入要检查的版本。
        SysUser user = lockCurrent();
        // 2. 分清没传、格式不对和版本过期，不能悄悄覆盖别人的资料修改。
        checkRevision(user, revision);
        // 3. 只改客户端实际传来的允许字段，没传保持，null 只清空可空字段。
        if (request.hasNickname()) {
            user.setNickname(request.getNickname());
        }
        if (request.hasEmail()) {
            user.setEmail(request.getEmail());
        }
        if (request.hasAvatar()) {
            UrlPolicy.requireValid(request.getAvatar());
            user.setAvatar(request.getAvatar());
        }
        if (request.hasIntro()) {
            user.setIntro(request.getIntro());
        }
        // 4. 保存后回查数据库时间，并按新资料返回整体版本。
        users.updateProfile(user);
        return profile(users.selectById(user.getUserId()));
    }

    /**
     * 设置或修改本人密码，最近认证、旧密码检查和写入都共用用户锁；第一次设置要最近 OAuth 登录且不能传旧密码，Redis 撤销全部会话失败就回滚。
     * @param request 已完成入口长度校验的新密码及条件必填的当前密码
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void setPassword(PasswordSetDTO request) {
        // 1. 先锁本人，再查这枚 Token 的最近认证；第一次设密码必须来自 OAuth 登录。
        SysUser user = lockCurrent();
        sessions.requireRecent(sessions.currentToken(), user.getUserId(), user.getPasswordHash() == null);
        // 2. 按原来有没有密码分别检查，已有密码时不能靠漏传旧密码绕过验证。
        if (user.getPasswordHash() == null) {
            if (request.hasCurrentPassword()) {
                throw new BusinessException(400, "VALIDATION_ERROR", "无密码账号必须省略currentPassword");
            }
        } else {
            verifyPassword(user, request.getCurrentPassword());
        }
        // 3. 写入新密码哈希后撤销全部会话，Redis 失败就回滚数据库修改。
        users.updatePassword(user.getUserId(), encoder.encode(request.getNewPassword()));
        sessions.revokeAll(user.getUserId());
    }

    /**
     * 先查最近认证和旧密码，再把密码清成 null，必须留下至少一种配置可用的平台登录方式；都在用户锁内完成，提交前撤销全部会话，失败就回滚。
     * @param request 已完成入口校验的当前密码
     * @throws BusinessException 尚无密码、凭据错误或清除后没有可用登录方式
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void clearPassword(PasswordClearDTO request) {
        // 1. 同一用户锁内检查最近认证、是否已有密码和密码是否正确。
        SysUser user = lockCurrent();
        sessions.requireRecent(sessions.currentToken(), user.getUserId(), false);
        if (user.getPasswordHash() == null) {
            throw new BusinessException(409, "STATE_CONFLICT", "尚未设置密码");
        }
        verifyPassword(user, request.currentPassword());
        // 2. 只有配置可用的平台绑定才算其他登录方式，不能清掉最后一种。
        if (!hasAvailableBinding(user.getUserId(), null)) {
            throw new BusinessException(409, "LAST_LOGIN_METHOD", "至少保留一种可用登录方式");
        }
        // 3. 密码要真正清成 NULL，数据库提交前再撤销全部本站会话。
        users.updatePassword(user.getUserId(), null);
        sessions.revokeAll(user.getUserId());
    }

    /**
     * 获取本人当前还能用的平台绑定，不返回第三方身份标识。
     * @return 按 source 升序的绑定平台和创建时间，无可用绑定时为空列表
     */
    @Override
    public List<OAuthBindingVO> bindings() {
        return oauth.findByUser(requireCurrent()).stream()
                .filter(binding -> providers.enabledSources().contains(binding.getSource()))
                .map(binding -> new OAuthBindingVO(binding.getSource(), binding.getCreateTime())).toList();
    }

    /**
     * 本人行锁内查最近认证和剩余登录方式，再按当前用户、平台删除绑定；全部会话撤销成功才提交，Redis 失败就恢复绑定。
     * @param source 待解绑的平台标识，必须在服务端启用的名单中
     * @throws BusinessException 未绑定该平台或解绑将移除最后一种可用登录方式
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void unbind(String source) {
        // 1. 先看平台是否可用，再锁本人并检查最近认证。
        providers.requireEnabled(source);
        SysUser user = lockCurrent();
        sessions.requireRecent(sessions.currentToken(), user.getUserId(), false);
        // 2. 锁内确认绑定还在，移除后也得有可用登录方式。
        if (oauth.findByUser(user.getUserId()).stream().noneMatch(binding -> source.equals(binding.getSource()))) {
            throw new BusinessException(404, "NOT_FOUND", "未绑定该平台");
        }
        if (user.getPasswordHash() == null && !hasAvailableBinding(user.getUserId(), source)) {
            throw new BusinessException(409, "LAST_LOGIN_METHOD", "至少保留一种可用登录方式");
        }
        // 3. 用户和平台一起限制删除目标，全部会话撤销成功后才能提交。
        oauth.deleteBinding(user.getUserId(), source);
        sessions.revokeAll(user.getUserId());
    }

    /**
     * 只按已验证的平台身份找启用用户，首次访问一起创建用户、member 和绑定；不能按昵称或邮箱合并，唯一键冲突先回滚再用新事务查获胜记录，最多三次。
     * @param source 服务端确认的平台标识
     * @param uuid 已由平台验证的身份标识，原样保留
     * @param nickname 初始化展示昵称，空白使用默认值，超长按 Unicode 字符截取
     * @param avatar 初始化头像，非法 URL 丢弃为 null
     * @return 平台身份对应的启用用户 ID
     */
    @Override
    public long resolveOAuth(String source, String uuid, String nickname, String avatar) {
        // 1. 每次尝试都用新事务，唯一键冲突后的失败事务不能接着查写。
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevelName("ISOLATION_READ_COMMITTED");
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return transaction.execute(status -> {
                    // 2. 只认平台验证过的 source 和 uuid，不能用昵称或邮箱合并账号。
                    SysUserOauth existing = oauth.findIdentity(source, uuid);
                    if (existing != null) {
                        // 2.已有绑定时先锁用户，再查一次绑定归属，避免等待期间身份变了。
                        SysUser user = users.lockById(existing.getUserId());
                        enabled(user);
                        SysUserOauth current = oauth.findIdentity(source, uuid);
                        if (current == null || !current.getUserId().equals(user.getUserId())) {
                            throw new BusinessException(409, "STATE_CONFLICT", "绑定已变化，请重新发起");
                        }
                        return user.getUserId();
                    }
                    // 2.首次注册生成本站用户名，密码和邮箱留 NULL，只初始化展示资料。
                    SysUser user = new SysUser();
                    user.setUsername("oauth_" + TokenUtils.random().replace('-', '_'));
                    // 昵称按 Unicode 字符截取，头像无效就丢弃，平台身份标识不能改。
                    String display = nickname == null ? "" : nickname.strip();
                    user.setNickname(display.isEmpty() ? "用户" : display.substring(0,
                            display.offsetByCodePoints(0, Math.min(64, display.codePointCount(0, display.length())))));
                    user.setAvatar(UrlPolicy.valid(avatar) ? avatar : null);
                    user.setStatus(1);
                    // 2.用户、member 关联和第三方绑定一起提交，任何一步失败都回滚。
                    createMember(user);
                    insertBinding(user.getUserId(), source, uuid);
                    return user.getUserId();
                });
            } catch (DuplicateKeyException exception) {
                // 3. 身份或随机用户名撞了唯一键，先回滚整笔，再查成功写入的那条记录。
                if (attempt == 2) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("OAuth注册重试未结束");
    }

    /**
     * 原用户锁内重查原会话，再绑定已验证的平台身份；同一身份重复绑定成功，其他身份或已属于他人的身份不能覆盖、转移。
     * @param userId 发起 BIND 时从本站会话保存的用户 ID
     * @param tokenHash 发起时原 Token 的摘要，用于重新检查有效性和最近认证
     * @param source 已验证的平台标识
     * @param uuid 平台确认的原样身份标识
     * @return 绑定平台及数据库生成的创建时间
     * @throws BusinessException 原身份或会话失效，或者第三方绑定冲突
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OAuthBindingVO bindOAuth(long userId, String tokenHash, String source, String uuid) {
        // 1. 锁目标用户后重查原会话，平台回调不能自行切换本站账号。
        enabled(users.lockById(userId));
        sessions.originalToken(userId, tokenHash);
        // 2. 同一身份重复绑定可以成功，同平台另一个身份不能覆盖已有绑定。
        List<SysUserOauth> bindings = oauth.findByUser(userId);
        for (SysUserOauth binding : bindings) {
            if (source.equals(binding.getSource())) {
                if (uuid.equals(binding.getUuid())) {
                    return new OAuthBindingVO(source, binding.getCreateTime());
                }
                throw new BusinessException(409, "OAUTH_BIND_CONFLICT", "该平台身份已被绑定");
            }
        }
        // 3. 数据库唯一键防止身份转移，冲突就报绑定失败并回滚。
        try {
            insertBinding(userId, source, uuid);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "OAUTH_BIND_CONFLICT", "该平台身份已被绑定");
        }
        // 4. 只返回平台和数据库生成的绑定时间，不带 uuid。
        return oauth.findByUser(userId).stream().filter(binding -> source.equals(binding.getSource()))
                .map(binding -> new OAuthBindingVO(source, binding.getCreateTime())).findFirst().orElseThrow();
    }

    /**
     * 用户锁内重查账号状态和绑定归属，再发第三方登录会话；认证时间沿用平台验证时刻，兑换等待不能续期，提交失败就撤销新 Token。
     * @param userId 已消费 LOGIN 票据关联的用户 ID
     * @param source 票据记录的平台标识
     * @param uuid 票据记录的已验证平台身份
     * @param authenticatedAt 平台验证成功时的毫秒时间戳
     * @return 独立本站会话及脱敏用户资料
     * @throws BusinessException 用户不可用或原平台身份已解绑
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SessionVO loginOAuth(long userId, String source, String uuid, long authenticatedAt) {
        // 1. 兑换时重新锁用户并查状态，不能只信回调时查到的结果。
        SysUser user = users.lockById(userId);
        enabled(user);
        // 2. 再查原身份是否仍属于这个用户，旧票据不能恢复已经解绑的身份。
        SysUserOauth binding = oauth.findIdentity(source, uuid);
        if (binding == null || !binding.getUserId().equals(userId)) {
            throw new BusinessException(400, "OAUTH_STATE_INVALID", "授权身份已解绑，请重新发起");
        }
        // 3. 认证时间用平台验证成功的时间，兑换等待不能刷新最近认证窗口。
        return createSession(user, authenticatedAt, "OAUTH");
    }

    /**
     * 锁住当前会话用户，等锁拿到后再查账号状态和 Token 归属。
     * @return 当前启用用户的内部实体
     * @throws BusinessException 账号不可用或等待锁期间会话已撤销
     */
    private SysUser lockCurrent() {
        // 1. 从当前会话找到用户并锁住，不能用客户端给的目标 ID。
        long userId = sessions.currentUserId();
        SysUser user = users.lockById(userId);
        enabled(user);
        // 2. 等行锁时可能已经退出登录，拿到锁后必须再查 Token。
        sessions.requireActive(sessions.currentToken(), userId);
        return user;
    }

    /**
     * 调用方事务里创建用户并分配启用的 member，初始化角色缺了就全部回滚。
     * @param user 待创建的内部用户实体，插入后填入生成的用户 ID
     */
    private void createMember(SysUser user) {
        // 1. 先保存用户，拿数据库自增 ID 来写角色关联。
        users.insert(user);
        // 2. 只分配启用的 member，初始化不完整就让整个注册失败。
        if (users.assignMember(user.getUserId()) != 1) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "member角色未正确初始化");
        }
    }

    /**
     * 调用方事务里插入平台绑定，身份及用户、平台组合都由数据库唯一键防重复。
     * @param userId 服务端已确认的绑定用户 ID
     * @param source 已验证的平台标识
     * @param uuid 平台确认的原样身份标识
     */
    private void insertBinding(long userId, String source, String uuid) {
        SysUserOauth binding = new SysUserOauth();
        binding.setUserId(userId);
        binding.setSource(source);
        binding.setUuid(uuid);
        oauth.insert(binding);
    }

    /**
     * 调用方持有用户锁时，检查排除这个平台后是否还有配置可用的绑定。
     * @param userId 已锁定的用户 ID
     * @param excludingSource 待解绑的平台；null 表示不排除任何平台
     * @return 是否至少保留一个启用且配置完整的平台绑定
     */
    private boolean hasAvailableBinding(long userId, String excludingSource) {
        // 先排除待解绑平台，再看剩下的平台配置是否可用，不能只数绑定行数。
        return oauth.findByUser(userId).stream().anyMatch(binding -> !binding.getSource().equals(excludingSource)
                && providers.enabledSources().contains(binding.getSource()));
    }

    /**
     * 已有密码的账号要验证当前密码，这里不改会话或认证时间。
     * @param user 已确认拥有密码的内部用户实体
     * @param password 请求提供的当前密码，不 trim 或截断
     * @throws BusinessException 未提供当前密码或密码不匹配
     */
    private void verifyPassword(SysUser user, String password) {
        if (password == null) {
            throw new BusinessException(400, "VALIDATION_ERROR", "必须提供currentPassword");
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(401, "AUTH_FAILED", "账号或密码错误");
        }
    }

    /**
     * 检查用户现在是否存在且启用，不能只信登录时的状态。
     * @param user 当前数据库查询结果，允许为 null
     * @throws BusinessException 用户不存在或账号已禁用
     */
    private void enabled(SysUser user) {
        if (user == null) {
            throw new BusinessException(401, "UNAUTHENTICATED", "请重新登录");
        }
        if (user.getStatus() != 1) {
            throw new BusinessException(403, "ACCOUNT_DISABLED", "账号已禁用");
        }
    }

    /**
     * 调用方持有用户锁，在同一事务里发会话并更新最近登录时间。
     * @param user 已锁定且确认启用的用户实体
     * @param authenticatedAt 本次凭据验证成功的毫秒时间戳
     * @param method Token 级认证方式，PASSWORD 或 OAUTH
     * @return 事务成功提交后可返回客户端的独立会话与脱敏资料
     */
    private SessionVO createSession(SysUser user, long authenticatedAt, String method) {
        // 1. 用户锁内发独立 Token，会话组件同时登记事务失败时的清理。
        String token = sessions.create(user.getUserId(), authenticatedAt, method);
        // 2. 登录时间在同一事务更新，提交成功后才返回会话和资料。
        users.updateLastLogin(user.getUserId());
        return new SessionVO("Bearer", token, 7200, view(users.selectById(user.getUserId())));
    }

    /**
     * 用户锁内用资料、状态和升序的全部角色 ID 算整体版本，密码、审计时间和最近登录时间都不算进去。
     * @param user 当前用户内部实体
     * @return 脱敏资料与带引号的 SHA-256 版本摘要
     */
    private UserProfileVO profile(SysUser user) {
        // 1. 字段和角色 ID 顺序固定后再算摘要，排除密码、审计时间和登录时间。
        String canonical = json.writeValueAsString(Arrays.asList(user.getUserId(), user.getUsername(),
                user.getNickname(), user.getEmail(), user.getAvatar(), user.getIntro(), user.getStatus(),
                authorization.roleIds(user.getUserId())));
        // 2. 版本要保留引号，客户端用 If-Revision 原样带回来。
        return new UserProfileVO(view(user), "\"" + TokenUtils.digest(canonical) + "\"");
    }

    /**
     * 从内部用户实体里只取本人资料，不带密码哈希等敏感字段。
     * @param user 已查询到的内部用户实体
     * @return 可对外返回的本人资料
     */
    private MeVO view(SysUser user) {
        return new MeVO(user.getUserId(), user.getUsername(), user.getNickname(), user.getEmail(),
                user.getAvatar(), user.getIntro(), user.getStatus(), user.getPasswordHash() != null,
                user.getLastLoginTime(), user.getCreateTime(), user.getUpdateTime());
    }
}
