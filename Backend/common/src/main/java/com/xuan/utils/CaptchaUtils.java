package com.xuan.utils;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.CircleCaptcha;
import cn.hutool.captcha.generator.RandomGenerator;

/** 验证码生成工具，保存答案和返回响应由调用方负责。 */
public final class CaptchaUtils {

    private CaptchaUtils() {
    }

    /** 生成六位数字验证码和干扰圆圈，保留开头的零。 */
    public static CircleCaptcha create() {
        return CaptchaUtil.createCircleCaptcha(200, 80, new RandomGenerator("0123456789", 6), 20);
    }
}
