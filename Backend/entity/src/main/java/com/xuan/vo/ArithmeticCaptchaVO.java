package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 返回算术题和随机标识，答案只保存在服务端。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArithmeticCaptchaVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 验证码ID
    private String captchaId;

    // 算术验证码题目
    private String question;

    // 过期时间（秒）
    private int expiresIn;
}
