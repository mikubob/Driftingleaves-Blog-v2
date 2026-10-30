package com.xuan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 这里只接收文章状态，不接收客户端传来的发布时间和回收时间。
 */
public record ArticleStateDTO(@NotNull @Min(1) @Max(2) Integer status) {

}
