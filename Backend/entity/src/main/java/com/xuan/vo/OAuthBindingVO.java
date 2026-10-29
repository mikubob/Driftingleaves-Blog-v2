package com.xuan.vo;

import java.time.LocalDateTime;

/** 绑定列表只公开平台和创建时间。 */
public record OAuthBindingVO(String source, LocalDateTime createTime) { }
