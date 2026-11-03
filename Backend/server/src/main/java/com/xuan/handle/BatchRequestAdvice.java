package com.xuan.handle;

import com.xuan.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.HandlerMapping;

/** 检查批量操作参数，每条记录单独传版本，不能用一个请求头代替整批版本。 */
@ControllerAdvice(basePackages = "com.xuan.controller.admin")
public class BatchRequestAdvice {
    /** 检查批量请求，不接受查询参数或单条记录的版本头，请求字段由 JSON 解析时校验。 */
    @ModelAttribute
    public void parameters(HttpServletRequest request) {
        String pattern = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        if (pattern.contains("/batch-")
                && (request.getHeader("If-Revision") != null || request.getQueryString() != null)) {
            throw new BusinessException(400, "VALIDATION_ERROR", "批量操作信息无效，请刷新列表、重新选择记录后再操作。");
        }
    }
}
