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
 * 公开文章只收这些筛选条件，ID 先保留字符串，入口检查通过后再交给业务层。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleQueryDTO implements Serializable {

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

    // 分类ID
    private String categoryId;

    // 标签ID
    private String tagId;

    // 作者ID
    private String authorId;

    // 归档月份
    @Pattern(regexp = "[1-9][0-9]{3}-(0[1-9]|1[0-2])", message = "月份格式须为年-月，例如2026-11")
    private String month;

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
