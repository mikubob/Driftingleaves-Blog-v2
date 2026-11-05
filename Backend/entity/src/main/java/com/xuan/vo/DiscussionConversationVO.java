package com.xuan.vo;

import java.util.List;

/** 返回主记录和当前页回复，回复总数不包含主记录。 */
public record DiscussionConversationVO<T>(T root, int page, int pageSize, String total,
                                           String totalPages, List<T> records) {
}
