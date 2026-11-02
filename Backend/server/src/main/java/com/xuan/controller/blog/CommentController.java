package com.xuan.controller.blog;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.annotation.RateLimit;
import com.xuan.dto.CommentCreateDTO;
import com.xuan.dto.CommentPublicQueryDTO;
import com.xuan.properties.AuthProperties;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ICommentService;
import com.xuan.store.CaptchaStore;
import com.xuan.utils.IdUtils;
import com.xuan.utils.IpUtils;
import com.xuan.vo.CommentOwnVO;
import com.xuan.vo.CommentPublicVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文章评论接口，公开查询不返回待审评论，发表时检查登录和评论权限。
 */
@RestController("publicCommentController")
@RequestMapping("/blog/articles/{articleId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final ICommentService comments;
    private final CaptchaStore captchas;
    private final AuthProperties auth;

    /** 分页查询文章评论，每次只展开直接回复，上级评论不可见或文章非公开时返回 404。 */
    @GetMapping
    public Result<PageResult<CommentPublicVO>> list(@PathVariable String articleId,
            @Valid @ModelAttribute CommentPublicQueryDTO query) {
        return Result.ok(comments.publicComments(IdUtils.parse(articleId), query));
    }

    /** 发表评论，按用户和 IP 限制为每分钟十次，只在接口计数，业务层不重复计数。 */
    @PostMapping
    @SaCheckPermission("blog:comment:create")
    @RateLimit(value = "comment:create", limit = 10, dimension = RateLimit.Dimension.USER)
    public ResponseEntity<Result<CommentOwnVO>> create(@PathVariable String articleId,
            @Valid @RequestBody CommentCreateDTO body, HttpServletRequest request) {
        captchas.verifyArithmetic(request.getHeader("X-Captcha-Id"), request.getHeader("X-Captcha-Code"));
        return ResponseEntity.status(201).body(Result.ok(comments.create(IdUtils.parse(articleId), body,
                IpUtils.clientIp(request, auth.getTrustedProxies()), request.getHeader("User-Agent"))));
    }
}
