package com.xuan.dto;

import cn.hutool.core.util.ObjectUtil;
import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 按字面关键词筛选用户；按角色查找时，已经分配但被禁用的角色也算关联。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserQueryDTO implements Serializable {

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

    // 搜索关键词
    @TextLength(min = 1, max = 100)
    private String keyword;

    // 状态
    @Min(0)
    @Max(1)
    private Integer status;

    // 角色ID
    private String roleId;

    public void setPage(Integer page) {
        this.page = ObjectUtil.defaultIfNull(page, 1);
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword == null ? null : keyword.strip();
    }
}
