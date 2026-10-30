package com.xuan.location;

import com.xuan.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.lionsoul.ip2region.service.Ip2Region;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 对字段归一化、无效输入和确定性查询故障做小范围测试，真实 xdb 由配置测试覆盖。
 */
class IpLocationResolverTest {

    private final Ip2Region database = mock(Ip2Region.class);
    private final IpLocationResolver resolver = new IpLocationResolver(database);

    @Test
    void normalizesMissingFieldsAndUsesCountryWhenProvinceAndCityAreUnknown() throws Exception {
        when(database.search("8.8.8.8")).thenReturn(" Country |0||ISP|CC");
        assertThat(resolver.getGeoInfo("8.8.8.8")).isEqualTo(new IpLocationResolver.GeoInfo("Country", null, null));
        assertThat(resolver.getIpLocation("8.8.8.8")).isEqualTo("Country");
        when(database.search("8.8.8.8")).thenReturn("Country| Province |City|ISP|CC");
        assertThat(resolver.getIpLocation("8.8.8.8")).isEqualTo("Province City");
        when(database.search("8.8.8.8")).thenReturn("", (String) null, "0|0|0");
        assertThat(resolver.getIpLocation("8.8.8.8")).isEqualTo("未知");
        assertThat(resolver.getIpLocation("8.8.8.8")).isEqualTo("未知");
        assertThat(resolver.getGeoInfo("8.8.8.8")).isEqualTo(new IpLocationResolver.GeoInfo(null, null, null));
    }

    @Test
    void invalidInputsAndIpv4Ipv6LoopbacksNeverReachDatabase() {
        for (String ip : new String[]{null, "", "localhost", "999.1.1.1", "8.8.8.8:53", "8.8.8.8, 1.1.1.1"}) {
            assertThat(resolver.getIpLocation(ip)).isEqualTo("未知");
            assertThat(resolver.getGeoInfo(ip)).isEqualTo(new IpLocationResolver.GeoInfo(null, null, null));
        }
        for (String ip : new String[]{"127.0.0.1", "127.1.2.3", "::1", "0:0:0:0:0:0:0:1", "::ffff:127.0.0.1"}) {
            assertThat(resolver.getIpLocation(ip)).isEqualTo("本地");
            assertThat(resolver.getGeoInfo(ip)).isEqualTo(new IpLocationResolver.GeoInfo(null, null, null));
        }
        verifyNoInteractions(database);
    }

    @Test
    void reportsLookupFailureWithoutRawMessageAndPreservesInterruption() throws Exception {
        when(database.search("8.8.8.8")).thenThrow(new IOException("internal database path"),
                new InterruptedException());
        assertThatThrownBy(() -> resolver.getGeoInfo("8.8.8.8"))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(503);
                    assertThat(error.getMessage()).doesNotContain("internal database path");
                });
        try {
            assertThatThrownBy(() -> resolver.getIpLocation("8.8.8.8")).isInstanceOf(BusinessException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void closesItsOwnedQueryEngine() throws Exception {
        resolver.close();
        verify(database).close();
    }
}
