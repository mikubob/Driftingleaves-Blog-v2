package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.annotation.RateLimit;
import com.xuan.controller.FileResponse;
import com.xuan.dto.BatchIdsDTO;
import com.xuan.dto.FileQueryDTO;
import com.xuan.properties.PublicSiteProperties;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IFileService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.BatchFileResultsVO;
import com.xuan.vo.FileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 后台文件管理接口，提供文件资料、上传和读取，私密文件通过后台会话读取后预览。
 */
@RestController
@RequiredArgsConstructor
public class FileController {

    private final IFileService files;
    private final PublicSiteProperties sites;

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
        FileVO value = files.upload(file, false);
        return ResponseEntity.created(URI.create("/api/v1/admin/files/" + value.fileId())).body(Result.ok(value));
    }

    /** 上传公开展示图片，与普通上传共用限流和文件校验，成功后返回可直接使用的地址。 */
    @PostMapping(value = "/admin/files/display-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckPermission("blog:file:upload")
    @RateLimit(value = "file-upload", limit = 10, dimension = RateLimit.Dimension.USER)
    public ResponseEntity<Result<String>> uploadDisplayImage(@RequestParam("file") MultipartFile file) {
        URI publicApi = sites.base("api");
        FileVO value = files.upload(file, true);
        URI url = URI.create(publicApi + "/blog/display-images/" + value.fileId());
        return ResponseEntity.created(url).body(Result.ok(url.toString()));
    }

    /** 读取原文件供预览，与文章是否公开无关，文件内容不放进 Result。 */
    @GetMapping("/admin/files/{fileId}/content")
    @SaCheckPermission("blog:file:query")
    public ResponseEntity<Resource> original(@PathVariable String fileId) {
        return FileResponse.inline(files.original(IdUtils.parse(fileId)));
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
        return FileResponse.inline(files.attachment(IdUtils.parse(articleId), IdUtils.parse(fileId), true));
    }
}
