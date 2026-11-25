package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.annotation.RateLimit;
import com.xuan.dto.BatchIdsDTO;
import com.xuan.dto.FileQueryDTO;
import com.xuan.model.FileContent;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IFileService;
import com.xuan.utils.FileResponseUtils;
import com.xuan.utils.IdUtils;
import com.xuan.vo.BatchFileResultsVO;
import com.xuan.vo.FileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
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

/**
 * 管理端文件接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class FileController {

    private final IFileService fileService;

    /**
     * 分页查询文件列表
     *
     * @param query 查询参数
     * @return 文件分页结果
     */
    @GetMapping("/admin/files")
    @SaCheckPermission("blog:file:query")
    public Result<PageResult<FileVO>> list(@Valid @ModelAttribute FileQueryDTO query) {
        log.info("分页查询文件列表: {}", query);
        PageResult<FileVO> pageResult = fileService.listFiles(query);
        return Result.ok(pageResult);
    }

    /**
     * 根据 ID 查询文件详情
     *
     * @param fileId 文件 ID
     * @return 文件详情
     */
    @GetMapping("/admin/files/{fileId}")
    @SaCheckPermission("blog:file:query")
    public Result<FileVO> detail(@PathVariable String fileId) {
        log.info("根据 ID 查询文件详情: fileId={}", fileId);
        FileVO file = fileService.detail(IdUtils.parse(fileId));
        return Result.ok(file);
    }

    /**
     * 上传文件
     *
     * @param file 上传文件
     * @param usage 使用场景标识
     * @return 201 响应及文件资料
     */
    @PostMapping(value = "/admin/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckPermission("blog:file:upload")
    @RateLimit(value = "file-upload", limit = 10, dimension = RateLimit.Dimension.USER)
    public ResponseEntity<Result<FileVO>> upload(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "usage", required = false) String usage) {
        log.info("上传文件: fileName={}, usage={}", file.getOriginalFilename(), usage);
        FileVO value = fileService.upload(file, false, usage);
        URI location = URI.create("/api/v1/admin/files/" + value.fileId());
        return ResponseEntity.created(location).body(Result.ok(value));
    }

    /**
     * 上传公开展示图片
     *
     * @param file 上传图片文件
     * @param usage 使用场景标识
     * @return 201 响应及公开展示地址
     */
    @PostMapping(value = "/admin/files/display-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckPermission("blog:file:upload")
    @RateLimit(value = "file-upload", limit = 10, dimension = RateLimit.Dimension.USER)
    public ResponseEntity<Result<String>> uploadDisplayImage(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "usage", required = false) String usage) {
        log.info("上传公开展示图片: fileName={}, usage={}", file.getOriginalFilename(), usage);
        URI url = fileService.uploadDisplayImage(file, usage);
        return ResponseEntity.created(url).body(Result.ok(url.toString()));
    }

    /**
     * 读取原文件内容
     *
     * @param fileId 文件 ID
     * @return 文件内联响应
     */
    @GetMapping("/admin/files/{fileId}/content")
    @SaCheckPermission("blog:file:query")
    public ResponseEntity<Resource> original(@PathVariable String fileId) {
        log.info("读取原文件内容: fileId={}", fileId);
        FileContent content = fileService.original(IdUtils.parse(fileId));
        return FileResponseUtils.inline(content);
    }

    /**
     * 批量删除文件
     *
     * @param body 批量删除参数
     * @return 批量删除结果
     */
    @PostMapping("/admin/files/batch-delete")
    @SaCheckPermission("blog:file:delete")
    public Result<BatchFileResultsVO> delete(@Valid @RequestBody BatchIdsDTO body) {
        log.info("批量删除文件: count={}", body.ids().size());
        BatchFileResultsVO result = fileService.deleteBatch(body.ids());
        return Result.ok(result);
    }

    /**
     * 读取后台文章附件内容
     *
     * @param articleId 文章 ID
     * @param fileId 文件 ID
     * @return 文件内联响应
     */
    @GetMapping("/admin/articles/{articleId}/files/{fileId}/content")
    @SaCheckPermission({"blog:article:query", "blog:file:query"})
    public ResponseEntity<Resource> attachment(@PathVariable String articleId, @PathVariable String fileId) {
        log.info("读取后台文章附件: articleId={}, fileId={}", articleId, fileId);
        FileContent content = fileService.attachment(IdUtils.parse(articleId), IdUtils.parse(fileId), true);
        return FileResponseUtils.inline(content);
    }
}
