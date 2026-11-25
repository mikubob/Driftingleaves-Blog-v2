package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 配置详情及完整替换返回相同资料和业务版本。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigProfileVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 配置信息
    private ConfigVO config;

    // 资源版本号
    private String revision;
}
