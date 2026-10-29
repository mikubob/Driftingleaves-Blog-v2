package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 标签字典。 */
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

    /** 创建时间；由数据库默认值维护，应用不填充。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 最后更新时间，记录修改时自动更新；由数据库默认值维护，应用不填充。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
