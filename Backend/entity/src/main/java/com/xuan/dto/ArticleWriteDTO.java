package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 创建文章可以用默认值，完整修改时所有字段都要填；Markdown 原文不去空白。
 */
@Getter
public class ArticleWriteDTO {

    /**
     * 完整替换时，公共字段的校验也要执行。
     */
    public interface Replace extends Default {

    }

    @NotNull
    @TextLength(min = 1, max = 200)
    private String title;

    @NotNull
    @Size(max = 200)
    @Pattern(regexp = "[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*", message = "只能使用字母、数字及分隔词语的单个连字符，不能包含空格或连续连字符")
    private String slug;

    @NotNull
    @TextLength(max = 500)
    private String summary = "";
    private Long categoryId;

    @TextLength(max = 2048)
    private String coverUrl;

    @NotNull
    @Min(1)
    @Max(3)
    private Integer articleType = 1;

    @TextLength(max = 2048)
    private String originalUrl;

    @NotNull
    @Min(1)
    @Max(2)
    private Integer visibility = 1;

    @NotNull
    @Min(0)
    @Max(1)
    private Integer isTop = 0;

    @NotNull
    @Min(0)
    @Max(1)
    private Integer allowComment = 1;

    @NotNull
    private String markdownContent;

    @NotNull
    @Size(max = 20)
    private List<@NotNull Long> tagIds = List.of();

    @Getter(AccessLevel.NONE)
    private final Set<String> present = new HashSet<>();

    public void setTitle(String value) {
        title = value == null ? null : value.strip();
    }

    public void setSlug(String value) {
        slug = value == null ? null : value.strip();
    }

    public void setSummary(String value) {
        present.add("summary");
        summary = value == null ? null : value.strip();
    }

    public void setCategoryId(Long value) {
        present.add("categoryId");
        categoryId = value;
    }

    public void setCoverUrl(String value) {
        present.add("coverUrl");
        coverUrl = clean(value);
    }

    public void setArticleType(Integer value) {
        present.add("articleType");
        articleType = value;
    }

    public void setOriginalUrl(String value) {
        present.add("originalUrl");
        originalUrl = clean(value);
    }

    public void setVisibility(Integer value) {
        present.add("visibility");
        visibility = value;
    }

    public void setIsTop(Integer value) {
        present.add("isTop");
        isTop = value;
    }

    public void setAllowComment(Integer value) {
        present.add("allowComment");
        allowComment = value;
    }

    public void setMarkdownContent(String value) {
        markdownContent = value;
    }

    public void setTagIds(List<Long> value) {
        present.add("tagIds");
        tagIds = value;
    }

    /** PUT 的默认字段和可空字段也要传，避免漏传后悄悄覆盖原资料。 */
    @AssertTrue(groups = Replace.class, message = "表单内容未完整提交，请重新打开编辑窗口后重试。")
    public boolean isCompleteReplace() {
        return present.size() == 9;
    }

    /** 标签不能重复；没传和传 null 由字段上的校验处理。 */
    @AssertTrue(message = "同一标签不能重复选择。")
    public boolean isUniqueTags() {
        return tagIds == null || new HashSet<>(tagIds).size() == tagIds.size();
    }

    /** 去掉可空文本的首尾空白，空白内容按清空处理。 */
    private String clean(String value) {
        return value == null || value.strip().isEmpty() ? null : value.strip();
    }
}
