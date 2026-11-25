package com.xuan.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 结果票据只能兑换一次，不能直接拿它当本站登录 Token。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthExchangeDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 一次性票据
    @NotNull
    @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "验证凭证无效，请重新发起验证")
    private String ticket;
}
