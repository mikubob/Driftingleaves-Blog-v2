package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 内部一起传递权限详情和版本，版本只写到响应头。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionProfileVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 权限信息
    private PermissionVO permission;

    // 资源版本号
    private String revision;
}
