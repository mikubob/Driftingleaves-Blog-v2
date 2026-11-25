package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 每项权限分别计算 ALL 或 SELF，数组按字典序返回。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapabilitiesVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 角色编码列表
    private List<String> roleCodes;

    // 权限编码列表
    private List<String> permissionCodes;

    // 各权限对应的数据范围
    private Map<String, String> dataScopes;
}
