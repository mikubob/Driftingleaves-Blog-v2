package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDate;

/** 全站展示资料，启停与账号身份无关，时间由数据库维护。 */
@Data
@TableName("cv_experience")
public class CvExperience {
    @TableId(type = IdType.AUTO)
    private Long experienceId;
    private Integer experienceType;
    private String title;
    private String subtitle;
    private String logoUrl;
    private String content;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
