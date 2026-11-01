package com.xuan.utils;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * 只解析 IP 地址，从右往左跳过明确配置的可信代理，不查主机名。
 */
public final class IpUtils {

    private IpUtils() {
    }

    /**
     * 取得规范的客户端 IP，不查 DNS；转发链有问题就用直连地址。
     *
     * @param request 当前请求，容器的自动转发头处理必须关闭
     * @param trustedProxies 受信代理的精确 IPv4/IPv6 地址，不接受网段和主机名
     * @return 客户端地址；容器未提供合法地址时返回 null
     */
    public static String clientIp(HttpServletRequest request, List<String> trustedProxies) {
        String remote = normalize(request.getRemoteAddr());
        if (remote == null || !trusted(remote, trustedProxies)) {
            return remote;
        }
        String header = request.getHeader("X-Forwarded-For");
        if (StrUtil.nullToEmpty(header).isBlank()) {
            return remote;
        }
        String current = remote;
        String[] chain = header.split(",", -1);
        for (int index = chain.length - 1; index >= 0 && trusted(current, trustedProxies); index--) {
            current = normalize(chain[index].strip());
            if (current == null) {
                return remote;
            }
        }
        return current;
    }

    /** 访问统计只保留IPv4 /24或IPv6 /48网段，不把完整客户端IP落库。 */
    public static String anonymize(String value) {
        String ip = normalize(value);
        if (ip == null) {
            return null;
        }
        try {
            byte[] bytes = InetAddress.getByName(ip).getAddress();
            Arrays.fill(bytes, bytes.length == 4 ? 3 : 6, bytes.length, (byte) 0);
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException error) {
            return null;
        }
    }

    /** 先把地址整理成统一写法再比较，避免同一个 IPv6 地址写法不同而漏掉检查。 */
    private static boolean trusted(String ip, List<String> proxies) {
        return proxies.stream().anyMatch(proxy -> ip.equals(normalize(proxy)));
    }

    /** 只收完整 IP 地址，不接受端口、zone ID、主机名或 IPv4 缩写。 */
    public static String normalize(String value) {
        if (StrUtil.isEmpty(value) || value.length() > 45) {
            return null;
        }
        if (value.contains(":")) {
            if (!value.matches("[0-9a-fA-F:.]+")) {
                return null;
            }
            try {
                return InetAddress.getByName(value).getHostAddress();
            } catch (UnknownHostException exception) {
                return null;
            }
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        for (String part : parts) {
            if (!part.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(part) > 255) {
                return null;
            }
        }
        return value;
    }
}
