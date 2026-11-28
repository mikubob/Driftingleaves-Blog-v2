package com.xuan.utils;

import com.xuan.constant.HttpConstants;
import com.xuan.model.FileContent;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 文件响应构建工具，生成安全的内联流式文件响应并交由 Spring MVC 写出与关闭流。
 */
public final class FileResponseUtils {

    private FileResponseUtils() {
    }

    /**
     * 构建内联文件响应，设置安全文件名、内容类型及防嗅探响应头，文本类型补齐 UTF-8 编码。
     *
     * @param file 已校验权限并打开的流式文件描述
     * @return 带安全响应头的流式资源响应
     */
    public static ResponseEntity<Resource> inline(FileContent file) {
        try {
            // 1. 解析媒体类型，文本类文件显式补充 UTF-8，避免浏览器乱码。
            MediaType type = MediaType.parseMediaType(file.getMimeType());
            if (file.getMimeType().startsWith("text/")) {
                type = new MediaType(type, StandardCharsets.UTF_8);
            }

            // 2. 组装安全响应头，禁止浏览器嗅探内容类型并编码文件名。
            String disposition = ContentDisposition.inline()
                    .filename(file.getName(), StandardCharsets.UTF_8)
                    .build()
                    .toString();

            // 3. 本地文件直接使用 FileSystemResource，OSS 使用带长度的 InputStreamResource 流式写出。
            Resource resource = file.getLocalPath() != null
                    ? new FileSystemResource(file.getLocalPath())
                    : new SizedInputStreamResource(file.getStream(), file.getSizeBytes(), file.getName());

            return ResponseEntity.ok()
                    .contentType(type)
                    .contentLength(file.getSizeBytes())
                    .header(HttpConstants.HEADER_CACHE_CONTROL, HttpConstants.CACHE_NO_STORE)
                    .header(HttpConstants.HEADER_CONTENT_TYPE_OPTIONS, HttpConstants.NOSNIFF)
                    .header(HttpConstants.HEADER_CONTENT_DISPOSITION, disposition)
                    .body(resource);
        } catch (RuntimeException error) {
            closeQuietly(file.getStream());
            throw error;
        }
    }

    /**
     * 响应组装失败时安静关闭已打开的输入流，避免连接泄漏。
     */
    private static void closeQuietly(InputStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException ignored) {
            // 保持原异常对外抛出。
        }
    }

    /**
     * 携带已知字节长度和文件名的输入流资源，供 Spring MVC 准确写出 Content-Length 并关闭流。
     */
    private static final class SizedInputStreamResource extends InputStreamResource {

        private final long sizeBytes;
        private final String filename;

        private SizedInputStreamResource(InputStream inputStream, long sizeBytes, String filename) {
            super(inputStream);
            this.sizeBytes = sizeBytes;
            this.filename = filename;
        }

        /**
         * 返回已校验的文件字节大小，避免基类读取流估算长度。
         */
        @Override
        public long contentLength() {
            return sizeBytes;
        }

        /**
         * 返回对外展示的文件名。
         */
        @Override
        public String getFilename() {
            return filename;
        }
    }
}
