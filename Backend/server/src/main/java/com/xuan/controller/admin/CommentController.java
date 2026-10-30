package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.CommentAdminQueryDTO;
import com.xuan.dto.CommentReviewDTO;
import com.xuan.exception.BusinessException;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ICommentService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.CommentAdminVO;
import com.xuan.vo.CommentProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台入口之外，查询、审核、管理删除还要分别检查自己的权限和文章范围。 */
@RestController
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
public class CommentController {
    private final ICommentService comments;

    /** 列表不带逐条版本，编辑前先取详情。 */
    @GetMapping
    @SaCheckPermission("blog:comment:query")
    public Result<PageResult<CommentAdminVO>> list(@Valid @ModelAttribute CommentAdminQueryDTO query) {
        return Result.ok(comments.adminComments(query));
    }

    /** 待审、驳回和非公开文章评论也按 query 的独立范围查询。 */
    @GetMapping("/{commentId}")
    @SaCheckPermission("blog:comment:query")
    public ResponseEntity<Result<CommentAdminVO>> detail(@PathVariable String commentId) {
        return profile(comments.detail(IdUtils.parse(commentId)));
    }

    /** 版本留到锁内检查，同目标也不能跳过版本校验。 */
    @PutMapping("/{commentId}/review")
    @SaCheckPermission("blog:comment:review")
    public ResponseEntity<Result<CommentAdminVO>> review(@PathVariable String commentId,
            @Valid @RequestBody CommentReviewDTO body,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(comments.review(IdUtils.parse(commentId), body.status(), revision));
    }

    /** 管理占位删除不要求版本，重复删除仍成功。 */
    @DeleteMapping("/{commentId}")
    @SaCheckPermission("blog:comment:moderate")
    public Result<Void> delete(@PathVariable String commentId, @RequestBody(required = false) String body) {
        if (body != null) {
            throw new BusinessException(400, "VALIDATION_ERROR", "本接口不接受请求体");
        }
        comments.moderate(IdUtils.parse(commentId));
        return Result.ok(null);
    }

    private ResponseEntity<Result<CommentAdminVO>> profile(CommentProfileVO value) {
        return ResponseEntity.ok().header("X-Revision", value.revision()).body(Result.ok(value.comment()));
    }
}
