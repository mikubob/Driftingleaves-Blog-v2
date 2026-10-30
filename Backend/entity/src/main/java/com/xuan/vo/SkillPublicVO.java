package com.xuan.vo;


/** 公开响应只保留展示字段，不返回管理状态与原始Markdown。 */
public record SkillPublicVO(Long skillId, String name, String description, String icon, Integer sortOrder) { }
