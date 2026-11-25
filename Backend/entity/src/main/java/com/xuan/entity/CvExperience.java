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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 全站展示资料，启停与账号身份无关，时间由数据库维护。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("cv_experience")
public class CvExperience implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 经历ID
    @TableId(type = IdType.AUTO)
    private Long experienceId;

    // 经历类型 0-工作 1-项目 2-教育
    private Integer experienceType;

    // 标题
    private String title;

    // 副标题
    private String subtitle;

    // 网站图标URL
    private String logoUrl;

    // 内容
    private String content;

    // 开始日期
    private LocalDate startDate;

    // 结束日期
    private LocalDate endDate;

    // 排序权重
    private Integer sortOrder;

    // 状态
    private Integer status;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;
}
