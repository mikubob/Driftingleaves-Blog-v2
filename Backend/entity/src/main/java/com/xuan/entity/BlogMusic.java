package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 全站展示资料，启停与账号身份无关，时间由数据库维护。
 */
@Data
@TableName("blog_music")
public class BlogMusic {

    @TableId(type = IdType.AUTO)
    private Long musicId;
    private String title;
    private String artist;
    private BigDecimal durationSeconds;
    private String coverUrl;
    private String audioUrl;
    private String lyricUrl;
    private String lyricType;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
