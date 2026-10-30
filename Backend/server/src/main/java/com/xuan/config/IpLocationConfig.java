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

/** 装配随应用打包的离线 IPv4/IPv6 库，启动时验证格式，关闭时释放查询器。 */
@Configuration(proxyBeanMethods = false)
public class IpLocationConfig {
    /**
     * 从资源流加载内存数据库，兼容可执行 jar，不把 classpath 资源强转磁盘文件。
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

    /** 3.3.7 对流资源协议不匹配的报错路径会引用空文件，先核对头部以保留明确的格式错误。 */
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
