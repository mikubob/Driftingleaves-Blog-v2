package com.xuan.utils;

/** 从请求头识别评论和留言展示所需的浏览器与系统，不把原始 User-Agent 公开。 */
public final class UserAgentUtils {

    private UserAgentUtils() {
    }

    /** 衍生浏览器必须先于 Chrome、Safari 判断，它们的请求头也包含这些标记。 */
    public static String browser(String agent) {
        if (agent == null || agent.isBlank()) {
            return null;
        }
        if (agent.contains("MicroMessenger/")) {
            return "微信";
        }
        if (agent.contains("Edg/") || agent.contains("EdgA/") || agent.contains("EdgiOS/")) {
            return "Edge";
        }
        if (agent.contains("OPR/") || agent.contains("Opera/")) {
            return "Opera";
        }
        if (agent.contains("SamsungBrowser/")) {
            return "Samsung Internet";
        }
        if (agent.contains("QQBrowser/") || agent.contains("MQQBrowser/")) {
            return "QQ浏览器";
        }
        if (agent.contains("Chrome/") || agent.contains("CriOS/")) {
            return "Chrome";
        }
        if (agent.contains("Firefox/") || agent.contains("FxiOS/")) {
            return "Firefox";
        }
        if (agent.contains("Safari/")) {
            return "Safari";
        }
        if (agent.contains("Trident/") || agent.contains("MSIE ")) {
            return "Internet Explorer";
        }
        return null;
    }

    /** 手机系统先于兼容标记判断，无法识别时留空，由展示端显示未知。 */
    public static String operatingSystem(String agent) {
        if (agent == null || agent.isBlank()) {
            return null;
        }
        if (agent.contains("Windows NT")) {
            return "Windows";
        }
        if (agent.contains("Android")) {
            return "Android";
        }
        if (agent.contains("iPhone") || agent.contains("iPad") || agent.contains("iPod")) {
            return "iOS";
        }
        if (agent.contains("CrOS")) {
            return "Chrome OS";
        }
        if (agent.contains("Macintosh") || agent.contains("Mac OS X")) {
            return "macOS";
        }
        if (agent.contains("Linux")) {
            return "Linux";
        }
        return null;
    }
}
