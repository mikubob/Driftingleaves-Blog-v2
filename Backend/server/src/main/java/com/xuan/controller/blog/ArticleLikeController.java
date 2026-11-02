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
 * 文章点赞接口，用户取当前会话，点赞和取消共用每用户、IP 每分钟六十次限制。
 */
@RestController
@RequestMapping("/blog/articles/{articleId}/like")
@RequiredArgsConstructor
public class ArticleLikeController {

    private final IArticleLikeService likes;

    /** 查询本人点赞状态，只要求登录和文章公开，不额外检查点赞权限。 */
    @GetMapping
    public Result<LikeStateVO> state(@PathVariable String articleId) {
        return Result.ok(likes.state(IdUtils.parse(articleId)));
    }

    /** 为文章点赞，重复提交仍返回 true，不切换为取消。 */
    @PutMapping
    @SaCheckPermission("blog:article:like")
    @RateLimit(value = "article:like", limit = 60, dimension = RateLimit.Dimension.USER)
    public Result<LikeStateVO> like(@PathVariable String articleId, @RequestBody(required = false) String body) {
        rejectBody(body);
        return Result.ok(likes.like(IdUtils.parse(articleId)));
    }

    /** 取消文章点赞，原本未点赞时也返回 false；非公开文章拒绝操作并保留原记录。 */
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
