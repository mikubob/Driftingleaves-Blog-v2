package com.xuan.utils;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class CaptchaUtilsTest {

    @Test
    void generatesSixDigitsAndDecodablePng() throws Exception {
        var captcha = CaptchaUtils.create();
        assertThat(captcha.getCode()).matches("[0-9]{6}");
        assertThat(captcha.getImageBase64Data()).startsWith("data:image/png;base64,");
        var image = ImageIO.read(new ByteArrayInputStream(captcha.getImageBytes()));
        assertThat(image.getWidth()).isEqualTo(200);
        assertThat(image.getHeight()).isEqualTo(80);
        assertThat(captcha.verify(captcha.getCode())).isTrue();
        assertThat(captcha.verify("abcdef")).isFalse();
    }
}
