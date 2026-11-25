package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 绑定成功的结果只返回 binding。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthBindResultVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 第三方授权操作类型
    private String operation;

    // 第三方绑定信息
    private OAuthBindingVO binding;
}
