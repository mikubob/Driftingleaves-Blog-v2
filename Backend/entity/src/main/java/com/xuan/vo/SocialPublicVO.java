package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 公开响应只保留展示字段，不返回管理状态与原始Markdown。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialPublicVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 社交链接ID
    private Long socialId;

    // 名称
    private String name;

    // 图标
    private String icon;

    // 链接地址
    private String url;

    // 排序权重
    private Integer sortOrder;
}
