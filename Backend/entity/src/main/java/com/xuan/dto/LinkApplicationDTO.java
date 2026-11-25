package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * 提交友链申请，只接收站点资料。
 */
@Getter
public class LinkApplicationDTO {

    @NotNull
    @TextLength(min = 1, max = 100)
    private String linkName;

    @NotNull
    @TextLength(min = 1, max = 2048)
    private String url;

    @TextLength(max = 2048)
    private String avatar;

    @TextLength(max = 255)
    private String description;

    /**
     * 去掉友站名称首尾的空白。
     */
    public void setLinkName(String value) {
        linkName = value == null ? null : value.strip();
    }

    /**
     * 去掉友站地址首尾的空白。
     */
    public void setUrl(String value) {
        url = value == null ? null : value.strip();
    }

    /**
     * 保存可空头像地址。
     */
    public void setAvatar(String value) {
        avatar = clean(value);
    }

    /**
     * 保存可空友站简介。
     */
    public void setDescription(String value) {
        description = clean(value);
    }

    /**
     * 可空文本去掉首尾空白，空串按清空处理。
     */
    private String clean(String value) {
        return value == null || value.strip().isEmpty() ? null : value.strip();
    }
}
