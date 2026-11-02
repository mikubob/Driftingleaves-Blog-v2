package com.xuan.vo;

/** 返回算术题和随机标识，答案只保存在服务端。 */
public record ArithmeticCaptchaVO(String captchaId, String question, int expiresIn) {
}
