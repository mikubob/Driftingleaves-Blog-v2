package com.xuan.dto;

import cn.hutool.core.util.ObjectUtil;
import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 后台按友链名称、网址、状态、来源和申请人筛选。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkQueryDTO implements Serializable {

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
    @Max(2)
    private Integer status;

    // 来源
    @Pattern(regexp = "APPLICATION|ADMIN|UNKNOWN")
    private String source;

    // 申请人ID
    private String applicantId;

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
