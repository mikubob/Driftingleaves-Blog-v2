package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 点赞用 article_id、user_id 两列一起确定一条记录，防止重复；取消就物理删除，不能用单个 ID 做增删改查。 */
@Data
@TableName("blog_article_like")
public class BlogArticleLike {
    /** 被点赞的文章ID。 */
    @TableField("article_id")
    private Long articleId;

    /** 点赞用户ID。 */
    @TableField("user_id")
    private Long userId;

    /** 点赞时间由数据库填写，应用不用赋值。 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
