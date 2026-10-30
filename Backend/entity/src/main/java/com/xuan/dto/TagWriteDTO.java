package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

/**
 * 新建和修改标签都只收名称、slug，两个字段都要填。
 */
@Getter
public class TagWriteDTO {

    @NotNull
    @TextLength(min = 1, max = 64)
    private String tagName;

    @NotNull
    @Pattern(regexp = "[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*")
    @TextLength(max = 100)
    private String slug;

    public void setTagName(String value) {
        tagName = value == null ? null : value.strip();
    }

    public void setSlug(String value) {
        slug = value == null ? null : value.strip();
    }
}
