package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.annotation.RateLimit;
import com.xuan.dto.BatchIdsDTO;
import com.xuan.dto.FileQueryDTO;
import com.xuan.model.FileContent;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IFileService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.BatchFileResultsVO;
import com.xuan.vo.FileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * 后台文件管理接口，提供文件资料、上传和读取，私密文件携带 Bearer 获取后预览。
 */
@RestController
@RequiredArgsConstructor
public class FileController {

    private final IFileService files;

    /** 分页查询文件资料，不返回每条记录的版本，筛选上传者不能扩大查询权限范围。 */
    @GetMapping("/admin/files")
    @SaCheckPermission("blog:file:query")
    public Result<PageResult<FileVO>> list(@Valid @ModelAttribute FileQueryDTO query) {
        return Result.ok(files.listFiles(query));
    }

    /** 根据id查询文件详情，文件资料不能编辑，不返回 X-Revision。 */
    @GetMapping("/admin/files/{fileId}")
    @SaCheckPermission("blog:file:query")
    public Result<FileVO> detail(@PathVariable String fileId) {
        return Result.ok(files.detail(IdUtils.parse(fileId)));
    }

    /** 上传文件，每用户和可信 IP 每分钟最多十次，每个请求计一次，成功返回文件资料的 Location。 */
    @PostMapping(value = "/admin/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckPermission("blog:file:upload")
    @RateLimit(value = "file-upload", limit = 10, dimension = RateLimit.Dimension.USER)
    public ResponseEntity<Result<FileVO>> upload(@RequestParam("file") MultipartFile file) {
        FileVO value = files.upload(file);
        return ResponseEntity.created(URI.create("/api/v1/admin/files/" + value.fileId())).body(Result.ok(value));
    }

    /** 读取原文件供预览，与文章是否公开无关，文件内容不放进 Result。 */
    @GetMapping("/admin/files/{fileId}/content")
    @SaCheckPermission("blog:file:query")
    public ResponseEntity<Resource> original(@PathVariable String fileId) {
        return content(files.original(IdUtils.parse(fileId)));
    }

    /** 批量删除文件，单条也走此接口，业务层统一检查权限、版本并处理失败。 */
    @PostMapping("/admin/files/batch-delete")
    @SaCheckPermission("blog:file:delete")
    public Result<BatchFileResultsVO> delete(@Valid @RequestBody BatchIdsDTO body) {
        return Result.ok(files.deleteBatch(body.ids()));
    }

    /** 读取后台文章附件，分别检查文章和文件的查询权限及范围，文章必须引用该文件。 */
    @GetMapping("/admin/articles/{articleId}/files/{fileId}/content")
    @SaCheckPermission({"blog:article:query", "blog:file:query"})
    public ResponseEntity<Resource> attachment(@PathVariable String articleId, @PathVariable String fileId) {
        return content(files.attachment(IdUtils.parse(articleId), IdUtils.parse(fileId), true));
    }

    /** 返回文件内容，读取时已检查权限并加锁，Range、206 和 416 由 Spring MVC 处理。 */
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
