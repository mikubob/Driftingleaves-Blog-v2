package com.xuan.vo;

import com.xuan.entity.BlogTag;

/**
 * 标签资料放响应体，版本单独放到 X-Revision 响应头。
 */
public record TagProfileVO(BlogTag tag, String revision) {

}
