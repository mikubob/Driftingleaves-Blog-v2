package com.xuan.utils;

import com.xuan.exception.BusinessException;

/**
 * 新业务沿用带引号的 SHA-256 业务版本，校验必须在资源锁内调用。
 */
public final class RevisionUtils {

    private RevisionUtils() {
    }

    /** 区分缺版本、格式错误和版本过期，不把审计时间放进业务值。 */
    public static void check(String expected, String supplied) {
        if (supplied == null) {
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
