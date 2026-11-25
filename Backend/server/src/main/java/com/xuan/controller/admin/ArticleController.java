package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.constant.HttpConstants;
import com.xuan.dto.AdminArticleQueryDTO;
import com.xuan.dto.ArticleWriteDTO;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.BatchStatusDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IArticleService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.ArticleAdminDetailVO;
import com.xuan.vo.ArticleAdminVO;
import com.xuan.vo.ArticleProfileVO;
import com.xuan.vo.BatchVersionsVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 管理端文章接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final IArticleService articleService;

    /**
     * 分页条件查询后台文章列表
     *
     * @param query 查询参数
     * @return 后台文章分页结果
     */
    @GetMapping
    @SaCheckPermission("blog:article:query")
    public Result<PageResult<ArticleAdminVO>> list(@Valid @ModelAttribute AdminArticleQueryDTO query) {
        log.info("分页查询后台文章列表: {}", query);
        PageResult<ArticleAdminVO> pageResult = articleService.adminArticles(query);
        return Result.ok(pageResult);
    }

    /**
     * 根据 ID 查询文章详情
     *
     * @param articleId 文章 ID
     * @return 文章详情及 X-Revision 响应头
     */
    @GetMapping("/{articleId}")
    @SaCheckPermission("blog:article:query")
    public ResponseEntity<Result<ArticleAdminDetailVO>> detail(@PathVariable String articleId) {
        log.info("根据 ID 查询后台文章详情: articleId={}", articleId);
        ArticleProfileVO profile = articleService.detail(IdUtils.parse(articleId));
        return profileResponse(profile);
    }

    /**
     * 创建文章草稿
     *
     * @param body 文章写入参数
     * @return 201 响应、Location 及 X-Revision 响应头
     */
    @PostMapping
    @SaCheckPermission("blog:article:create")
    public ResponseEntity<Result<ArticleAdminDetailVO>> create(@Valid @RequestBody ArticleWriteDTO body) {
        log.info("创建文章草稿: title={}", body.getTitle());
        ArticleProfileVO result = articleService.create(body);
        URI location = URI.create("/api/v1/admin/articles/" + result.getArticle().getArticleId());
        return ResponseEntity.created(location)
                .header(HttpConstants.HEADER_X_REVISION, result.getRevision())
                .body(Result.ok(result.getArticle()));
    }

    /**
     * 根据 ID 更新文章
     *
     * @param articleId 文章 ID
     * @param body 完整文章内容参数
     * @param revision 请求头携带的 If-Revision 版本
     * @return 更新后的文章详情及新 X-Revision 响应头
     */
    @PutMapping("/{articleId}")
    @SaCheckPermission("blog:article:update")
    public ResponseEntity<Result<ArticleAdminDetailVO>> update(@PathVariable String articleId,
            @Validated(ArticleWriteDTO.Replace.class) @RequestBody ArticleWriteDTO body,
            @RequestHeader(value = HttpConstants.HEADER_IF_REVISION, required = false) String revision) {
        log.info("更新文章: articleId={}", articleId);
        ArticleProfileVO profile = articleService.update(IdUtils.parse(articleId), body, revision);
        return profileResponse(profile);
    }

    /**
     * 批量发布或下架文章
     *
     * @param body 批量状态变更参数
     * @return 更新后的批量版本信息
     */
    @PutMapping("/batch-status")
    @SaCheckPermission("blog:article:publish")
    public Result<BatchVersionsVO> status(@Valid @RequestBody BatchStatusDTO body) {
        log.info("批量修改文章发布状态: count={}, status={}", body.getItems().size(), body.getStatus());
        BatchVersionsVO versions = articleService.changeStatusBatch(body.getItems(), body.getStatus());
        return Result.ok(versions);
    }

    /**
     * 批量将文章移入回收站
     *
     * @param body 批量操作参数
     * @return 成功结果
     */
    @PostMapping("/batch-recycle")
    @SaCheckPermission("blog:article:delete")
    public Result<Void> recycle(@Valid @RequestBody BatchRevisionDTO body) {
        log.info("批量移入回收站: count={}", body.getItems().size());
        articleService.recycleBatch(body.getItems());
        return Result.ok(null);
    }

    /**
     * 批量恢复回收站文章
     *
     * @param body 批量操作参数
     * @return 恢复后的批量版本信息
     */
    @PostMapping("/batch-restore")
    @SaCheckPermission("blog:article:restore")
    public Result<BatchVersionsVO> restore(@Valid @RequestBody BatchRevisionDTO body) {
        log.info("批量恢复回收站文章: count={}", body.getItems().size());
        BatchVersionsVO versions = articleService.restoreBatch(body.getItems());
        return Result.ok(versions);
    }

    /**
     * 批量永久删除回收站文章
     *
     * @param body 批量操作参数
     * @return 成功结果
     */
    @PostMapping("/batch-permanent-delete")
    @SaCheckPermission({"blog:article:delete", "blog:article:purge"})
    public Result<Void> purge(@Valid @RequestBody BatchRevisionDTO body) {
        log.info("批量永久删除文章: count={}", body.getItems().size());
        articleService.purgeBatch(body.getItems());
        return Result.ok(null);
    }

    /**
     * 组装文章详情响应，版本放在 X-Revision 响应头。
     *
     * @param result 文章详情与版本
     * @return 200 响应
     */
    private ResponseEntity<Result<ArticleAdminDetailVO>> profileResponse(ArticleProfileVO result) {
        return ResponseEntity.ok()
                .header(HttpConstants.HEADER_X_REVISION, result.getRevision())
                .body(Result.ok(result.getArticle()));
    }
}
