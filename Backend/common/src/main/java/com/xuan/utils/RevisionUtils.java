package com.xuan.utils;

import cn.hutool.core.util.ObjectUtil;
import com.xuan.constant.ErrorCode;
import com.xuan.exception.BusinessException;

/**
 * 版本校验工具，版本为带引号的 SHA-256 值，必须先锁住记录再检查。
 */
public final class RevisionUtils {

    private static final String REVISION_PATTERN = "\"[0-9a-f]{64}\"";
    private static final String DEFAULT_CONFLICT_MESSAGE = "资料已修改，请重新获取";

    private RevisionUtils() {
    }

    /**
     * 检查编辑版本，使用默认冲突提示。
     *
     * @param expected 当前数据库记录计算出的版本
     * @param supplied 客户端提交的版本
     */
    public static void check(String expected, String supplied) {
        check(expected, supplied, DEFAULT_CONFLICT_MESSAGE);
    }

    /**
     * 检查编辑版本，分别处理未传、格式错误和版本过期，不用审计时间充当版本。
     *
     * @param expected 当前数据库记录计算出的版本
     * @param supplied 客户端提交的版本
     * @param conflictMessage 版本冲突时返回给页面的提示文案
     */
    public static void check(String expected, String supplied, String conflictMessage) {
        // 1. 缺失版本返回 428，提示页面先加载最新详情。
        if (ObjectUtil.isNull(supplied)) {
            throw new BusinessException(428, ErrorCode.PRECONDITION_REQUIRED, "尚未取得编辑版本，请重新打开详情后再操作。");
        }

        // 2. 格式不符合带引号的 64 位十六进制摘要时返回 400。
        if (!supplied.matches(REVISION_PATTERN)) {
            throw new BusinessException(400, ErrorCode.VALIDATION_ERROR, "编辑版本无效，请保留当前输入，重新打开详情后再操作。");
        }

        // 3. 与当前锁内记录版本不一致时返回 412，避免覆盖他人修改。
        if (!expected.equals(supplied)) {
            throw new BusinessException(412, ErrorCode.REVISION_CONFLICT, conflictMessage);
        }
    }
}
