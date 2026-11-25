package com.xuan.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 操作只能选枚举里的值，要绑定谁从原本站会话里取。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthAuthorizeDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 第三方授权操作类型
    @NotNull
    private Operation operation;

    /**
     * 区分第三方登录和绑定当前账号。
     */
    public enum Operation {
        LOGIN, BIND
    }
}
