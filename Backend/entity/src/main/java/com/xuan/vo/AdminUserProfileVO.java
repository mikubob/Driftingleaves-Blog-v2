package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 内部一起传递后台用户资料和版本，版本只放响应头。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserProfileVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 用户信息
    private UserAdminVO user;

    // 资源版本号
    private String revision;
}
