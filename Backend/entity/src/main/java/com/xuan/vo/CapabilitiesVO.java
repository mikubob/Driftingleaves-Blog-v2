package com.xuan.vo;

import java.util.List;
import java.util.Map;

/**
 * 每项权限分别计算 ALL 或 SELF，数组按字典序返回。
 */
public record CapabilitiesVO(List<String> roleCodes, List<String> permissionCodes, Map<String, String> dataScopes) {

}
