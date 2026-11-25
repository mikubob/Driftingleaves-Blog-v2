package com.xuan.service.impl;

import com.xuan.service.ICaptchaService;
import com.xuan.store.CaptchaStore;
import com.xuan.utils.CaptchaUtils;
import com.xuan.vo.ArithmeticCaptchaVO;
import com.xuan.vo.CaptchaVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * 验证码服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements ICaptchaService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CaptchaStore captchaStore;

    /**
     * 生成登录图形验证码，验证码答案只保存在 Redis 中。
     *
     * @return 图形验证码结果
     */
    @Override
    public CaptchaVO createCaptcha() {
        var captcha = CaptchaUtils.create();
        String image = captcha.getImageBase64Data();
        String captchaId = captchaStore.issue(captcha.getCode());
        return new CaptchaVO(captchaId, image, CaptchaStore.EXPIRES_IN);
    }

    /**
     * 生成一至九的加减乘算术验证码，减法不产生负数，答案只保存在 Redis 中。
     *
     * @return 算术验证码结果
     */
    @Override
    public ArithmeticCaptchaVO createArithmeticCaptcha() {
        int left = RANDOM.nextInt(9) + 1;
        int right = RANDOM.nextInt(9) + 1;
        int operation = RANDOM.nextInt(3);

        // 减法保证结果为非负数。
        if (operation == 1 && left < right) {
            int previous = left;
            left = right;
            right = previous;
        }

        String operator;
        int answer;
        if (operation == 0) {
            operator = "+";
            answer = left + right;
        } else if (operation == 1) {
            operator = "-";
            answer = left - right;
        } else {
            operator = "×";
            answer = left * right;
        }

        String question = left + " " + operator + " " + right + " = ?";
        String captchaId = captchaStore.issueArithmetic(answer);
        return new ArithmeticCaptchaVO(captchaId, question, CaptchaStore.EXPIRES_IN);
    }
}
