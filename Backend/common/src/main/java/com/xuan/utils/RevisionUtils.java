package com.xuan.utils;

import cn.hutool.core.util.ObjectUtil;
import com.xuan.exception.BusinessException;

/**
 * 版本校验工具，版本为带引号的 SHA-256 值，必须先锁住记录再检查。
 */
public final class RevisionUtils {

    private RevisionUtils() {
    }

    /** 检查版本，分别处理未传、格式错误和版本过期，不用审计时间充当版本。 */
    public static void check(String expected, String supplied) {
        if (ObjectUtil.isNull(supplied)) {
            throw new BusinessException(428, "PRECONDITION_REQUIRED", "缺少If-Revision");
        }
        if (!supplied.matches("\"[0-9a-f]{64}\"")) {
            throw new BusinessException(400, "VALIDATION_ERROR", "If-Revision格式错误");
        }
        if (!expected.equals(supplied)) {
            throw new BusinessException(412, "REVISION_CONFLICT", "资料已修改，请重新获取");
        }
    }
}
