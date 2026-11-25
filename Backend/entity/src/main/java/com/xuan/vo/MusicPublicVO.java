package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 公开响应只保留展示字段，不返回管理状态与原始Markdown。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MusicPublicVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 音乐ID
    private Long musicId;

    // 标题
    private String title;

    // 歌手
    private String artist;

    // 时长（秒）
    private BigDecimal durationSeconds;

    // 封面图URL
    private String coverUrl;

    // 音频URL
    private String audioUrl;

    // 歌词URL
    private String lyricUrl;

    // 歌词类型
    private String lyricType;

    // 排序权重
    private Integer sortOrder;
}
