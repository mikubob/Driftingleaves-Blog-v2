package com.xuan.controller.blog;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.annotation.RateLimit;
import com.xuan.dto.CommentCreateDTO;
import com.xuan.dto.CommentPublicQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ICommentService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.CommentOwnVO;
import com.xuan.vo.CommentPublicVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 博客端文章评论接口
 */
@Slf4j
@RestController("publicCommentController")
@RequestMapping("/blog/articles/{articleId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final ICommentService commentService;

    /**
     * 分页查询文章公开评论
     *
     * @param articleId 文章 ID
     * @param query 查询参数
     * @return 评论分页结果
     */
    @GetMapping
    public Result<PageResult<CommentPublicVO>> list(@PathVariable String articleId,
            @Valid @ModelAttribute CommentPublicQueryDTO query) {
        log.info("分页查询文章公开评论: articleId={}, query={}", articleId, query);
        PageResult<CommentPublicVO> pageResult = commentService.publicComments(IdUtils.parse(articleId), query);
        return Result.ok(pageResult);
    }

    /**
     * 发表文章评论
     *
     * @param articleId 文章 ID
     * @param body 评论参数
     * @param request 当前 HTTP 请求
     * @return 201 响应及新建评论资料
     */
    @PostMapping
    @SaCheckPermission("blog:comment:create")
    @RateLimit(value = "comment:create", limit = 10, dimension = RateLimit.Dimension.USER)
    public ResponseEntity<Result<CommentOwnVO>> create(@PathVariable String articleId,
            @Valid @RequestBody CommentCreateDTO body,
            HttpServletRequest request) {
        log.info("发表文章评论: articleId={}", articleId);
        CommentOwnVO comment = commentService.createWithCaptcha(IdUtils.parse(articleId), body, request);
        return ResponseEntity.status(201).body(Result.ok(comment));
    }
}
