package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 全站展示资料，启停与账号身份无关，时间由数据库维护。 */
@Data
@TableName("cv_skill")
public class CvSkill {
    @TableId(type = IdType.AUTO)
    private Long skillId;
    private String name;
    private String description;
    private String icon;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
