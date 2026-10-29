package com.xuan.vo;



/** 只列配置完整的平台，不返回应用凭据。 */
public record ProviderVO(String source, String displayName) { }
