package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 按 UTC 月份返回该月符合公开条件的文章数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 归档月份
    private String month;

    // 文章数
    private Long articleCount;
}
