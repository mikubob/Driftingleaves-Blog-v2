package com.xuan.handle;

import com.xuan.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.HandlerMapping;

/** 多目标版本只能来自请求中的各自版本，不能误用一个请求头覆盖整批。 */
@ControllerAdvice(basePackages = "com.xuan.controller.admin")
public class BatchRequestAdvice {
    /** 批量动作不接受查询参数或单一版本头，模型字段由JSON边界校验。 */
    @ModelAttribute
    public void parameters(HttpServletRequest request) {
        String pattern = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        if (pattern.contains("/batch-")
                && (request.getHeader("If-Revision") != null || request.getQueryString() != null)) {
            throw new BusinessException(400, "VALIDATION_ERROR", "批量操作只接受请求体中的目标和逐条版本");
        }
    }
}
