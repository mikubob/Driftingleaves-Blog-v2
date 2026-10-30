package com.xuan.utils;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** 真实地址解析测试，不使用 DNS 或外部地域数据库。 */
class IpUtilsTest {
    @Test
    void ignoresHeadersFromUntrustedPeerAndStopsAtFirstUntrustedHop() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.1");
        request.addHeader("X-Forwarded-For", "1.1.1.1, 198.51.100.9, 192.0.2.2");
        assertThat(IpUtils.clientIp(request, List.of())).isEqualTo("192.0.2.1");
        assertThat(IpUtils.clientIp(request, List.of("192.0.2.1", "192.0.2.2"))).isEqualTo("198.51.100.9");
    }

    @Test
    void normalizesIpv6AndRejectsMalformedOrAmbiguousHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("::1");
        request.addHeader("X-Forwarded-For", "2001:db8::1");
        assertThat(IpUtils.clientIp(request, List.of("0:0:0:0:0:0:0:1"))).isEqualTo("2001:db8:0:0:0:0:0:1");
        for (String invalid : List.of("localhost", "1.1.1.1:80", "999.1.1.1", "127.1", "01.1.1.1", "::1%eth0", "")) {
            assertThat(IpUtils.normalize(invalid)).as(invalid).isNull();
            request.removeHeader("X-Forwarded-For");
            request.addHeader("X-Forwarded-For", "198.51.100.1," + invalid);
            assertThat(IpUtils.clientIp(request, List.of("::1"))).isEqualTo("0:0:0:0:0:0:0:1");
        }
    }
}
