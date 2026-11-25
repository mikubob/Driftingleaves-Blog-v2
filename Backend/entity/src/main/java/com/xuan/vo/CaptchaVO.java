package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 图片以 PNG Data URI 返回，答案不能出现在响应中。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 验证码ID
    private String captchaId;

    // 验证码图片Base64
    private String imageBase64;

    // 过期时间（秒）
    private int expiresIn;
}
