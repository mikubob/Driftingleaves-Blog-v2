package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.AccessLevel;
import java.util.Set;
import java.util.HashSet;

/** 只收声明字段，私密性和父关系不能通过编辑改写。 */
@Getter
public class MessageCreateDTO {

    private Long parentId;
    @NotNull @TextLength(min = 1, max = 2000)
    private String content;
    @NotNull @Min(0) @Max(1)
    private Integer contentFormat;
    @NotNull @Min(0) @Max(1)
    private Integer isSecret;
    @NotNull @Min(0) @Max(1)
    private Integer notifyReply;
    @Getter(AccessLevel.NONE)
    private final Set<String> present = new HashSet<>();
    public void setParentId(Long value) { present.add("parentId"); parentId = value; }
    public void setContent(String value) { present.add("content"); content = value == null ? null : value.strip(); }
    public void setContentFormat(Integer value) { present.add("contentFormat"); contentFormat = value; }
    public void setIsSecret(Integer value) { present.add("isSecret"); isSecret = value; }
    public void setNotifyReply(Integer value) { present.add("notifyReply"); notifyReply = value; }
    /** 可空parentId也必须显式出现。 */
    @AssertTrue(message = "须提供全部声明字段")
    public boolean isComplete() { return present.size() == 5; }
}
