package com.xuan.location;

import com.xuan.exception.BusinessException;
import com.xuan.utils.IpUtils;
import org.lionsoul.ip2region.service.Ip2Region;
import org.lionsoul.ip2region.xdb.InetAddressException;

import java.io.IOException;

/**
 * IP 地域在本地查，IPv4 和 IPv6 查询器只创建一份，不查 DNS 或远程定位服务。
 */
public class IpLocationResolver implements AutoCloseable {

    private static final GeoInfo UNKNOWN = new GeoInfo(null, null, null);
    private final Ip2Region database;

    /** 数据库由应用先加载并检查，关闭解析器时一起释放。 */
    public IpLocationResolver(Ip2Region database) {
        this.database = database;
    }

    /**
     * 查国家、省份和城市，查不到的字段就留 null，不编造地址。
     *
     * @param ip IPv4 或 IPv6 字面量；请求 IP 应先由 IpUtils 按可信代理规则提取
     * @return 无效、回环或未命中时字段为 null；库内 0/空字段同样归一为 null
     * @throws BusinessException 数据库查询故障或线程中断时返回 503，不当作普通未知地址
     */
    public GeoInfo getGeoInfo(String ip) {
        return lookup(IpUtils.normalize(ip));
    }

    /**
     * 省市能查到就显示省市，否则显示国家；回环显示本地，没有结果显示未知。
     *
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

    /** 把整理好的 IP 交给官方查询器，它支持并发；这里只取国家、省、市。 */
    private GeoInfo lookup(String ip) {
        if (ip == null || isLoopback(ip)) {
            return UNKNOWN;
        }
        try {
            String region = database.search(ip);
            if (region == null || region.isBlank()) {
                return UNKNOWN;
            }

            // 3.3. x 库依次是国家、省份、城市、ISP、国家代码，不能照搬 2.x 的字段位置。
            String[] parts = region.split("\\|", -1);
            return new GeoInfo(part(parts, 0), part(parts, 1), part(parts, 2));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "IP地域查询被中断");
        } catch (IOException | InetAddressException exception) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "IP地域查询暂不可用");
        }
    }

    /** IPv4 和 IPv6 回环地址都显示本地，不再查库。 */
    private boolean isLoopback(String ip) {
        return ip != null && (ip.startsWith("127.") || "0:0:0:0:0:0:0:1".equals(ip));
    }

    /** 库里的 0 表示没查到，返回时换成 null。 */
    private String part(String[] parts, int index) {
        if (index >= parts.length) {
            return null;
        }
        String value = parts[index].strip();
        return value.isEmpty() || "0".equals(value) ? null : value;
    }

    /** 查询器共用到应用退出，由 Spring 关闭，不要查一次就关一次。 */
    @Override
    public void close() throws InterruptedException {
        database.close();
    }

    /**
     * 地域结果不依赖业务实体，未知字段填 null。
     */
    public record GeoInfo(String country, String province, String city) {

    }
}
