package com.xuan.utils;

/** 设备识别工具，从请求头获取浏览器和系统，不公开原始 User-Agent。 */
public final class UserAgentUtils {

    private UserAgentUtils() {
    }

    /** 识别浏览器，先检查衍生浏览器，再检查 Chrome 和 Safari，避免把兼容标记认错。 */
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

    /** 识别操作系统，先检查手机系统，再检查兼容标记，无法识别时留空。 */
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
