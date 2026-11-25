package com.xuan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 资料和版本一起读，Controller 只把版本放响应头。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceVersion<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 资源数据
    private T value;

    // 资源版本号
    private String revision;
}
