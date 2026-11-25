package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 在线入口只接收站点统计标识，身份由 HTTP 会话确定。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlineTicketCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 访客指纹
    @NotNull
    @Pattern(regexp = "[A-Za-z0-9-]{16,150}", message = "访客标识无效，请刷新页面后重试")
    private String fingerprint;

    // 站点编码
    @NotNull
    @Pattern(regexp = "home|blog|cv", message = "请选择主页、博客或简历站点")
    private String siteCode;

    // 页面路径
    @NotNull
    @TextLength(min = 1, max = 2048)
    private String pagePath;
}
