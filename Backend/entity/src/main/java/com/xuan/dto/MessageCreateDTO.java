package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * 只收声明字段，私密性和父关系不能通过编辑改写。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 父级ID
    private Long parentId;

    // 内容
    @NotNull
    @TextLength(min = 1, max = 2000)
    private String content;

    // 内容格式
    @NotNull
    @Min(0)
    @Max(1)
    private Integer contentFormat;

    // 是否悄悄话 0-否 1-是
    @NotNull
    @Min(0)
    @Max(1)
    private Integer isSecret;

    // 是否开启回复通知
    @NotNull
    @Min(0)
    @Max(1)
    private Integer notifyReply;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Set<String> present = new HashSet<>();

    public void setParentId(Long value) {
        present.add("parentId");
        parentId = value;
    }

    public void setContent(String value) {
        present.add("content");
        content = value == null ? null : value.strip();
    }

    public void setContentFormat(Integer value) {
        present.add("contentFormat");
        contentFormat = value;
    }

    public void setIsSecret(Integer value) {
        present.add("isSecret");
        isSecret = value;
    }

    public void setNotifyReply(Integer value) {
        present.add("notifyReply");
        notifyReply = value;
    }

    /**
     * 可空parentId也必须显式出现。
     */
    @AssertTrue(message = "表单内容未完整提交，请刷新页面后重试。")
    public boolean isComplete() {
        return present.size() == 5;
    }
}
