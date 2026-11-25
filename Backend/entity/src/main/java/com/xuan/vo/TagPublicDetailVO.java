package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 标签详情只统计当前公开文章。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagPublicDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 标签ID
    private Long tagId;

    // 标签名称
    private String tagName;

    // URL别名
    private String slug;

    // 文章数
    private String articleCount;
}
