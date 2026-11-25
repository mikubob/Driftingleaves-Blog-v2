package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 结果严格对应用户原始选择顺序。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchVersionsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 列表项
    private List<BatchVersionVO> items;
}
