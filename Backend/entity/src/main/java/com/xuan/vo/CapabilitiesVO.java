package com.xuan.vo;

import java.util.List;
import java.util.Map;

/** 各权限独立计算 ALL/SELF，数组按字典序。 */
public record CapabilitiesVO(List<String> roleCodes, List<String> permissionCodes, Map<String, String> dataScopes) { }
