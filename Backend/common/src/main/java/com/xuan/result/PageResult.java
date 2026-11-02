package com.xuan.result;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 分页响应，保留请求页码，总数等 BIGINT 使用十进制字符串。
 */
public record PageResult<T>(int page, int pageSize, String total, String totalPages, List<T> records) {

    /** 转换数据库分页结果，超出末页时保留原页码并返回空列表。 */
    public static <T> PageResult<T> from(IPage<T> page) {
        return new PageResult<>(Math.toIntExact(page.getCurrent()), Math.toIntExact(page.getSize()),
                Long.toString(page.getTotal()),
                Long.toString(page.getPages()), List.copyOf(page.getRecords()));
    }
}
