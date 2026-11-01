package com.xuan.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 检查浏览器兼容标记的优先级，避免 Edge 和手机系统被识别成 Chrome 或 macOS。 */
class UserAgentUtilsTest {

    @Test
    void identifiesDesktopAndMobileBrowsersBeforeCompatibilityTokens() {
        assertThat(UserAgentUtils.browser("Windows NT 10.0 Chrome/130.0 Safari/537.36 Edg/130.0"))
                .isEqualTo("Edge");
        assertThat(UserAgentUtils.browser("Android Chrome/130.0 Safari/537.36 SamsungBrowser/27.0"))
                .isEqualTo("Samsung Internet");
        assertThat(UserAgentUtils.browser("Android Chrome/130.0 MicroMessenger/8.0"))
                .isEqualTo("微信");
        assertThat(UserAgentUtils.browser("Chrome/130.0 Safari/537.36 OPR/115.0")).isEqualTo("Opera");
        assertThat(UserAgentUtils.browser("iPhone CriOS/130.0 Safari/604.1")).isEqualTo("Chrome");
        assertThat(UserAgentUtils.browser("iPhone FxiOS/130.0 Safari/604.1")).isEqualTo("Firefox");
        assertThat(UserAgentUtils.browser("Macintosh Version/18.0 Safari/605.1")).isEqualTo("Safari");
        assertThat(UserAgentUtils.browser("Firefox/130.0")).isEqualTo("Firefox");
        assertThat(UserAgentUtils.browser("Chrome/130.0 Safari/537.36")).isEqualTo("Chrome");
        assertThat(UserAgentUtils.browser(null)).isNull();
        assertThat(UserAgentUtils.browser("unknown-client")).isNull();
    }

    @Test
    void identifiesSystemsAndLeavesUnknownValuesEmpty() {
        assertThat(UserAgentUtils.operatingSystem("Windows NT 10.0")).isEqualTo("Windows");
        assertThat(UserAgentUtils.operatingSystem("Linux; Android 14")).isEqualTo("Android");
        assertThat(UserAgentUtils.operatingSystem("iPhone; CPU iPhone OS 18_0 like Mac OS X"))
                .isEqualTo("iOS");
        assertThat(UserAgentUtils.operatingSystem("Macintosh; Intel Mac OS X 10_15_7")).isEqualTo("macOS");
        assertThat(UserAgentUtils.operatingSystem("X11; Linux x86_64")).isEqualTo("Linux");
        assertThat(UserAgentUtils.operatingSystem("CrOS x86_64")).isEqualTo("Chrome OS");
        assertThat(UserAgentUtils.operatingSystem(null)).isNull();
        assertThat(UserAgentUtils.operatingSystem("unknown-client")).isNull();
    }
}
