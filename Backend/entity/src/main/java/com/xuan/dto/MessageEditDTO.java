package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

/**
 * 只收声明字段，私密性和父关系不能通过编辑改写。
 */
@Getter
public class MessageEditDTO {

    @NotNull
    @TextLength(min = 1, max = 2000)
    private String content;

    @NotNull
    @Min(0)
    @Max(1)
    private Integer contentFormat;

    @NotNull
    @Min(0)
    @Max(1)
    private Integer notifyReply;

    @Getter(AccessLevel.NONE)
    private final Set<String> present = new HashSet<>();

    public void setContent(String value) {
        present.add("content");
        content = value == null ? null : value.strip();
    }

    public void setContentFormat(Integer value) {
        present.add("contentFormat");
        contentFormat = value;
    }

    public void setNotifyReply(Integer value) {
        present.add("notifyReply");
        notifyReply = value;
    }

    /** 可空parentId也必须显式出现。 */
    @AssertTrue(message = "须提供全部声明字段")
    public boolean isComplete() {
        return present.size() == 3;
    }
}
