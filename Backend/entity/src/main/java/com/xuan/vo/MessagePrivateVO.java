package com.xuan.vo;

import java.time.LocalDateTime;

/** 本人和ALL后台读取时额外返回通知意愿，不能据此认定邮箱已验证。 */
public record MessagePrivateVO(Long messageId, Long parentId, ArticlePublicVO.Author author, String content, String htmlContent, Integer contentFormat, Integer isSecret, Integer status, LocalDateTime editedAt, boolean isOwn, LocalDateTime createTime, LocalDateTime updateTime, Integer notifyReply) { }
