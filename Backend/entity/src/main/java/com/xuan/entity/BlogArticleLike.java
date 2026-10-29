package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 登录用户点赞，复合主键防重复；取消点赞物理删除；复合键为 article_id, user_id，不提供单 ID CRUD。 */
@Data
@TableName("blog_article_like")
public class BlogArticleLike {
    /** 被点赞的文章ID。 */
    @TableField("article_id")
    private Long articleId;

    /** 点赞用户ID。 */
    @TableField("user_id")
    private Long userId;

    /** 点赞时间；由数据库默认值维护，应用不填充。 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
