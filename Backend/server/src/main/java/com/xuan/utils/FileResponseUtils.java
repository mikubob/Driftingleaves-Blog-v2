package com.xuan.utils;

import com.xuan.constant.HttpConstants;
import com.xuan.model.FileContent;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/**
 * 文件响应构建工具，生成安全的内联文件响应并交由 Spring MVC 处理 Range 请求。
 */
public final class FileResponseUtils {

    private FileResponseUtils() {
    }

    /**
     * 构建内联文件响应，设置安全文件名、内容类型及防嗅探响应头，文本类型补齐 UTF-8 编码。
     *
     * @param file 已读取的文件内容及元数据
     * @return 带安全响应头的二进制资源响应
     */
    public static ResponseEntity<Resource> inline(FileContent file) {
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

        return ResponseEntity.ok()
                .contentType(type)
                .contentLength(file.getBytes().length)
                .header(HttpConstants.HEADER_CACHE_CONTROL, HttpConstants.CACHE_NO_STORE)
                .header(HttpConstants.HEADER_CONTENT_TYPE_OPTIONS, HttpConstants.NOSNIFF)
                .header(HttpConstants.HEADER_CONTENT_DISPOSITION, disposition)
                .body(new ByteArrayResource(file.getBytes()));
    }
}
