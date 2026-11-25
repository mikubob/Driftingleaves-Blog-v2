package com.xuan.controller.blog;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.annotation.RateLimit;
import com.xuan.exception.BusinessException;
import com.xuan.result.Result;
import com.xuan.service.IArticleLikeService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.LikeStateVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 博客端文章点赞接口
 */
@Slf4j
@RestController
@RequestMapping("/blog/articles/{articleId}/like")
@RequiredArgsConstructor
public class ArticleLikeController {

    private final IArticleLikeService articleLikeService;

    /**
     * 查询当前用户对文章的点赞状态
     *
     * @param articleId 文章 ID
     * @return 点赞状态
     */
    @GetMapping
    public Result<LikeStateVO> state(@PathVariable String articleId) {
        log.info("查询文章点赞状态: articleId={}", articleId);
        LikeStateVO state = articleLikeService.state(IdUtils.parse(articleId));
        return Result.ok(state);
    }

    /**
     * 为文章点赞
     *
     * @param articleId 文章 ID
     * @param body 请求体，必须为空
     * @return 点赞状态
     */
    @PutMapping
    @SaCheckPermission("blog:article:like")
    @RateLimit(value = "article:like", limit = 60, dimension = RateLimit.Dimension.USER)
    public Result<LikeStateVO> like(@PathVariable String articleId, @RequestBody(required = false) String body) {
        log.info("文章点赞: articleId={}", articleId);
        rejectBody(body);
        LikeStateVO state = articleLikeService.like(IdUtils.parse(articleId));
        return Result.ok(state);
    }

    /**
     * 取消文章点赞
     *
     * @param articleId 文章 ID
     * @param body 请求体，必须为空
     * @return 点赞状态
     */
    @DeleteMapping
    @SaCheckPermission("blog:article:like")
    @RateLimit(value = "article:like", limit = 60, dimension = RateLimit.Dimension.USER)
    public Result<LikeStateVO> unlike(@PathVariable String articleId, @RequestBody(required = false) String body) {
        log.info("取消文章点赞: articleId={}", articleId);
        rejectBody(body);
        LikeStateVO state = articleLikeService.unlike(IdUtils.parse(articleId));
        return Result.ok(state);
    }

    /**
     * 拒绝非空请求体。
     *
     * @param body 原始请求体
     */
    private void rejectBody(String body) {
        if (body != null) {
            throw new BusinessException(400, "VALIDATION_ERROR", "本接口不接受请求体");
        }
    }
}
