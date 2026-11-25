package com.xuan.model;

import com.xuan.vo.OAuthAuthorizeVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 第三方授权发起结果，包含授权地址信息及浏览器标识。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthAuthorizeSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 授权地址响应信息
    private OAuthAuthorizeVO authorize;

    // 浏览器标识
    private String browserId;
}
