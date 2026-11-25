package com.xuan.controller.security;

import com.xuan.result.Result;
import com.xuan.service.INotFoundService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 前端 404 上报接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class NotFoundController {

    private final INotFoundService notFoundService;

    /**
     * 上报前端 404 访问记录
     *
     * @param request 当前 HTTP 请求
     * @return 成功结果
     * @throws IOException 读取请求流失败时抛出
     */
    @PostMapping({"/blog/security/not-found", "/admin/security/not-found"})
    public Result<Void> report(HttpServletRequest request) throws IOException {
        log.info("上报前端 404 访问: path={}", request.getServletPath());
        notFoundService.report(request);
        return Result.ok(null);
    }
}
