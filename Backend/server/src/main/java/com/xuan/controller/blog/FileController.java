package com.xuan.controller.blog;

import com.xuan.model.FileContent;
import com.xuan.service.IFileService;
import com.xuan.utils.IdUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 只通过当前 PUBLIC 文章或展示音乐的真实引用读取文件，不开放任意 fileId。
 */
@RestController("publicFileController")
@RequiredArgsConstructor
public class FileController {

    private final IFileService files;

    /** 即使带管理员 Token 也不放宽 PUBLIC；错误仍由统一处理器返回 Result。 */
    @GetMapping("/blog/articles/{articleId}/files/{fileId}/content")
    public ResponseEntity<Resource> attachment(@PathVariable String articleId, @PathVariable String fileId) {
        return content(files.attachment(IdUtils.parse(articleId), IdUtils.parse(fileId), false));
    }

    /** 受管音乐文件只按当前展示状态开放，不能仅凭 fileId 读取任意文件。 */
    @GetMapping("/blog/music/files/{fileId}/content")
    public ResponseEntity<Resource> music(@PathVariable String fileId) {
        return content(files.musicAttachment(IdUtils.parse(fileId)));
    }

    /** 原生播放器会请求 Range，交给 Spring 的 Resource 响应处理分段和无效范围。 */
    private ResponseEntity<Resource> content(FileContent value) {
        MediaType type = MediaType.parseMediaType(value.mimeType());
        if (value.mimeType().startsWith("text/")) {
            type = new MediaType(type, StandardCharsets.UTF_8);
        }
        return ResponseEntity.ok().contentType(type)
                .contentLength(value.bytes().length).header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition",
                        ContentDisposition.inline().filename(value.name(), StandardCharsets.UTF_8).build().toString())
                .body(new ByteArrayResource(value.bytes()));
    }
}
