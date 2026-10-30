package com.xuan.utils;

import com.xuan.exception.BusinessException;

/**
 * 路径和查询里的 ID 也按 JSON ID 的规则检查，只收正十进制字符串。
 */
public final class IdUtils {

    private IdUtils() {
    }

    /** 可选参数没传就返回 null；有符号、前导零或超过 Long 范围都报错。 */
    public static Long parse(String value) {
        if (value == null) {
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
