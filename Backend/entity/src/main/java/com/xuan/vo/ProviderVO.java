package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 只列配置齐全的平台，不返回应用凭据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 来源
    private String source;

    // 展示名称
    private String displayName;
}
