package com.xuan.config;

import com.xuan.location.IpLocationResolver;
import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.InvalidConfigException;
import org.lionsoul.ip2region.service.Ip2Region;
import org.lionsoul.ip2region.xdb.XdbException;
import org.lionsoul.ip2region.xdb.Header;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Version;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import java.io.IOException;
import java.io.BufferedInputStream;

/** IPv4 和 IPv6 离线库随应用打包，启动时检查格式，退出时释放查询器。 */
@Configuration(proxyBeanMethods = false)
public class IpLocationConfig {
    /**
     * 从资源流加载数据库，这样打成可执行 jar 也能读取，不能硬转成磁盘文件。
     * @param ipv4Database 与 3.3.7 客户端匹配的 IPv4 xdb
     * @param ipv6Database 与 3.3.7 客户端匹配的 IPv6 xdb
     * @return 并发安全、由容器统一关闭的地域解析器
     * @throws IOException 资源缺失或读取失败，阻止不完整组件启动
     * @throws XdbException 数据库格式或协议版本不匹配
     * @throws InvalidConfigException 数据库加载配置无效
     */
    @Bean(destroyMethod = "close")
    public IpLocationResolver ipLocationResolver(
            @Value("classpath:ip2region/ip2region_v4.xdb") Resource ipv4Database,
            @Value("classpath:ip2region/ip2region_v6.xdb") Resource ipv6Database)
            throws IOException, XdbException, InvalidConfigException {
        try (var ipv4 = new BufferedInputStream(ipv4Database.getInputStream());
             var ipv6 = new BufferedInputStream(ipv6Database.getInputStream())) {
            verifyVersion(ipv4, Version.IPv4);
            verifyVersion(ipv6, Version.IPv6);
            Config v4 = Config.custom().setCachePolicy(Config.BufferCache).setXdbInputStream(ipv4).asV4();
            Config v6 = Config.custom().setCachePolicy(Config.BufferCache).setXdbInputStream(ipv6).asV6();
            return new IpLocationResolver(Ip2Region.create(v4, v6));
        }
    }

    /** 3.3.7 遇到流资源协议不符时会引用空文件，先查文件头，才能报出明确的格式错误。 */
    private void verifyVersion(BufferedInputStream input, Version expected) throws IOException, XdbException {
        input.mark(Searcher.HeaderInfoLength);
        byte[] bytes = input.readNBytes(Searcher.HeaderInfoLength);
        if (bytes.length != Searcher.HeaderInfoLength) {
            throw new XdbException("IP地域库头部不完整");
        }
        if (Version.fromHeader(new Header(bytes)).id != expected.id) {
            throw new XdbException("IP地域库与声明的协议版本不匹配");
        }
        input.reset();
    }
}
