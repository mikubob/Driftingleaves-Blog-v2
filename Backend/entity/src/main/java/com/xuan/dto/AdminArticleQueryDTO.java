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
 * authorId 只是额外筛选条件，不能借它看到 query 权限以外的文章。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminArticleQueryDTO implements Serializable {

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

    // 状态
    @Min(0)
    @Max(2)
    private Integer status;

    // 可见性
    @Min(1)
    @Max(2)
    private Integer visibility;

    // 回收站筛选标记
    @Builder.Default
    @Min(0)
    @Max(1)
    private Integer trash = 0;

    public void setPage(Integer page) {
        this.page = ObjectUtil.defaultIfNull(page, 1);
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword == null ? null : keyword.strip();
    }

    public void setTrash(Integer trash) {
        this.trash = ObjectUtil.defaultIfNull(trash, 0);
    }
}
