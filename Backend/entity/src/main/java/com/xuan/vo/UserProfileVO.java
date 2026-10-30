package com.xuan.vo;

/**
 * 资料和版本在内部一起传给 Controller，版本由它写进响应头。
 */
public record UserProfileVO(MeVO user, String revision) {

}
