package com.xuan.controller.auth;

import com.xuan.annotation.RateLimit;
import com.xuan.result.Result;
import com.xuan.store.CaptchaStore;
import com.xuan.utils.CaptchaUtils;
import com.xuan.vo.CaptchaVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 两端共用验证码生成能力，不创建登录会话或浏览器 Cookie。 */
@RestController
@RequestMapping({"/admin/auth/captcha", "/blog/auth/captcha"})
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaStore captchas;

    /** 生成前按可信 IP 限流，只返回随机标识、图片和有效秒数。 */
    @GetMapping
    @RateLimit(value = "captcha", limit = 30)
    public ResponseEntity<Result<CaptchaVO>> create() {
        var captcha = CaptchaUtils.create();
        String image = captcha.getImageBase64Data();
        String captchaId = captchas.issue(captcha.getCode());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Result.ok(new CaptchaVO(captchaId, image, CaptchaStore.EXPIRES_IN)));
    }
}
