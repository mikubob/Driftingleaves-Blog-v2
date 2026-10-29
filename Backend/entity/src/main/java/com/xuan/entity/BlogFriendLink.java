package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 友情链接；URL协议及长度由应用校验。 */
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

    /** 创建时间；由数据库默认值维护，应用不填充。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 最后更新时间，记录修改时自动更新；由数据库默认值维护，应用不填充。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
