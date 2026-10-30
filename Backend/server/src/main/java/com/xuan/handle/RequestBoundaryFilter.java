package com.xuan.handle;

import com.xuan.result.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** JSON 解析前先限制为 8MiB，认证响应和错误响应都不能缓存。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class RequestBoundaryFilter extends OncePerRequestFilter {
    private static final int MAX_JSON_BYTES = 8 * 1024 * 1024;
    private final JsonMapper json;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 1. 进入业务前先设置禁止缓存和来源策略，报错时也要生效。
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        // 2. 这里只限制 JSON 请求，其他类型交给各自处理器。
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT).contains("json")) {
            chain.doFilter(request, response);
            return;
        }
        // 3. 最多多读一个字节来判断超限，不能把无限大的请求体整个读进内存。
        byte[] body = request.getInputStream().readNBytes(MAX_JSON_BYTES + 1);
        if (body.length > MAX_JSON_BYTES) {
            response.setStatus(413);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(json.writeValueAsString(Result.error("PAYLOAD_TOO_LARGE", "JSON请求体超过8MiB")));
            return;
        }
        // 4. 检查过的字节还要交回 MVC，不能因为这里读过就让业务收到空请求体。
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() {
                ByteArrayInputStream input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override
                    public int read() {
                        return input.read();
                    }
                    @Override
                    public boolean isFinished() {
                        return input.available() == 0;
                    }
                    @Override
                    public boolean isReady() {
                        return true;
                    }
                    @Override
                    public void setReadListener(ReadListener listener) {
                        throw new UnsupportedOperationException("同步MVC请求体不支持异步读取");
                    }
                };
            }
            @Override
            public BufferedReader getReader() {
                return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
        }, response);
    }
}
