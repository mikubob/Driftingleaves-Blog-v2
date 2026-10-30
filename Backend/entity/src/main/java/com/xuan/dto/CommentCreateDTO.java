package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.NotBlank;

/**
 * 评论按纯文本保存，文章取路径、作者取会话，不能让请求覆盖审核状态。
 */
public record CommentCreateDTO(@NotBlank @TextLength(min = 1, max = 2000) String content, Long parentId) {

    public CommentCreateDTO {
        content = content == null ? null : content.strip();
    }
}
