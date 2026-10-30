package com.xuan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 审核只能通过或驳回，不能改正文或复活占位评论。
 */
public record CommentReviewDTO(@NotNull @Min(1) @Max(2) Integer status) {

}
