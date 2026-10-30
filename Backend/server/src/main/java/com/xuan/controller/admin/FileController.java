package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.annotation.RateLimit;
import com.xuan.dto.FileQueryDTO;
import com.xuan.model.FileContent;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IFileService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.FileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * 后台文件元数据、上传和原图入口，图片通过 Bearer fetch 后再显示临时 object URL。
 */
@RestController
@RequiredArgsConstructor
public class FileController {

    private final IFileService files;

    /** 元数据分页不附每条业务版本，上传者筛选不能扩大当前 query 范围。 */
    @GetMapping("/admin/files")
    @SaCheckPermission("blog:file:query")
    public Result<PageResult<FileVO>> list(@Valid @ModelAttribute FileQueryDTO query) {
        return Result.ok(files.listFiles(query));
    }

    /** 元数据不可编辑，不返回 X-Revision。 */
    @GetMapping("/admin/files/{fileId}")
    @SaCheckPermission("blog:file:query")
    public Result<FileVO> detail(@PathVariable String fileId) {
        return Result.ok(files.detail(IdUtils.parse(fileId)));
    }

    /** 每用户及可信 IP 每分钟最多上传十次，请求只计一次；成功给元数据 Location。 */
    @PostMapping(value = "/admin/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckPermission("blog:file:upload")
    @RateLimit(value = "file-upload", limit = 10, dimension = RateLimit.Dimension.USER)
    public ResponseEntity<Result<FileVO>> upload(@RequestParam("file") MultipartFile file) {
        FileVO value = files.upload(file);
        return ResponseEntity.created(URI.create("/api/v1/admin/files/" + value.fileId())).body(Result.ok(value));
    }

    /** 本人原文件预览与文章公开无关，内容响应不包 Result。 */
    @GetMapping("/admin/files/{fileId}/content")
    @SaCheckPermission("blog:file:query")
    public ResponseEntity<byte[]> original(@PathVariable String fileId) {
        return image(files.original(IdUtils.parse(fileId)));
    }

    /** 删除不要求版本，文件锁内必须查全部文章引用，并确认存储删除成功。 */
    @DeleteMapping("/admin/files/{fileId}")
    @SaCheckPermission("blog:file:delete")
    public Result<Void> delete(@PathVariable String fileId) {
        files.delete(IdUtils.parse(fileId));
        return Result.ok(null);
    }

    /** 两项 query 权限及各自范围都要满足，文章还必须真正引用该文件。 */
    @GetMapping("/admin/articles/{articleId}/files/{fileId}/content")
    @SaCheckPermission({"blog:article:query", "blog:file:query"})
    public ResponseEntity<byte[]> attachment(@PathVariable String articleId, @PathVariable String fileId) {
        return image(files.attachment(IdUtils.parse(articleId), IdUtils.parse(fileId), true));
    }

    /** 返回已读完的原图，文件名只进安全编码头，所有图片都禁止缓存和 MIME 猜测。 */
    private ResponseEntity<byte[]> image(FileContent value) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(value.mimeType()))
                .contentLength(value.bytes().length).header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition",
                        ContentDisposition.inline().filename(value.name(), StandardCharsets.UTF_8).build().toString())
                .body(value.bytes());
    }
}
