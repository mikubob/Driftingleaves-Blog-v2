package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新建和修改标签都只收名称、slug，两个字段都要填。
 */
@Data
@NoArgsConstructor
public class TagWriteDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 标签名称
    @NotNull
    @TextLength(min = 1, max = 64)
    private String tagName;

    // URL别名
    @NotNull
    @Pattern(regexp = "[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*", message = "只能使用字母、数字及分隔词语的单个连字符，不能包含空格或连续连字符")
    @TextLength(max = 100)
    private String slug;

    public void setTagName(String value) {
        tagName = value == null ? null : value.strip();
    }

    public void setSlug(String value) {
        slug = value == null ? null : value.strip();
    }
}
