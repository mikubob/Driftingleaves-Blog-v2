package com.xuan.service;

import com.xuan.vo.ArithmeticCaptchaVO;
import com.xuan.vo.CaptchaVO;

/**
 * 验证码服务接口
 */
public interface ICaptchaService {

    /**
     * 生成登录图形验证码
     *
     * @return 验证码标识、图片内容及有效期
     */
    CaptchaVO createCaptcha();

    /**
     * 生成评论和留言算术验证码
     *
     * @return 验证码标识、算术题目及有效期
     */
    ArithmeticCaptchaVO createArithmeticCaptcha();
}
