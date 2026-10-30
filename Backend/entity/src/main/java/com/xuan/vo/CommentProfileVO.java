package com.xuan.vo;

/**
 * 版本给 Controller 写响应头，不混进评论 JSON。
 */
public record CommentProfileVO(CommentAdminVO comment, String revision) {

}
