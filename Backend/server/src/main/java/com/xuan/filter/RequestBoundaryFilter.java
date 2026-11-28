package com.xuan.filter;

import com.xuan.constant.ErrorCode;
import com.xuan.constant.HttpConstants;
import com.xuan.exception.BusinessException;
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
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 限制 JSON 请求体不超过 8MiB，并禁止缓存登录和错误响应。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class RequestBoundaryFilter extends OncePerRequestFilter {

    private final JsonMapper json;

    /**
     * 预检 Content-Length 并包装有界输入流，避免在鉴权与限流前将超大 JSON 整包读入内存。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 1. 进入业务前先设置禁止缓存和来源策略，报错时也要生效。
        response.setHeader(HttpConstants.HEADER_CACHE_CONTROL, HttpConstants.CACHE_NO_STORE);
        response.setHeader(HttpConstants.HEADER_REFERRER_POLICY, HttpConstants.REFERRER_NO_REFERRER);

        // 2. 这里只限制 JSON 请求，其他类型交给各自处理器。
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).contains("json")) {
            chain.doFilter(request, response);
            return;
        }

        // 3. 声明长度已超过 8 MiB 时直接返回 413，不读取请求体。
        if (request.getContentLengthLong() > HttpConstants.MAX_JSON_BODY_BYTES) {
            writePayloadTooLarge(response);
            return;
        }

        // 4. 分块传输或未准确声明长度的请求，使用有界流式包装按需读取并限制总字节数。
        chain.doFilter(new BoundedJsonRequestWrapper(request), response);
    }

    /**
     * 返回 413 JSON 响应。
     */
    private void writePayloadTooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(413);
        response.setContentType(HttpConstants.APPLICATION_JSON_UTF8);
        response.getWriter()
                .write(json.writeValueAsString(Result.error(ErrorCode.PAYLOAD_TOO_LARGE, "JSON请求体超过8MiB")));
    }

    /**
     * 限制单次 JSON 请求读取总字节数的请求包装器。
     */
    private static final class BoundedJsonRequestWrapper extends HttpServletRequestWrapper {

        private ServletInputStream boundedStream;

        private BoundedJsonRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        /**
         * 返回带 8 MiB 上限的流式输入流，避免整包缓存到堆内存。
         */
        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (boundedStream == null) {
                boundedStream = new BoundedServletInputStream(super.getInputStream(), HttpConstants.MAX_JSON_BODY_BYTES);
            }
            return boundedStream;
        }

        /**
         * 以 UTF-8 文本读取受限的请求体。
         */
        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }

    /**
     * 委托到底层流的有界 ServletInputStream，支持批量数组读取，超限时抛出 413 业务异常。
     */
    private static final class BoundedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private long totalRead;

        private BoundedServletInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        /**
         * 读取单字节并累计计数。
         */
        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                count(1);
            }
            return value;
        }

        /**
         * 委托底层流执行数组批量读取，避免退化为逐字节调用。
         */
        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            long remaining = maxBytes - totalRead;
            // 最多多探测 1 字节即可确认超限，防止单次大数组读取过多数据。
            int allowed = (int) Math.min(length, remaining + 1);
            int read = delegate.read(buffer, offset, allowed);
            if (read > 0) {
                count(read);
            }
            return read;
        }

        /**
         * 累计已读字节，超过上限立即抛出 413 异常。
         */
        private void count(int read) {
            totalRead += read;
            if (totalRead > maxBytes) {
                throw new BusinessException(413, ErrorCode.PAYLOAD_TOO_LARGE, "JSON请求体超过8MiB");
            }
        }

        /**
         * 委托底层流判断是否读完。
         */
        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        /**
         * 委托底层流判断是否可读。
         */
        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        /**
         * 委托底层流注册异步读取监听。
         */
        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
