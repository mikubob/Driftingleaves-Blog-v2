package com.xuan.result;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 页码保留用户请求的值，总数这类 BIGINT 用十进制字符串返回。
 */
public record PageResult<T>(int page, int pageSize, String total, String totalPages, List<T> records) {

    /** 按数据库查到的分页结果返回，超过末页也保留页码并给空列表。 */
    public static <T> PageResult<T> from(IPage<T> page) {
        return new PageResult<>(Math.toIntExact(page.getCurrent()), Math.toIntExact(page.getSize()),
                Long.toString(page.getTotal()),
                Long.toString(page.getPages()), List.copyOf(page.getRecords()));
    }
}
