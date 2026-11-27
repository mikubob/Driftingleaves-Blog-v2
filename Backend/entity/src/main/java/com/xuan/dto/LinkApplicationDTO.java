package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;

/**
 * 提交友链申请，只接收站点资料。
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class LinkApplicationDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 友链名称
    @NotNull
    @TextLength(min = 1, max = 100)
    private String linkName;

    // 链接地址
    @NotNull
    @TextLength(min = 1, max = 2048)
    private String url;

    // 头像URL
    @TextLength(max = 2048)
    private String avatar;

    // 描述
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
