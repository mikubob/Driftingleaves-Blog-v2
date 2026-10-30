package com.xuan.location;

import com.xuan.exception.BusinessException;
import com.xuan.utils.IpUtils;
import org.lionsoul.ip2region.service.Ip2Region;
import org.lionsoul.ip2region.xdb.InetAddressException;
import java.io.IOException;

/** 离线 IP 地域解析器；复用单例双协议查询器，不进行 DNS 或远程定位请求。 */
public class IpLocationResolver implements AutoCloseable {
    private static final GeoInfo UNKNOWN = new GeoInfo(null, null, null);
    private final Ip2Region database;

    /** 接收由应用加载并验证的数据库，生命周期随解析器关闭。 */
    public IpLocationResolver(Ip2Region database) {
        this.database = database;
    }

    /**
     * 查询国家、省份和城市，沿用 V2 可空字段约定，不把未知信息伪造成有效地址。
     * @param ip IPv4 或 IPv6 字面量；请求 IP 应先由 IpUtils 按可信代理规则提取
     * @return 无效、回环或未命中时字段为 null；库内 0/空字段同样归一为 null
     * @throws BusinessException 数据库查询故障或线程中断时返回 503，不当作普通未知地址
     */
    public GeoInfo getGeoInfo(String ip) {
        return lookup(IpUtils.normalize(ip));
    }

    /**
     * 取得可展示的省市地址；无省市时使用国家，回环地址显示本地，无有效结果显示未知。
     * @param ip IPv4 或 IPv6 字面量，不接受主机名、端口或转发头列表
     * @return 省份与城市以一个空格连接，或国家、本地、未知
     */
    public String getIpLocation(String ip) {
        String normalized = IpUtils.normalize(ip);
        if (isLoopback(normalized)) {
            return "本地";
        }
        GeoInfo info = lookup(normalized);
        String area = ((info.province() == null ? "" : info.province()) + " "
                + (info.city() == null ? "" : info.city())).strip();
        if (!area.isEmpty()) {
            return area;
        }
        return info.country() == null ? "未知" : info.country();
    }

    /** 将已规范化的地址交给官方并发安全查询器，仅解析国家、省、市三个已知字段。 */
    private GeoInfo lookup(String ip) {
        if (ip == null || isLoopback(ip)) {
            return UNKNOWN;
        }
        try {
            String region = database.search(ip);
            if (region == null || region.isBlank()) {
                return UNKNOWN;
            }
            // 3.x 配套库格式为国家|省份|城市|ISP|国家代码，不套用旧 2.x 的字段位置。
            String[] parts = region.split("\\|", -1);
            return new GeoInfo(part(parts, 0), part(parts, 1), part(parts, 2));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "IP地域查询被中断");
        } catch (IOException | InetAddressException exception) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "IP地域查询暂不可用");
        }
    }

    /** 对规范化后的 IPv4/IPv6 回环地址采用统一的本地展示，不查询数据库。 */
    private boolean isLoopback(String ip) {
        return ip != null && (ip.startsWith("127.") || "0:0:0:0:0:0:0:1".equals(ip));
    }

    /** 地域库使用 0 表示缺失信息，边界转换为 V2 的 null。 */
    private String part(String[] parts, int index) {
        if (index >= parts.length) {
            return null;
        }
        String value = parts[index].strip();
        return value.isEmpty() || "0".equals(value) ? null : value;
    }

    /** 由 Spring 容器关闭共享查询器，不在每次查询后关闭。 */
    @Override
    public void close() throws InterruptedException {
        database.close();
    }

    /** 共享地域结果，不依赖业务实体，未知字段使用 null。 */
    public record GeoInfo(String country, String province, String city) { }
}
