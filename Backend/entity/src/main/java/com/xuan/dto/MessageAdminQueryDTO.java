package com.xuan.dto;

import cn.hutool.core.util.ObjectUtil;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 查询后台留言，parentId 为0时查根节点，未传时保留平铺分页；查询须有ALL范围。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageAdminQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 页码
    @Builder.Default
    @Min(1)
    @Max(100000)
    private Integer page = 1;

    // 每页条数
    @Builder.Default
    @Min(1)
    @Max(100)
    private Integer pageSize = 10;

    // 状态
    @Min(0)
    @Max(2)
    private Integer status;

    // 用户ID
    private String userId;

    // 是否悄悄话
    @Min(0)
    @Max(1)
    private Integer isSecret;

    // 开始时间
    private String begin;

    // 结束时间
    private String end;

    // 父级ID
    private String parentId;

    public void setPage(Integer page) {
        this.page = ObjectUtil.defaultIfNull(page, 1);
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
    }
}
