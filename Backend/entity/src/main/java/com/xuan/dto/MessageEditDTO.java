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
 * 提交后台留言回复，只接收正文、格式和回复通知设置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageEditDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 内容
    @NotNull
    @TextLength(min = 1, max = 2000)
    private String content;

    // 内容格式
    @NotNull
    @Min(0)
    @Max(1)
    private Integer contentFormat;

    // 是否开启回复通知
    @NotNull
    @Min(0)
    @Max(1)
    private Integer notifyReply;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
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

    /**
     * 检查回复字段是否完整。
     */
    @AssertTrue(message = "表单内容未完整提交，请刷新页面后重试。")
    public boolean isComplete() {
        return present.size() == 3;
    }
}
