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
 * 查询后台评论，parentId 为0时查根节点，未传时保留平铺分页；userId 不扩大文章范围。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentAdminQueryDTO implements Serializable {

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
    @Max(50)
    private Integer pageSize = 10;

    // 文章ID
    private String articleId;

    // 用户ID
    private String userId;

    // 状态
    @Min(0)
    @Max(2)
    private Integer status;

    // 父级ID
    private String parentId;

    public void setPage(Integer page) {
        this.page = ObjectUtil.defaultIfNull(page, 1);
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
    }
}
