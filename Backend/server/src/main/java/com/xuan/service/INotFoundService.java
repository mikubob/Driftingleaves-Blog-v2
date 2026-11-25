package com.xuan.service;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

/**
 * 前端 404 上报服务接口
 */
public interface INotFoundService {

    /**
     * 上报前端 404 访问记录，先检查来源和请求体，再计入当前 IP 的 404 窗口。
     *
     * @param request 当前 HTTP 请求
     * @throws IOException 读取请求流失败时抛出
     */
    void report(HttpServletRequest request) throws IOException;
}
