package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * 创建和修改共用资料字段，可空字段用null清空；状态不随资料修改。
 */
@Getter
public class ExperienceWriteDTO {

    @NotNull
    @Min(0)
    @Max(2)
    private Integer experienceType;

    @NotNull
    @TextLength(min = 1, max = 200)
    private String title;

    @TextLength(min = 1, max = 200)
    private String subtitle;

    @TextLength(min = 1, max = 2048)
    private String logoUrl;

    @NotNull
    @TextLength(min = 1, max = 65535)
    private String content;

    @NotNull
    private LocalDate startDate;

    private LocalDate endDate;

    @NotNull
    private Integer sortOrder;

    @Getter(AccessLevel.NONE)
    private final Set<String> present = new HashSet<>();

    public void setExperienceType(Integer value) {
        present.add("experienceType");
        experienceType = value;
    }

    public void setTitle(String value) {
        present.add("title");
        title = value == null ? null : value.strip();
    }

    public void setSubtitle(String value) {
        present.add("subtitle");
        subtitle = value == null ? null : value.strip();
    }

    public void setLogoUrl(String value) {
        present.add("logoUrl");
        logoUrl = value == null ? null : value.strip();
    }

    public void setContent(String value) {
        present.add("content");
        content = value == null ? null : value.strip();
    }

    public void setStartDate(LocalDate value) {
        present.add("startDate");
        startDate = value;
    }

    public void setEndDate(LocalDate value) {
        present.add("endDate");
        endDate = value;
    }

    public void setSortOrder(Integer value) {
        present.add("sortOrder");
        sortOrder = value;
    }

    /** 创建和替换都必须提供可空字段，防止漏传被当作清空。 */
    @AssertTrue(message = "表单内容未完整提交，请重新打开编辑窗口后重试。")
    public boolean isComplete() {
        return present.size() == 8;
    }

    /** DATE 不能越过数据库范围，正文按 UTF-8 字节数限制。 */
    @AssertTrue(message = "经历日期须在1000至9999年之间，结束日期不能早于开始日期，正文不能超过65,535字节。")
    public boolean isValidContent() {
        return (startDate == null || startDate.getYear() >= 1000 && startDate.getYear() <= 9999)
                && (endDate == null || endDate.getYear() >= 1000 && endDate.getYear() <= 9999
                && (startDate == null || !endDate.isBefore(startDate)))
                && (content == null || content.getBytes(StandardCharsets.UTF_8).length <= 65535);
    }
}
