package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 在线入口只接收站点统计标识，身份由 HTTP 会话确定。 */
public record OnlineTicketCreateDTO(
        @NotNull @Pattern(regexp = "[A-Za-z0-9-]{16,150}", message = "访客标识无效，请刷新页面后重试") String fingerprint,
        @NotNull @Pattern(regexp = "home|blog|cv", message = "请选择主页、博客或简历站点") String siteCode,
        @NotNull @TextLength(min = 1, max = 2048) String pagePath) {
}
