package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

/**
 * 首次设置与完整替换均须提供全部字段，联系邮箱由管理员明确公开。
 */
@Getter
public class HomeProfileWriteDTO {

    @NotNull
    private Long userId;

    @NotNull
    @TextLength(min = 1, max = 64)
    private String nickname;

    @NotNull
    @TextLength(max = 100)
    private String tagline;

    @TextLength(min = 1, max = 500)
    private String description;

    @TextLength(min = 1, max = 2048)
    private String avatar;

    @TextLength(min = 1, max = 2048)
    private String website;

    @Email
    @TextLength(min = 1, max = 254)
    private String publicEmail;

    @TextLength(min = 1, max = 2048)
    private String githubUrl;

    @TextLength(min = 1, max = 100)
    private String location;

    @Getter(AccessLevel.NONE)
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

    /** 创建和替换都必须提供可空字段，防止漏传被当作清空。 */
    @AssertTrue(message = "必须提供全部可写字段，可空字段可为null")
    public boolean isComplete() {
        return present.size() == 9;
    }
}
