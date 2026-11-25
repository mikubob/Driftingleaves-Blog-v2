package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 一个文件的实际执行结果，失败不掩盖其他文件已经成功。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchFileResultVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 主键ID
    private Long id;

    // HTTP状态码
    private int httpStatus;

    // 状态码
    private String code;

    // 提示信息
    private String msg;
}
