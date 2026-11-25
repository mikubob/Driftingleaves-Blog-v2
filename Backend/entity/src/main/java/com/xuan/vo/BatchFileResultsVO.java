package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 文件对象不能跨项回滚，逐项报告结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchFileResultsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 批量处理结果列表
    private List<BatchFileResultVO> results;
}
