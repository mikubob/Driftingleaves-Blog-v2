package com.xuan.vo;

/** 当前用户状态和同一次读取时的真实点赞明细数量。 */
public record LikeStateVO(boolean liked, Long likeCount) { }
