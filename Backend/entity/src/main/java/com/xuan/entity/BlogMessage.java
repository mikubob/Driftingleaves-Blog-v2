package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 留言记录，创建后不可修改回复关系，删除时连同全部子回复一起删除。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TableName("blog_message")
public class BlogMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 留言ID
    @TableId(type = IdType.AUTO)
    private Long messageId;

    // 用户ID
    private Long userId;

    // 父级ID
    private Long parentId;

    // 所属会话根留言ID（根留言为 null）
    private Long rootId;

    // 内容
    private String content;

    // 属地信息
    private String location;

    // 操作系统
    private String userAgentOs;

    // 浏览器
    private String userAgentBrowser;

    // 内容格式
    private Integer contentFormat;

    // 是否悄悄话 0-否 1-是
    private Integer isSecret;

    // 是否开启回复通知
    private Integer notifyReply;

    // 状态
    private Integer status;

    // 编辑时间
    private LocalDateTime editedAt;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;
}
