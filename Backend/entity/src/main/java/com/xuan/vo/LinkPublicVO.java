package com.xuan.vo;

/** 公开友链只含展示字段，状态和内部维护时间不返回。 */
public record LinkPublicVO(Long linkId, String linkName, String url, String avatar,
                           String description, Integer sortOrder) { }
