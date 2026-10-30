package com.xuan.controller.blog;

import com.xuan.model.FileContent;
import com.xuan.service.IFileService;
import com.xuan.utils.IdUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 只通过当前 PUBLIC 文章的真实引用读取附件，不按 fileId 单独开放匿名文件。
 */
@RestController("publicFileController")
@RequiredArgsConstructor
public class FileController {

    private final IFileService files;

    /** 即使带管理员 Token 也不放宽 PUBLIC；错误仍由统一处理器返回 Result。 */
    @GetMapping("/blog/articles/{articleId}/files/{fileId}/content")
    public ResponseEntity<byte[]> attachment(@PathVariable String articleId, @PathVariable String fileId) {
        FileContent value = files.attachment(IdUtils.parse(articleId), IdUtils.parse(fileId), false);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(value.mimeType()))
                .contentLength(value.bytes().length).header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition",
                        ContentDisposition.inline().filename(value.name(), StandardCharsets.UTF_8).build().toString())
                .body(value.bytes());
    }
}
