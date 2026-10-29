package com.xuan.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;

/** 保留请求页码；BIGINT 分页统计使用十进制字符串。 */
public record PageResult<T>(int page, int pageSize, String total, String totalPages, List<T> records) {
    /** 从查询的真实分页结果生成响应，包括超末页的空列表。 */
    public static <T> PageResult<T> from(IPage<T> page) {
        return new PageResult<>(Math.toIntExact(page.getCurrent()), Math.toIntExact(page.getSize()), Long.toString(page.getTotal()),
                Long.toString(page.getPages()), List.copyOf(page.getRecords()));
    }
}
