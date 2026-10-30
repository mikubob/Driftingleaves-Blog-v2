package com.xuan.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站点地图按资源种类和ID分批扫描，不因为单文件上限截断公开URL。
 */
@Data
public class SitemapRow {

    private Integer kind;
    private Long resourceId;
    private String slug;
    private LocalDateTime updatedAt;
}
