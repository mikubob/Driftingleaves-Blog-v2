package com.xuan.vo;

import java.time.LocalDateTime;

/**
 * 公开留言数据，只在全部上级留言可见时返回。
 */
public record MessagePublicVO(Long messageId, Long parentId, ArticlePublicVO.Author author, String content,
                              String htmlContent, Integer contentFormat, Integer isSecret, Integer status,
                              LocalDateTime editedAt, boolean isOwn, LocalDateTime createTime,
                              LocalDateTime updateTime, String location, String userAgentOs,
                              String userAgentBrowser) {

}
