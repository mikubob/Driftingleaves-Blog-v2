package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 配置值返回 JSON 对象，不把数据库里的 JSON 文本套成字符串。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 配置键
    private String configKey;

    // 配置值
    private Map<String, Object> configValue;

    // 描述
    private String description;

    // 是否公开
    private Integer isPublic;

    // 更新时间
    private LocalDateTime updateTime;
}
