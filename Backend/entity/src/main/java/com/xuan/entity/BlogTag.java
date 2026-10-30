package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标签字典。
 */
@Data
@TableName("blog_tag")
public class BlogTag {

    /** 标签ID。 */
    @TableId(value = "tag_id", type = IdType.AUTO)
    private Long tagId;

    /** 标签名称。 */
    @TableField("tag_name")
    private String tagName;

    /** 标签URL别名，唯一且区分大小写。 */
    @TableField("slug")
    private String slug;

    /** 创建时间由数据库填写，应用不用赋值。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 修改记录时数据库自动更新时间，应用不用赋值。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
