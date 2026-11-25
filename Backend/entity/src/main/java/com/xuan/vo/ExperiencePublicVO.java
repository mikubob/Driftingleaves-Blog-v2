package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 公开响应只保留展示字段，不返回管理状态与原始Markdown。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperiencePublicVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 经历ID
    private Long experienceId;

    // 经历类型
    private Integer experienceType;

    // 标题
    private String title;

    // 副标题
    private String subtitle;

    // 网站图标URL
    private String logoUrl;

    // 开始日期
    private LocalDate startDate;

    // 结束日期
    private LocalDate endDate;

    // 排序权重
    private Integer sortOrder;

    // HTML内容
    private String htmlContent;
}
