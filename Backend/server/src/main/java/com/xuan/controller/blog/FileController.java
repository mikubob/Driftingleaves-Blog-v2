package com.xuan.controller.blog;

import com.xuan.annotation.RateLimit;
import com.xuan.model.FileContent;
import com.xuan.service.IFileService;
import com.xuan.utils.FileResponseUtils;
import com.xuan.utils.IdUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 博客端公开文件接口
 */
@Slf4j
@RestController("publicFileController")
@RequiredArgsConstructor
public class FileController {

    private final IFileService fileService;

    /**
     * 读取公开文章附件内容
     *
     * @param articleId 文章 ID
     * @param fileId 文件 ID
     * @return 文件内联响应
     */
    @GetMapping("/blog/articles/{articleId}/files/{fileId}/content")
    public ResponseEntity<Resource> attachment(@PathVariable String articleId, @PathVariable String fileId) {
        log.info("读取公开文章附件: articleId={}, fileId={}", articleId, fileId);
        FileContent content = fileService.attachment(IdUtils.parse(articleId), IdUtils.parse(fileId), false);
        return FileResponseUtils.inline(content);
    }

    /**
     * 读取音乐附件内容
     *
     * @param fileId 文件 ID
     * @return 文件内联响应
     */
    @GetMapping("/blog/music/files/{fileId}/content")
    public ResponseEntity<Resource> music(@PathVariable String fileId) {
        log.info("读取音乐附件: fileId={}", fileId);
        FileContent content = fileService.musicAttachment(IdUtils.parse(fileId));
        return FileResponseUtils.inline(content);
    }

    /**
     * 读取公开展示图片内容
     *
     * @param fileId 文件 ID
     * @return 图片内联响应
     */
    @GetMapping("/blog/display-images/{fileId}")
    @RateLimit(value = "display-image-read", limit = 120)
    public ResponseEntity<Resource> displayImage(@PathVariable String fileId) {
        log.info("读取公开展示图片: fileId={}", fileId);
        FileContent content = fileService.displayImage(IdUtils.parse(fileId));
        return FileResponseUtils.inline(content);
    }
}
