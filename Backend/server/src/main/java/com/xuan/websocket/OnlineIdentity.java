package com.xuan.websocket;

import com.xuan.entity.SysUser;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.UserMapper;
import com.xuan.model.OnlineBinding;
import com.xuan.session.SessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 脱离 HTTP 请求后仍核验原会话与当前账号，不从指纹恢复身份。 */
@Component
@RequiredArgsConstructor
public class OnlineIdentity {
    private final SessionManager sessions;
    private final UserMapper users;

    /** 重查连接绑定的原会话和启用账号，查询失败不能降级成访客。 */
    public void requireActive(OnlineBinding binding) {
        if (binding.userId() == null) {
            return;
        }
        sessions.requireOriginalActive(binding.userId(), binding.tokenHash(), binding.expiresAt());
        SysUser user = users.selectById(binding.userId());
        if (user == null) {
            throw new BusinessException(401, "UNAUTHENTICATED", "请重新登录");
        }
        if (user.getStatus() != 1) {
            throw new BusinessException(403, "ACCOUNT_DISABLED", "账号已禁用");
        }
    }
}
