package com.xuan.controller.blog;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.CommentOwnQueryDTO;
import com.xuan.exception.BusinessException;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ICommentService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.CommentOwnVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本人评论接口，用户取当前会话，文章下架后仍能查询和删除自己的评论。
 */
@RestController("ownCommentController")
@RequestMapping("/blog/me/comments")
@RequiredArgsConstructor
public class MyCommentController {

    private final ICommentService comments;

    /** 分页查询本人评论，不额外要求查询权限，不接受客户端 userId。 */
    @GetMapping
    public Result<PageResult<CommentOwnVO>> list(@Valid @ModelAttribute CommentOwnQueryDTO query) {
        return Result.ok(comments.ownComments(query));
    }

    /** 根据id查询本人评论及版本。 */
    @GetMapping("/{commentId}")
    public ResponseEntity<Result<CommentOwnVO>> detail(@PathVariable String commentId) {
        return response(comments.ownDetail(IdUtils.parse(commentId)));
    }

    /** 删除本人评论及其全部子回复，不要求 If-Revision。 */
    @DeleteMapping("/{commentId}")
    @SaCheckPermission("blog:comment:deleteOwn")
    public Result<Void> delete(@PathVariable String commentId, @RequestBody(required = false) String body) {
        if (body != null) {
            throw new BusinessException(400, "VALIDATION_ERROR", "本接口不接受请求体");
        }
        comments.deleteOwn(IdUtils.parse(commentId));
        return Result.ok(null);
    }

    /** 将版本放到响应头，正文只返回本人评论资料。 */
    private ResponseEntity<Result<CommentOwnVO>> response(ResourceVersion<CommentOwnVO> result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.value()));
    }
}
