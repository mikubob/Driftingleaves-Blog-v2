package com.xuan.vo;

import com.xuan.entity.BlogCategory;

/** 分类资料放响应体，版本单独放到 X-Revision 响应头。 */
public record CategoryProfileVO(BlogCategory category, String revision) { }
