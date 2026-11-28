package com.xuan.policy;

import com.xuan.entity.BlogSiteConfig;
import com.xuan.exception.BusinessException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * 评论与留言开关配置解析规则，只接受私有且不超过 16 KiB 的两项布尔策略。
 */
public final class DiscussionConfigPolicy {

    private static final int MAX_BYTES = 16 * 1024;

    private DiscussionConfigPolicy() {
    }

    /**
     * 解析并校验讨论开关配置，缺失或格式异常统一返回 503，不回显内部配置内容。
     *
     * @param config 已锁定的站点配置行
     * @param json JSON 解析器
     * @param unavailableMessage 不可用时的错误提示
     * @return 含 enabled 与 reviewRequired 两项布尔值的 JSON 节点
     */
    public static JsonNode parse(BlogSiteConfig config, JsonMapper json, String unavailableMessage) {
        if (config == null || config.getIsPublic() != 0 || config.getConfigValue() == null
                || config.getConfigValue().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", unavailableMessage);
        }
        try {
            JsonNode node = json.readTree(config.getConfigValue());
            if (node == null || !node.isObject() || node.size() != 2
                    || !node.path("enabled").isBoolean()
                    || !node.path("reviewRequired").isBoolean()) {
                throw new BusinessException(503, "SERVICE_UNAVAILABLE", unavailableMessage);
            }
            return node;
        } catch (JacksonException exception) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", unavailableMessage);
        }
    }
}
