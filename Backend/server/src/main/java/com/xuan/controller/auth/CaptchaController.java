package com.xuan.controller.auth;

import com.xuan.annotation.RateLimit;
import com.xuan.result.Result;
import com.xuan.store.CaptchaStore;
import com.xuan.utils.CaptchaUtils;
import com.xuan.vo.ArithmeticCaptchaVO;
import com.xuan.vo.CaptchaVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;

/** 登录图形验证码和评论留言算术题接口，不创建登录会话或浏览器 Cookie。 */
@RestController
@RequiredArgsConstructor
public class CaptchaController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final CaptchaStore captchas;

    /** 生成图形验证码，先按可信 IP 限流，只返回随机id、图片和有效秒数。 */
    @GetMapping({"/admin/auth/captcha", "/blog/auth/captcha"})
    @RateLimit(value = "captcha", limit = 30)
    public ResponseEntity<Result<CaptchaVO>> create() {
        var captcha = CaptchaUtils.create();
        String image = captcha.getImageBase64Data();
        String captchaId = captchas.issue(captcha.getCode());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Result.ok(new CaptchaVO(captchaId, image, CaptchaStore.EXPIRES_IN)));
    }

    /** 生成一至九的加减乘题，减法不产生负数，答案只存 Redis。 */
    @GetMapping("/blog/auth/math-captcha")
    @RateLimit(value = "captcha", limit = 30)
    public ResponseEntity<Result<ArithmeticCaptchaVO>> arithmetic() {
        int left = RANDOM.nextInt(9) + 1;
        int right = RANDOM.nextInt(9) + 1;
        int operation = RANDOM.nextInt(3);
        if (operation == 1 && left < right) {
            int previous = left;
            left = right;
            right = previous;
        }
        String operator;
        int answer;
        switch (operation) {
            case 0 -> {
                operator = "+";
                answer = left + right;
            }
            case 1 -> {
                operator = "-";
                answer = left - right;
            }
            default -> {
                operator = "×";
                answer = left * right;
            }
        }
        String question = left + " " + operator + " " + right + " = ?";
        String captchaId = captchas.issueArithmetic(answer);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Result.ok(new ArithmeticCaptchaVO(captchaId, question, CaptchaStore.EXPIRES_IN)));
    }
}
