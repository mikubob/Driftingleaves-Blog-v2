package com.xuan.controller.blog;

import com.xuan.annotation.RateLimit;
import com.xuan.controller.FileResponse;
import com.xuan.service.IFileService;
import com.xuan.utils.IdUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 读取公开文章和展示曲目的附件，以及专门公开上传的展示图片。
 */
@RestController("publicFileController")
@RequiredArgsConstructor
public class FileController {

    private final IFileService files;

    /** 读取公开文章附件，管理员 Token 也不能放宽文章公开条件，失败时返回统一 Result。 */
    @GetMapping("/blog/articles/{articleId}/files/{fileId}/content")
    public ResponseEntity<Resource> attachment(@PathVariable String articleId, @PathVariable String fileId) {
        return FileResponse.inline(files.attachment(IdUtils.parse(articleId), IdUtils.parse(fileId), false));
    }

    /** 读取音乐附件，要求曲目当前展示且引用该文件，不能只凭 fileId 读取。 */
    @GetMapping("/blog/music/files/{fileId}/content")
    public ResponseEntity<Resource> music(@PathVariable String fileId) {
        return FileResponse.inline(files.musicAttachment(IdUtils.parse(fileId)));
    }

    /** 读取公开展示图片，普通附件不能通过这个入口读取。 */
    @GetMapping("/blog/display-images/{fileId}")
    @RateLimit(value = "display-image-read", limit = 120)
    public ResponseEntity<Resource> displayImage(@PathVariable String fileId) {
        return FileResponse.inline(files.displayImage(IdUtils.parse(fileId)));
    }
}
