package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一篇文章只选一个分类；归档按发布时间查，不另建归档表。
 */
@Data
@TableName("blog_category")
public class BlogCategory {

    /** 分类ID。 */
    @TableId(value = "category_id", type = IdType.AUTO)
    private Long categoryId;

    /** 分类名称。 */
    @TableField("category_name")
    private String categoryName;

    /** 分类URL别名，唯一且区分大小写。 */
    @TableField("slug")
    private String slug;

    /** 分类说明。 */
    @TableField("description")
    private String description;

    /** 排序值，数值越小越靠前。 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 0隐藏 1展示；不改变文章发布状态。 */
    @TableField("status")
    private Integer status;

    /** 创建时间由数据库填写，应用不用赋值。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 修改记录时数据库自动更新时间，应用不用赋值。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
