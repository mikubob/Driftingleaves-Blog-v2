package com.xuan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站点地图按资源种类和ID分批扫描，不因为单文件上限截断公开URL。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SitemapRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 资源种类
    private Integer kind;

    // 资源ID
    private Long resourceId;

    // 资源别名
    private String slug;

    // 最后更新时间
    private LocalDateTime updatedAt;
}
