package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.AccessLevel;
import lombok.Getter;
import java.util.HashSet;
import java.util.Set;

/** 友链创建可用默认值，PUT 要完整填写；URL 的公开资源规则由入口业务统一检查。 */
@Getter
public class LinkWriteDTO {
    /** 完整替换也执行公共字段校验。 */
    public interface Replace extends Default { }
    @NotNull @TextLength(min = 1, max = 100)
    private String linkName;
    @NotNull @TextLength(min = 1, max = 2048)
    private String url;
    @TextLength(max = 2048)
    private String avatar;
    @TextLength(max = 255)
    private String description;
    @NotNull
    private Integer sortOrder = 0;
    @NotNull @Min(0) @Max(2)
    private Integer status = 0;
    @Getter(AccessLevel.NONE)
    private final Set<String> present = new HashSet<>();

    public void setLinkName(String value) { linkName = value == null ? null : value.strip(); }
    public void setUrl(String value) { url = value == null ? null : value.strip(); }
    public void setAvatar(String value) { present.add("avatar"); avatar = clean(value); }
    public void setDescription(String value) { present.add("description"); description = clean(value); }
    public void setSortOrder(Integer value) { present.add("sortOrder"); sortOrder = value; }
    public void setStatus(Integer value) { present.add("status"); status = value; }

    /** 可空字段也得显式传入，防止漏传时把现有资料覆盖掉。 */
    @AssertTrue(groups = Replace.class, message = "PUT必须提供全部可写字段，可空字段可为null")
    public boolean isCompleteReplace() { return present.size() == 4; }

    /** 可空文本去掉首尾空白，空串按清空处理。 */
    private String clean(String value) {
        return value == null || value.strip().isEmpty() ? null : value.strip();
    }
}
