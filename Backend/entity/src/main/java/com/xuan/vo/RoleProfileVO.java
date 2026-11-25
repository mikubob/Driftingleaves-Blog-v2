package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 内部一起传递角色资料和版本，版本只写到响应头。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleProfileVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 角色信息
    private RoleVO role;

    // 资源版本号
    private String revision;
}
