package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 评论按纯文本保存，文章取路径、作者取会话，不能让请求覆盖审核状态。
 */
@Data
@NoArgsConstructor
public class CommentCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 内容
    @NotBlank
    @TextLength(min = 1, max = 2000)
    private String content;

    // 父级ID
    private Long parentId;

    public void setContent(String content) {
        this.content = content == null ? null : content.strip();
    }
}
