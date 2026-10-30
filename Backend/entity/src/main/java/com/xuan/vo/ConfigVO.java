package com.xuan.vo;

import java.time.LocalDateTime;
import java.util.Map;

/** 配置值返回 JSON 对象，不把数据库里的 JSON 文本套成字符串。 */
public record ConfigVO(String configKey, Map<String, Object> configValue, String description,
                       Integer isPublic, LocalDateTime updateTime) { }
