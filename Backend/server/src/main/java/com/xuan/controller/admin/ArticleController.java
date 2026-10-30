package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.AdminArticleQueryDTO;
import com.xuan.dto.ArticleStateDTO;
import com.xuan.dto.ArticleWriteDTO;
import com.xuan.exception.BusinessException;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IArticleService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.ArticleAdminDetailVO;
import com.xuan.vo.ArticleAdminVO;
import com.xuan.vo.ArticleProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 后台文章先检查入口和操作权限，再由业务事务检查各自范围和版本。
 */
@RestController
@RequestMapping("/admin/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final IArticleService articles;

    /**
     * 按 query 权限范围分页，默认只看正常文章，列表不带正文和每条版本。
     *
     * @param query 已校验的分页及过滤，authorId不能扩大SELF
     * @return 200后台文章分页
     */
    @GetMapping
    @SaCheckPermission("blog:article:query")
    public Result<PageResult<ArticleAdminVO>> list(@Valid @ModelAttribute AdminArticleQueryDTO query) {
        return Result.ok(articles.adminArticles(query));
    }

    /**
     * 按 query 权限范围取文章完整详情，回收站文章也能查。
     *
     * @param articleId 正十进制目标ID
     * @return 200详情及X-Revision，不存在或越界404
     */
    @GetMapping("/{articleId}")
    @SaCheckPermission("blog:article:query")
    public ResponseEntity<Result<ArticleAdminDetailVO>> detail(@PathVariable String articleId) {
        return profile(articles.detail(IdUtils.parse(articleId)));
    }

    /**
     * 检查后台入口和 create 权限后，以当前用户创建草稿；失败时不能留下半篇文章。
     *
     * @param body 已校验的创建字段及默认值，不接受身份或服务端字段
     * @return 201 完整详情、Location及X-Revision
     */
    @PostMapping
    @SaCheckPermission("blog:article:create")
    public ResponseEntity<Result<ArticleAdminDetailVO>> create(@Valid @RequestBody ArticleWriteDTO body) {
        ArticleProfileVO result = articles.create(body);
        return ResponseEntity.created(URI.create("/api/v1/admin/articles/" + result.article().getArticleId()))
                .header("X-Revision", result.revision()).body(Result.ok(result.article()));
    }

    /**
     * PUT 要填全，可空字段也要传；锁内查 update 范围，已发布文章还要单独查 publish 范围。
     *
     * @param articleId 正十进制目标ID
     * @param body 全部允许的内容字段，不接受状态或HTML
     * @param revision 原样详情版本，缺失交给权限检查后的锁事务返回428
     * @return 200详情及新X-Revision，过期412，不部分覆盖
     */
    @PutMapping("/{articleId}")
    @SaCheckPermission("blog:article:update")
    public ResponseEntity<Result<ArticleAdminDetailVO>> update(@PathVariable String articleId,
            @Validated(ArticleWriteDTO.Replace.class) @RequestBody ArticleWriteDTO body,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(articles.update(IdUtils.parse(articleId), body, revision));
    }

    /**
     * 按 publish 范围发布或下架；目标状态相同也要查版本，回收站文章不能改。
     *
     * @param articleId 正十进制目标ID
     * @param body 只允许status=1或2
     * @param revision 当前整体版本
     * @return 200详情及新版本，首次发布时间只由服务端写入
     */
    @PutMapping("/{articleId}/status")
    @SaCheckPermission("blog:article:publish")
    public ResponseEntity<Result<ArticleAdminDetailVO>> status(@PathVariable String articleId,
            @Valid @RequestBody ArticleStateDTO body,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(articles.changeStatus(IdUtils.parse(articleId), body.status(), revision));
    }

    /**
     * 检查 delete 范围和版本后只移入回收站，正文和关联都保留。
     *
     * @param articleId 正十进制目标ID
     * @param revision 当前整体版本，已回收仍先检查
     * @param body 本接口不接受请求体，防止静默忽略未声明字段
     * @return 200空结果，无X-Revision
     */
    @DeleteMapping("/{articleId}")
    @SaCheckPermission("blog:article:delete")
    public Result<Void> recycle(@PathVariable String articleId,
            @RequestHeader(value = "If-Revision", required = false) String revision,
            @RequestBody(required = false) String body) {
        requireNoBody(body);
        articles.recycle(IdUtils.parse(articleId), revision);
        return Result.ok(null);
    }

    /**
     * 检查 restore 范围后恢复回收站文章，固定为下架状态，不自动发布。
     *
     * @param articleId 正十进制目标ID
     * @param revision 当前回收站整体版本
     * @param body 本接口不接受请求体
     * @return 200详情及版本，非回收站409
     */
    @PostMapping("/{articleId}/restore")
    @SaCheckPermission("blog:article:restore")
    public ResponseEntity<Result<ArticleAdminDetailVO>> restore(@PathVariable String articleId,
            @RequestHeader(value = "If-Revision", required = false) String revision,
            @RequestBody(required = false) String body) {
        requireNoBody(body);
        return profile(articles.restore(IdUtils.parse(articleId), revision));
    }

    /** 这些接口没有请求体，传了实际内容就拒绝，不能悄悄接受身份、状态或时间字段。 */
    private void requireNoBody(String body) {
        if (body != null) {
            throw new BusinessException(400, "VALIDATION_ERROR", "本接口不接受请求体");
        }
    }

    /**
     * 把文章整体版本写进响应头，响应体只返回约定的后台详情字段。
     *
     * @param result 同一快照或锁事务生成的详情与版本
     * @return 200 约定详情及X-Revision
     */
    private ResponseEntity<Result<ArticleAdminDetailVO>> profile(ArticleProfileVO result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.article()));
    }
}
