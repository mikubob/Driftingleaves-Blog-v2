package com.xuan.utils;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/** 客户端地址处理；只解析 IP 字面量，从右向左剥离精确配置的受信代理。 */
public final class IpUtils {
    private IpUtils() { }

    /**
     * 取得规范化客户端 IP，不进行 DNS 查询；无效转发链退回直连地址。
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
        if (header == null || header.isBlank()) {
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

    /** 规范化地址后比较，避免 IPv6 的等价写法绕开受信链判断。 */
    private static boolean trusted(String ip, List<String> proxies) {
        return proxies.stream().anyMatch(proxy -> ip.equals(normalize(proxy)));
    }

    /** 校验并规范化 IP 字面量，不接受端口、zone ID、主机名或缩写 IPv4。 */
    public static String normalize(String value) {
        if (value == null || value.isEmpty() || value.length() > 45) {
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
