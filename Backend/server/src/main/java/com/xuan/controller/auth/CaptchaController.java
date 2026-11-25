package com.xuan.controller.auth;

import com.xuan.annotation.RateLimit;
import com.xuan.result.Result;
import com.xuan.service.ICaptchaService;
import com.xuan.vo.ArithmeticCaptchaVO;
import com.xuan.vo.CaptchaVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验证码接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class CaptchaController {

    private final ICaptchaService captchaService;

    /**
     * 获取登录图形验证码
     *
     * @return 图形验证码结果
     */
    @GetMapping({"/admin/auth/captcha", "/blog/auth/captcha"})
    @RateLimit(value = "captcha", limit = 30)
    public ResponseEntity<Result<CaptchaVO>> create() {
        log.info("生成登录图形验证码");
        CaptchaVO captcha = captchaService.createCaptcha();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Result.ok(captcha));
    }

    /**
     * 获取评论和留言算术验证码
     *
     * @return 算术验证码结果
     */
    @GetMapping("/blog/auth/math-captcha")
    @RateLimit(value = "captcha", limit = 30)
    public ResponseEntity<Result<ArithmeticCaptchaVO>> arithmetic() {
        log.info("生成算术验证码");
        ArithmeticCaptchaVO captcha = captchaService.createArithmeticCaptcha();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Result.ok(captcha));
    }
}
