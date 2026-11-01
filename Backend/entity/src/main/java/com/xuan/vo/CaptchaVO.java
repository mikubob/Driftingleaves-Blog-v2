package com.xuan.vo;

/** 图片以 PNG Data URI 返回，答案不能出现在响应中。 */
public record CaptchaVO(String captchaId, String imageBase64, int expiresIn) {
}
