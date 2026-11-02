package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 留言记录，创建后不可修改回复关系，删除时连同全部子回复一起删除。
 */
@Data
@TableName("blog_message")
public class BlogMessage {

    @TableId(type = IdType.AUTO)
    private Long messageId;
    private Long userId;
    private Long parentId;
    private String content;
    private String location;
    private String userAgentOs;
    private String userAgentBrowser;
    private Integer contentFormat;
    private Integer isSecret;
    private Integer notifyReply;
    private Integer status;
    private LocalDateTime editedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
