package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 资料和版本在内部一起传给 Controller，版本由它写进响应头。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 用户信息
    private MeVO user;

    // 资源版本号
    private String revision;
}
