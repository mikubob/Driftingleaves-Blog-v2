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
import java.time.LocalDateTime;

/**
 * 全站展示资料，启停与账号身份无关，时间由数据库维护。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("cv_skill")
public class CvSkill implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 技能ID
    @TableId(type = IdType.AUTO)
    private Long skillId;

    // 名称
    private String name;

    // 描述
    private String description;

    // 图标
    private String icon;

    // 排序权重
    private Integer sortOrder;

    // 状态
    private Integer status;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;
}
