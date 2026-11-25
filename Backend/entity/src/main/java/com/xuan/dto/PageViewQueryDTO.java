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
 * 明细筛选时间为[begin,end)，路径不接受查询参数与fragment。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageViewQueryDTO implements Serializable {

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

    // 访客ID
    private String visitorId;

    // 用户ID
    private String userId;

    // 文章ID
    private String articleId;

    // 站点编码
    @Pattern(regexp = "home|blog|cv", message = "请选择主页、博客或简历站点")
    private String siteCode;

    // 页面路径
    @TextLength(min = 1, max = 2048)
    private String pagePath;

    // 来源页
    @TextLength(min = 1, max = 2048)
    private String referer;

    // 开始时间
    private String begin;

    // 结束时间
    private String end;

    public void setPage(Integer page) {
        this.page = ObjectUtil.defaultIfNull(page, 1);
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
    }
}
