package com.xuan.utils;

import cn.hutool.core.util.ObjectUtil;
import com.xuan.exception.BusinessException;

/**
 * id校验工具，路径、查询和 JSON 参数都使用正十进制字符串。
 */
public final class IdUtils {

    private IdUtils() {
    }

    /** 将字符串id转为 Long，未传时返回 null，包含符号、前导零或超出范围时报错。 */
    public static Long parse(String value) {
        if (ObjectUtil.isNull(value)) {
            return null;
        }
        if (value.matches("[1-9][0-9]{0,18}")) {
            try {
                return Long.valueOf(value);
            } catch (NumberFormatException exception) {
                throw new BusinessException(400, "VALIDATION_ERROR", "ID超出范围");
            }
        }
        throw new BusinessException(400, "VALIDATION_ERROR", "ID须为正十进制字符串");
    }
}
