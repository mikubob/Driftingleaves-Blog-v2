package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * 采集只收统计字段，账号、IP、UA和时间由服务端确认。
 * 通过 setter 记录字段是否出现，可空字段也必须显式提交，不能只看值是否为 null。
 */
@Data
@NoArgsConstructor
public class PageViewCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 访客指纹标识
    @NotNull @Pattern(regexp = "[A-Za-z0-9-]{16,150}", message = "访客标识无效，请刷新页面后重试")
    private String fingerprint;

    // 站点编码
    @NotNull @Pattern(regexp = "home|blog|cv", message = "请选择主页、博客或简历站点")
    private String siteCode;

    // 页面路径
    @NotNull @TextLength(min = 1, max = 2048)
    private String pagePath;

    // 页面标题
    @TextLength(min = 1, max = 200)
    private String pageTitle;

    // 来源页面
    @TextLength(min = 1, max = 2048)
    private String referer;

    // 文章ID
    private Long articleId;

    /**
     * 区分请求中未提供字段与显式传入 null。
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Set<String> present = new HashSet<>();

    public void setFingerprint(String value) {
        present.add("fingerprint");
        fingerprint = value == null ? null : value.strip();
    }

    public void setSiteCode(String value) {
        present.add("siteCode");
        siteCode = value == null ? null : value.strip();
    }

    public void setPagePath(String value) {
        present.add("pagePath");
        pagePath = value == null ? null : value.strip();
    }

    public void setPageTitle(String value) {
        present.add("pageTitle");
        pageTitle = value == null ? null : value.strip();
    }

    public void setReferer(String value) {
        present.add("referer");
        referer = value == null ? null : value.strip();
    }

    public void setArticleId(Long value) {
        present.add("articleId");
        articleId = value;
    }

    /**
     * 可空字段必须显式出现。
     */
    @AssertTrue(message = "表单内容未完整提交，请刷新页面后重试。")
    public boolean isComplete() {
        return present.size() == 6;
    }
}
