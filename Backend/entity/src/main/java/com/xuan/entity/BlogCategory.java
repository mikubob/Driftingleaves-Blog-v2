package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 文章单分类；归档按文章发布时间查询，不另建归档表。 */
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

    /** 创建时间；由数据库默认值维护，应用不填充。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 最后更新时间，记录修改时自动更新；由数据库默认值维护，应用不填充。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
