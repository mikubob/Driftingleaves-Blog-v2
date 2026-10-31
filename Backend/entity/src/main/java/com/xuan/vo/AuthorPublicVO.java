package com.xuan.vo;

import java.time.LocalDateTime;

/**
 * 作者发现页只返回公开资料和当前公开文章的汇总。
 */
public record AuthorPublicVO(Long userId, String nickname, String avatar, String intro, String articleCount,
                             LocalDateTime lastPublishTime) {

}
