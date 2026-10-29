package com.xuan.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
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
import com.xuan.service.OAuthProviderGateway;
import com.xuan.service.SessionService;
import com.xuan.service.UrlPolicy;
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

/** 身份写入统一锁定 sys_user；Redis 撤销成功后数据库才能提交。 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, SysUser> implements IUserService {
    private final UserMapper users;
    private final OAuthBindingMapper oauth;
    private final AuthorizationMapper authorization;
    private final BCryptPasswordEncoder encoder;
    private final SessionService sessions;
    private final OAuthProviderGateway providers;
    private final PlatformTransactionManager transactionManager;
    private final JsonMapper json;
    private final Clock clock;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MeVO register(RegisterDTO request) {
        // 1. 仅写注册白名单字段，密码使用随机盐编码，账号状态由服务端固定。
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setNickname(request.nickname());
        user.setPasswordHash(encoder.encode(request.password()));
        user.setStatus(1);
        // 2. 同事务创建用户和 member 关联，初始化缺失时整笔回滚。
        createMember(user);
        // 3. 回查数据库生成的审计时间，以资料投影返回，不自动登录。
        return view(users.selectById(user.getUserId()));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SessionVO login(LoginDTO request) {
        // 1. 先锁定用户再验证凭据，避免旧密码校验跨过并发撤销点。
        SysUser user = users.lockByUsername(request.username());
        // 2. 不存在、无密码和密码错误统一失败，避免暴露账号类型。
        if (user == null || user.getPasswordHash() == null
                || !encoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(401, "AUTH_FAILED", "账号或密码错误");
        }
        // 3. 确认凭据后检查启用状态，再签发会话并更新登录时间。
        enabled(user);
        return createSession(user, clock.millis(), "PASSWORD");
    }

    @Override
    public long requireCurrent() {
        // 从本站会话取身份，并实时检查账号状态，不信任登录时快照。
        long userId = sessions.currentUserId();
        enabled(users.selectById(userId));
        return userId;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserProfileVO me() {
        return profile(lockCurrent());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserProfileVO patch(ProfilePatchDTO request, String revision) {
        // 1. 先确认本人身份并取得用户行锁，再读取本次写入所用的版本。
        SysUser user = lockCurrent();
        // 2. 区分缺失、格式错误和版本过期，防止资料被静默覆盖。
        if (revision == null) {
            throw new BusinessException(428, "PRECONDITION_REQUIRED", "缺少If-Revision");
        }
        if (!revision.matches("\"[0-9a-f]{64}\"")) {
            throw new BusinessException(400, "VALIDATION_ERROR", "If-Revision格式错误");
        }
        if (!revision.equals(profile(user).revision())) {
            throw new BusinessException(412, "REVISION_CONFLICT", "资料已修改，请重新获取");
        }
        // 3. 只更新明确出现的白名单字段；缺失保持，null 清空可空字段。
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
        // 4. 保存后回查审计时间，并返回对应新资料的聚合版本。
        users.updateProfile(user);
        return profile(users.selectById(user.getUserId()));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void setPassword(PasswordSetDTO request) {
        // 1. 锁定本人并验证 Token 级最近认证；首设密码必须来自 OAuth 登录。
        SysUser user = lockCurrent();
        sessions.requireRecent(sessions.currentToken(), user.getUserId(), user.getPasswordHash() == null);
        // 2. 按是否已有密码分支校验，不能用省略旧密码绕过现有凭据。
        if (user.getPasswordHash() == null) {
            if (request.hasCurrentPassword()) {
                throw new BusinessException(400, "VALIDATION_ERROR", "无密码账号必须省略currentPassword");
            }
        } else {
            verifyPassword(user, request.getCurrentPassword());
        }
        // 3. 写入新哈希后撤销全部会话，Redis 失败将回滚数据库修改。
        users.updatePassword(user.getUserId(), encoder.encode(request.getNewPassword()));
        sessions.revokeAll(user.getUserId());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void clearPassword(PasswordClearDTO request) {
        // 1. 在同一用户锁内确认最近认证、现有密码及其正确性。
        SysUser user = lockCurrent();
        sessions.requireRecent(sessions.currentToken(), user.getUserId(), false);
        if (user.getPasswordHash() == null) {
            throw new BusinessException(409, "STATE_CONFLICT", "尚未设置密码");
        }
        verifyPassword(user, request.currentPassword());
        // 2. 只将当前配置可用的平台绑定计为替代方式，禁止清除最后登录方式。
        if (!hasAvailableBinding(user.getUserId(), null)) {
            throw new BusinessException(409, "LAST_LOGIN_METHOD", "至少保留一种可用登录方式");
        }
        // 3. 将密码真正清为 NULL，并在数据库提交前撤销全部本站会话。
        users.updatePassword(user.getUserId(), null);
        sessions.revokeAll(user.getUserId());
    }

    @Override
    public List<OAuthBindingVO> bindings() {
        return oauth.findByUser(requireCurrent()).stream()
                .map(binding -> new OAuthBindingVO(binding.getSource(), binding.getCreateTime())).toList();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void unbind(String source) {
        // 1. 检查平台可用性，再锁定本人并验证最近认证。
        providers.requireEnabled(source);
        SysUser user = lockCurrent();
        sessions.requireRecent(sessions.currentToken(), user.getUserId(), false);
        // 2. 在锁内确认绑定存在，并检查移除后是否仍有可用登录方式。
        if (oauth.findByUser(user.getUserId()).stream().noneMatch(binding -> source.equals(binding.getSource()))) {
            throw new BusinessException(404, "NOT_FOUND", "未绑定该平台");
        }
        if (user.getPasswordHash() == null && !hasAvailableBinding(user.getUserId(), source)) {
            throw new BusinessException(409, "LAST_LOGIN_METHOD", "至少保留一种可用登录方式");
        }
        // 3. 按用户和平台共同限定删除；撤销全部会话成功后才能提交。
        oauth.deleteBinding(user.getUserId(), source);
        sessions.revokeAll(user.getUserId());
    }

    @Override
    public long resolveOAuth(String source, String uuid, String nickname, String avatar) {
        // 1. 每次重试使用独立事务，唯一键冲突不能在原失败事务中继续处理。
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevelName("ISOLATION_READ_COMMITTED");
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return transaction.execute(status -> {
                    // 2. 仅按平台已验证的 source + uuid 定位身份，不按昵称或邮箱合并。
                    SysUserOauth existing = oauth.findIdentity(source, uuid);
                    if (existing != null) {
                        // 2.1 已有绑定先锁用户，再重查绑定归属，防止等待期间身份发生变化。
                        SysUser user = users.lockById(existing.getUserId());
                        enabled(user);
                        SysUserOauth current = oauth.findIdentity(source, uuid);
                        if (current == null || !current.getUserId().equals(user.getUserId())) {
                            throw new BusinessException(409, "STATE_CONFLICT", "绑定已变化，请重新发起");
                        }
                        return user.getUserId();
                    }
                    // 2.2 首次注册生成本站用户名，密码和邮箱保持 NULL，仅初始化展示资料。
                    SysUser user = new SysUser();
                    user.setUsername("oauth_" + SessionService.random().replace('-', '_'));
                    // 按 Unicode 字符截取昵称；无效头像丢弃，不能改变平台身份标识。
                    String display = nickname == null ? "" : nickname.strip();
                    user.setNickname(display.isEmpty() ? "用户" : display.substring(0,
                            display.offsetByCodePoints(0, Math.min(64, display.codePointCount(0, display.length())))));
                    user.setAvatar(UrlPolicy.valid(avatar) ? avatar : null);
                    user.setStatus(1);
                    // 2.3 用户、member 和第三方绑定共同提交，任一步失败均回滚。
                    createMember(user);
                    insertBinding(user.getUserId(), source, uuid);
                    return user.getUserId();
                });
            } catch (DuplicateKeyException exception) {
                // 3. 身份或随机用户名冲突必须先回滚整笔，再查询唯一赢家。
                if (attempt == 2) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("OAuth注册重试未结束");
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OAuthBindingVO bindOAuth(long userId, String tokenHash, String source, String uuid) {
        // 1. 锁定目标用户并重查原会话，平台回调不能自行切换本站身份。
        enabled(users.lockById(userId));
        sessions.originalToken(userId, tokenHash);
        // 2. 相同身份重复绑定可成功；同平台不同身份不得覆盖原绑定。
        List<SysUserOauth> bindings = oauth.findByUser(userId);
        for (SysUserOauth binding : bindings) {
            if (source.equals(binding.getSource())) {
                if (uuid.equals(binding.getUuid())) {
                    return new OAuthBindingVO(source, binding.getCreateTime());
                }
                throw new BusinessException(409, "OAUTH_BIND_CONFLICT", "该平台身份已被绑定");
            }
        }
        // 3. 由唯一键阻止身份转移，冲突转换为绑定失败并回滚事务。
        try {
            insertBinding(userId, source, uuid);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "OAUTH_BIND_CONFLICT", "该平台身份已被绑定");
        }
        // 4. 只返回平台和数据库生成的绑定时间，不暴露 uuid。
        return oauth.findByUser(userId).stream().filter(binding -> source.equals(binding.getSource()))
                .map(binding -> new OAuthBindingVO(source, binding.getCreateTime())).findFirst().orElseThrow();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SessionVO loginOAuth(long userId, String source, String uuid, long authenticatedAt) {
        // 1. 兑换时重新锁定用户并检查状态，不能仅信回调时的查询结果。
        SysUser user = users.lockById(userId);
        enabled(user);
        // 2. 重查原身份仍属于该用户，禁止待兑换票据恢复已解绑身份。
        SysUserOauth binding = oauth.findIdentity(source, uuid);
        if (binding == null || !binding.getUserId().equals(userId)) {
            throw new BusinessException(400, "OAUTH_STATE_INVALID", "授权身份已解绑，请重新发起");
        }
        // 3. 沿用平台验证成功时间，兑换等待不能刷新最近认证窗口。
        return createSession(user, authenticatedAt, "OAUTH");
    }

    private SysUser lockCurrent() {
        // 1. 从当前会话定位用户并取得行锁，不使用客户端目标 ID。
        long userId = sessions.currentUserId();
        SysUser user = users.lockById(userId);
        enabled(user);
        // 2. 等待行锁期间可能发生撤销，取得锁后必须重查 Token。
        sessions.requireActive(sessions.currentToken(), userId);
        return user;
    }

    private void createMember(SysUser user) {
        // 1. 保存用户并取得数据库自增 ID，供角色关联使用。
        users.insert(user);
        // 2. 仅分配启用的 member；初始化不完整时使整个注册事务失败。
        if (users.assignMember(user.getUserId()) != 1) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "member角色未正确初始化");
        }
    }

    private void insertBinding(long userId, String source, String uuid) {
        SysUserOauth binding = new SysUserOauth();
        binding.setUserId(userId);
        binding.setSource(source);
        binding.setUuid(uuid);
        oauth.insert(binding);
    }

    private boolean hasAvailableBinding(long userId, String excludingSource) {
        // 排除待解绑平台，并与当前配置可用的平台求交，不能只统计绑定行数。
        return oauth.findByUser(userId).stream().anyMatch(binding -> !binding.getSource().equals(excludingSource)
                && providers.enabledSources().contains(binding.getSource()));
    }

    private void verifyPassword(SysUser user, String password) {
        if (password == null) {
            throw new BusinessException(400, "VALIDATION_ERROR", "必须提供currentPassword");
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(401, "AUTH_FAILED", "账号或密码错误");
        }
    }

    private void enabled(SysUser user) {
        if (user == null) {
            throw new BusinessException(401, "UNAUTHENTICATED", "请重新登录");
        }
        if (user.getStatus() != 1) {
            throw new BusinessException(403, "ACCOUNT_DISABLED", "账号已禁用");
        }
    }

    private SessionVO createSession(SysUser user, long authenticatedAt, String method) {
        // 1. 在用户锁内签发独立 Token，并由会话服务登记事务失败清理。
        String token = sessions.create(user.getUserId(), authenticatedAt, method);
        // 2. 同事务更新登录时间，提交后才向客户端返回会话和资料。
        users.updateLastLogin(user.getUserId());
        return new SessionVO("Bearer", token, 7200, view(users.selectById(user.getUserId())));
    }

    private UserProfileVO profile(SysUser user) {
        // 1. 固定字段及角色 ID 顺序生成摘要，排除密码、审计时间和登录时间。
        String canonical = json.writeValueAsString(Arrays.asList(user.getUserId(), user.getUsername(),
                user.getNickname(), user.getEmail(), user.getAvatar(), user.getIntro(), user.getStatus(),
                authorization.roleIds(user.getUserId())));
        // 2. 将版本保留为带引号的具体值，供客户端原样用于 If-Revision。
        return new UserProfileVO(view(user), "\"" + SessionService.digest(canonical) + "\"");
    }

    private MeVO view(SysUser user) {
        return new MeVO(user.getUserId(), user.getUsername(), user.getNickname(), user.getEmail(),
                user.getAvatar(), user.getIntro(), user.getStatus(), user.getPasswordHash() != null,
                user.getLastLoginTime(), user.getCreateTime(), user.getUpdateTime());
    }
}
