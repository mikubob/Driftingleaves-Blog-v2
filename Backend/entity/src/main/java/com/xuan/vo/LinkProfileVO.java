package com.xuan.vo;

import com.xuan.entity.BlogFriendLink;

/**
 * 友链版本交给 Controller 写响应头。
 */
public record LinkProfileVO<T extends BlogFriendLink>(T link, String revision) {

}
