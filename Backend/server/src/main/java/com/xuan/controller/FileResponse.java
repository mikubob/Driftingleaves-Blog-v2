package com.xuan.controller;

import com.xuan.model.FileContent;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/** 生成文件内容响应，交给 Spring MVC 处理 Range 请求。 */
public final class FileResponse {

    private FileResponse() {
    }

    /** 设置安全文件名和实际内容类型，文本使用 UTF-8。 */
    public static ResponseEntity<Resource> inline(FileContent file) {
        MediaType type = MediaType.parseMediaType(file.mimeType());
        if (file.mimeType().startsWith("text/")) {
            type = new MediaType(type, StandardCharsets.UTF_8);
        }
        return ResponseEntity.ok().contentType(type)
                .contentLength(file.bytes().length).header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition",
                        ContentDisposition.inline().filename(file.name(), StandardCharsets.UTF_8).build().toString())
                .body(new ByteArrayResource(file.bytes()));
    }
}
