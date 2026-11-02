package com.xuan.policy;

import com.xuan.entity.SysUser;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.UserMapper;
import com.xuan.model.OnlineBinding;
import com.xuan.session.SessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 在线身份检查，离开 HTTP 请求后仍检查原会话和账号，不凭统计标识恢复登录。 */
@Component
@RequiredArgsConstructor
public class OnlineIdentityPolicy {
    private final SessionManager sessions;
    private final UserMapper users;

    /** 检查在线用户，重新查询原会话和账号启用状态，查询失败时不能改为访客继续。 */
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
