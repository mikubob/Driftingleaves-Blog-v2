package com.xuan.batch;

import com.xuan.dto.BatchItemDTO;
import com.xuan.utils.IdUtils;
import com.xuan.vo.BatchVersionVO;
import com.xuan.vo.BatchVersionsVO;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 同层资源按数值顺序锁定，响应仍按用户选择顺序返回。 */
public final class BatchInputs {
    private BatchInputs() {
    }

    /** 输入格式由入口验证，排序时仍保留十进制字符串ID。 */
    public static List<BatchItemDTO> sorted(List<BatchItemDTO> items) {
        return items.stream().sorted(Comparator.comparingLong(item -> IdUtils.parse(item.id()))).toList();
    }

    /** 按数值升序锁定无版本目标。 */
    public static List<Long> ids(List<String> ids) {
        return ids.stream().map(IdUtils::parse).sorted().toList();
    }

    /** 只返回对应目标的新版本，不借写权限暴露详情。 */
    public static BatchVersionsVO versions(List<BatchItemDTO> items, Map<Long, String> versions) {
        return new BatchVersionsVO(items.stream().map(item -> {
            long id = IdUtils.parse(item.id());
            return new BatchVersionVO(id, versions.get(id));
        }).toList());
    }
}
