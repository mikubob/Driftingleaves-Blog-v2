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
 * 公开附件接口，只读取公开文章或展示曲目实际引用的文件，不开放任意 fileId。
 */
@RestController("publicFileController")
@RequiredArgsConstructor
public class FileController {

    private final IFileService files;

    /** 读取公开文章附件，管理员 Token 也不能放宽文章公开条件，失败时返回统一 Result。 */
    @GetMapping("/blog/articles/{articleId}/files/{fileId}/content")
    public ResponseEntity<Resource> attachment(@PathVariable String articleId, @PathVariable String fileId) {
        return content(files.attachment(IdUtils.parse(articleId), IdUtils.parse(fileId), false));
    }

    /** 读取音乐附件，要求曲目当前展示且引用该文件，不能只凭 fileId 读取。 */
    @GetMapping("/blog/music/files/{fileId}/content")
    public ResponseEntity<Resource> music(@PathVariable String fileId) {
        return content(files.musicAttachment(IdUtils.parse(fileId)));
    }

    /** 返回文件内容，交给 Spring Resource 处理播放器的 Range 请求和无效范围。 */
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
