package com.xuan.vo;

import java.time.LocalDate;

/**
 * 公开响应只保留展示字段，不返回管理状态与原始Markdown。
 */
public record ExperiencePublicVO(Long experienceId, Integer experienceType, String title, String subtitle,
                                 String logoUrl, LocalDate startDate, LocalDate endDate, Integer sortOrder,
                                 String htmlContent) {

}
