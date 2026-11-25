package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 全站展示资料，启停与账号身份无关，时间由数据库维护。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("blog_music")
public class BlogMusic implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 音乐ID
    @TableId(type = IdType.AUTO)
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

    // 状态
    private Integer status;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;
}
