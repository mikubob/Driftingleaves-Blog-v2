package com.xuan.vo;

import java.math.BigDecimal;

/**
 * 公开响应只保留展示字段，不返回管理状态与原始Markdown。
 */
public record MusicPublicVO(Long musicId, String title, String artist, BigDecimal durationSeconds, String coverUrl,
                            String audioUrl, String lyricUrl, String lyricType, Integer sortOrder) {

}
