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
public class SkillPublicVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 技能ID
    private Long skillId;

    // 名称
    private String name;

    // 描述
    private String description;

    // 图标
    private String icon;

    // 排序权重
    private Integer sortOrder;
}
