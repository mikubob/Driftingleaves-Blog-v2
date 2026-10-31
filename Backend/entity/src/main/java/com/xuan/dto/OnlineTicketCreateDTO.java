package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 在线入口只接收站点统计标识，身份由 HTTP 会话确定。 */
public record OnlineTicketCreateDTO(
        @NotNull @Pattern(regexp = "[A-Za-z0-9-]{16,150}") String fingerprint,
        @NotNull @Pattern(regexp = "home|blog|cv") String siteCode,
        @NotNull @TextLength(min = 1, max = 2048) String pagePath) {
}
