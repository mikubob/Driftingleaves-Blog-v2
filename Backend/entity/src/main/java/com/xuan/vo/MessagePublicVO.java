package com.xuan.vo;

import java.time.LocalDateTime;

/**
 * 公开响应只在完整父链可见时返回，删除节点不含作者或原文。
 */
public record MessagePublicVO(Long messageId, Long parentId, ArticlePublicVO.Author author, String content,
                              String htmlContent, Integer contentFormat, Integer isSecret, Integer status,
                              LocalDateTime editedAt, boolean isOwn, LocalDateTime createTime,
                              LocalDateTime updateTime, String location, String userAgentOs,
                              String userAgentBrowser) {

}
