package com.xuan.utils;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.CircleCaptcha;
import cn.hutool.captcha.generator.RandomGenerator;

/** 只生成图片和答案，存储与 HTTP 响应由调用方负责。 */
public final class CaptchaUtils {

    private CaptchaUtils() {
    }

    /** 固定六位数字并保留前导零，使用圆圈干扰。 */
    public static CircleCaptcha create() {
        return CaptchaUtil.createCircleCaptcha(200, 80, new RandomGenerator("0123456789", 6), 20);
    }
}
