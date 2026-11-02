package com.xuan.utils;

import com.xuan.dto.BatchItemDTO;
import com.xuan.utils.IdUtils;
import com.xuan.vo.BatchVersionVO;
import com.xuan.vo.BatchVersionsVO;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 批量操作工具，按id数值排序供加锁使用，结果按用户选择顺序返回。 */
public final class BatchUtils {
    private BatchUtils() {
    }

    /** 按id数值升序排列批量目标，输入格式由接口校验，排序后id仍为字符串。 */
    public static List<BatchItemDTO> sorted(List<BatchItemDTO> items) {
        return items.stream().sorted(Comparator.comparingLong(item -> IdUtils.parse(item.id()))).toList();
    }

    /** 将字符串id转为 Long 并升序排列，供调用方依次加锁。 */
    public static List<Long> ids(List<String> ids) {
        return ids.stream().map(IdUtils::parse).sorted().toList();
    }

    /** 按原选择顺序返回每条记录的新版本，不附带详情。 */
    public static BatchVersionsVO versions(List<BatchItemDTO> items, Map<Long, String> versions) {
        return new BatchVersionsVO(items.stream().map(item -> {
            long id = IdUtils.parse(item.id());
            return new BatchVersionVO(id, versions.get(id));
        }).toList());
    }
}
