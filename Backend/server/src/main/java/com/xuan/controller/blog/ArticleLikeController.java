package com.xuan.controller.blog;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.annotation.RateLimit;
import com.xuan.exception.BusinessException;
import com.xuan.result.Result;
import com.xuan.service.IArticleLikeService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.LikeStateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本人点赞不收 userId；PUT 和 DELETE 共用用户、IP 的一分钟六十次窗口。
 */
@RestController
@RequestMapping("/blog/articles/{articleId}/like")
@RequiredArgsConstructor
public class ArticleLikeController {

    private final IArticleLikeService likes;

    /** 查询只需登录，不额外要求 like 权限，文章仍必须公开。 */
    @GetMapping
    public Result<LikeStateVO> state(@PathVariable String articleId) {
        return Result.ok(likes.state(IdUtils.parse(articleId)));
    }

    /** 重复 PUT 保持 true，不做 toggle。 */
    @PutMapping
    @SaCheckPermission("blog:article:like")
    @RateLimit(value = "article:like", limit = 60, dimension = RateLimit.Dimension.USER)
    public Result<LikeStateVO> like(@PathVariable String articleId, @RequestBody(required = false) String body) {
        rejectBody(body);
        return Result.ok(likes.like(IdUtils.parse(articleId)));
    }

    /** 不存在也返回 false，非公开文章在业务层拒绝且保留原明细。 */
    @DeleteMapping
    @SaCheckPermission("blog:article:like")
    @RateLimit(value = "article:like", limit = 60, dimension = RateLimit.Dimension.USER)
    public Result<LikeStateVO> unlike(@PathVariable String articleId, @RequestBody(required = false) String body) {
        rejectBody(body);
        return Result.ok(likes.unlike(IdUtils.parse(articleId)));
    }

    /**
     * 点赞接口不收请求体，传入实际内容时返回 400。
     */
    private void rejectBody(String body) {
        if (body != null) {
            throw new BusinessException(400, "VALIDATION_ERROR", "本接口不接受请求体");
        }
    }
}
