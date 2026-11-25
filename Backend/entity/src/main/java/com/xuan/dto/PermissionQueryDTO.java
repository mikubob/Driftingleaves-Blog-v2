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
 * 按编码或名称里的文字查权限，模块和状态要精确匹配。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionQueryDTO implements Serializable {

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

    // 所属模块
    @Pattern(regexp = "[a-z][a-z0-9_]{0,31}", message = "须以小写字母开头，只能包含小写字母、数字或下划线，最多32位")
    private String module;

    // 状态
    @Min(0)
    @Max(1)
    private Integer status;

    public void setPage(Integer page) {
        this.page = ObjectUtil.defaultIfNull(page, 1);
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword == null ? null : keyword.strip();
    }

    public void setModule(String module) {
        this.module = module == null ? null : module.strip();
    }
}
