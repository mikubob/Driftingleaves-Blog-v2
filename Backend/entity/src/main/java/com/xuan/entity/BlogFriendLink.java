package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 友链 URL 的协议和长度由应用检查。
 */
@Data
@TableName("blog_friend_link")
public class BlogFriendLink {

    /** 友链ID。 */
    @TableId(value = "link_id", type = IdType.AUTO)
    private Long linkId;

    /** 友情链接显示名称。 */
    @TableField("link_name")
    private String linkName;

    /** 友情链接目标URL。 */
    @TableField("url")
    private String url;

    /** 友链站点头像或图标URL。 */
    @TableField("avatar")
    private String avatar;

    /** 友链站点简介。 */
    @TableField("description")
    private String description;

    /** 排序值，数值越小越靠前。 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 0待审核 1展示 2驳回/下架。 */
    @TableField("status")
    private Integer status;

    /** 创建时间由数据库填写，应用不用赋值。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 修改记录时数据库自动更新时间，应用不用赋值。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
