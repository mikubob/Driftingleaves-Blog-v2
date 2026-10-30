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
 * 公开查询不混入待审评论，创建仍要求当前用户和评论权限。
 */
@RestController("publicCommentController")
@RequestMapping("/blog/articles/{articleId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final ICommentService comments;

    /** 每次只展开一层，父链不可见和文章非公开都返回 404。 */
    @GetMapping
    public Result<PageResult<CommentPublicVO>> list(@PathVariable String articleId,
            @Valid @ModelAttribute CommentPublicQueryDTO query) {
        return Result.ok(comments.publicComments(IdUtils.parse(articleId), query));
    }

    /** 用户和 IP 共用一分钟十次窗口；只在这里计数，业务层不重复限流。 */
    @PostMapping
    @SaCheckPermission("blog:comment:create")
    @RateLimit(value = "comment:create", limit = 10, dimension = RateLimit.Dimension.USER)
    public ResponseEntity<Result<CommentOwnVO>> create(@PathVariable String articleId,
            @Valid @RequestBody CommentCreateDTO body) {
        return ResponseEntity.status(201).body(Result.ok(comments.create(IdUtils.parse(articleId), body)));
    }
}
