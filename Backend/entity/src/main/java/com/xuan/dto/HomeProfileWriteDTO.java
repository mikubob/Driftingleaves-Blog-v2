package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
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
 * 首次设置与完整替换均须提供全部字段，联系邮箱由管理员明确公开。
 */
@Data
@NoArgsConstructor
public class HomeProfileWriteDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 用户ID
    @NotNull
    private Long userId;

    // 昵称
    @NotNull
    @TextLength(min = 1, max = 64)
    private String nickname;

    // 个人标语
    @NotNull
    @TextLength(max = 100)
    private String tagline;

    // 描述
    @TextLength(min = 1, max = 500)
    private String description;

    // 头像URL
    @TextLength(min = 1, max = 2048)
    private String avatar;

    // 个人网站地址
    @TextLength(min = 1, max = 2048)
    private String website;

    // 公开联系邮箱
    @Email
    @TextLength(min = 1, max = 254)
    private String publicEmail;

    // GitHub地址
    @TextLength(min = 1, max = 2048)
    private String githubUrl;

    // 属地信息
    @TextLength(min = 1, max = 100)
    private String location;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Set<String> present = new HashSet<>();

    public void setUserId(Long value) {
        present.add("userId");
        userId = value;
    }

    public void setNickname(String value) {
        present.add("nickname");
        nickname = value == null ? null : value.strip();
    }

    public void setTagline(String value) {
        present.add("tagline");
        tagline = value == null ? null : value.strip();
    }

    public void setDescription(String value) {
        present.add("description");
        description = value == null ? null : value.strip();
    }

    public void setAvatar(String value) {
        present.add("avatar");
        avatar = value == null ? null : value.strip();
    }

    public void setWebsite(String value) {
        present.add("website");
        website = value == null ? null : value.strip();
    }

    public void setPublicEmail(String value) {
        present.add("publicEmail");
        publicEmail = value == null ? null : value.strip();
    }

    public void setGithubUrl(String value) {
        present.add("githubUrl");
        githubUrl = value == null ? null : value.strip();
    }

    public void setLocation(String value) {
        present.add("location");
        location = value == null ? null : value.strip();
    }

    /**
     * 创建和替换都必须提供可空字段，防止漏传被当作清空。
     */
    @AssertTrue(message = "表单内容未完整提交，请重新打开编辑窗口后重试。")
    public boolean isComplete() {
        return present.size() == 9;
    }
}
