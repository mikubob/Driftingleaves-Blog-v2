package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 一个资源写入后的版本，不携带业务详情。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchVersionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 主键ID
    private Long id;

    // 资源版本号
    private String revision;
}
