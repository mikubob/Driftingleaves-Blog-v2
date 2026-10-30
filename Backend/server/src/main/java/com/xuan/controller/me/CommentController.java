package com.xuan.controller.me;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.CommentOwnQueryDTO;
import com.xuan.exception.BusinessException;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ICommentService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.CommentOwnVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本人查询和删除只取会话用户，文章下架后仍能处理自己的评论。
 */
@RestController("ownCommentController")
@RequestMapping("/me/comments")
@RequiredArgsConstructor
public class CommentController {

    private final ICommentService comments;

    /** 不额外要求 query 权限，也不接收客户端 userId。 */
    @GetMapping
    public Result<PageResult<CommentOwnVO>> list(@Valid @ModelAttribute CommentOwnQueryDTO query) {
        return Result.ok(comments.ownComments(query));
    }

    /** 删除保留占位和回复，不要机械要求 If-Revision。 */
    @DeleteMapping("/{commentId}")
    @SaCheckPermission("blog:comment:deleteOwn")
    public Result<Void> delete(@PathVariable String commentId, @RequestBody(required = false) String body) {
        if (body != null) {
            throw new BusinessException(400, "VALIDATION_ERROR", "本接口不接受请求体");
        }
        comments.deleteOwn(IdUtils.parse(commentId));
        return Result.ok(null);
    }
}
