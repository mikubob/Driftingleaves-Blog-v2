package com.xuan;

import com.xuan.config.IpLocationConfig;
import com.xuan.location.IpLocationResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lionsoul.ip2region.xdb.XdbException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import com.xuan.utils.IpUtils;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 实际装配双协议 xdb 并离线查询，覆盖并发调用、代理取址及无效资源启动失败。 */
class IpLocationConfigTest {
    private static AnnotationConfigApplicationContext context;
    private static IpLocationResolver resolver;

    @BeforeAll
    static void loadBundledDatabases() {
        context = new AnnotationConfigApplicationContext(IpLocationConfig.class);
        resolver = context.getBean(IpLocationResolver.class);
    }

    @AfterAll
    static void closeContainer() {
        context.close();
        assertThat(context.isActive()).isFalse();
    }

    @Test
    void resolvesRealIpv4AndIpv6UsingBundledDatabaseAndTrustedClientIp() {
        assertThat(resolver.getGeoInfo("114.114.114.114"))
                .isEqualTo(new IpLocationResolver.GeoInfo("中国", "江苏省", "南京市"));
        assertThat(resolver.getIpLocation("114.114.114.114")).isEqualTo("江苏省 南京市");
        assertThat(resolver.getGeoInfo("240e:3b7:3272:d8d0:db09:c067:8d59:539e"))
                .isEqualTo(new IpLocationResolver.GeoInfo("中国", "广东省", "深圳市"));
        assertThat(resolver.getGeoInfo("2001:4860:4860::8888"))
                .isEqualTo(new IpLocationResolver.GeoInfo("United States", "Florida", "Miami"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.1");
        request.addHeader("X-Forwarded-For", "8.8.8.8, 114.114.114.114, 192.0.2.2");
        String ip = IpUtils.clientIp(request, List.of("192.0.2.1", "192.0.2.2"));
        assertThat(resolver.getIpLocation(ip)).isEqualTo("江苏省 南京市");
    }

    @Test
    void supportsConcurrentDualStackCallsOnOneSpringSingleton() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<String>> queries = IntStream.range(0, 32).mapToObj(index -> (Callable<String>) () ->
                    resolver.getIpLocation(index % 2 == 0 ? "114.114.114.114" : "240e:3b7:3272:d8d0:db09:c067:8d59:539e")).toList();
            var results = executor.invokeAll(queries);
            for (int index = 0; index < results.size(); index++) {
                assertThat(results.get(index).get()).isEqualTo(index % 2 == 0 ? "江苏省 南京市" : "广东省 深圳市");
            }
        }
    }

    @Test
    void failsLoadingMissingCorruptOrWrongProtocolDatabase() {
        var config = new IpLocationConfig();
        var v4 = new ClassPathResource("ip2region/ip2region_v4.xdb");
        var v6 = new ClassPathResource("ip2region/ip2region_v6.xdb");
        assertThatThrownBy(() -> config.ipLocationResolver(new ClassPathResource("ip2region/missing.xdb"), v6))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> config.ipLocationResolver(new ByteArrayResource(new byte[512]), v6))
                .isInstanceOf(XdbException.class);
        assertThatThrownBy(() -> config.ipLocationResolver(v6, v4)).isInstanceOf(XdbException.class);
    }
}
