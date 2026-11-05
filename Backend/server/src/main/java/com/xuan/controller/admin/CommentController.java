package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.BatchIdsDTO;
import com.xuan.dto.DiscussionConversationQueryDTO;
import com.xuan.vo.DiscussionConversationVO;
import com.xuan.dto.BatchStatusDTO;
import com.xuan.dto.CommentAdminQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ICommentService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.CommentAdminVO;
import com.xuan.vo.CommentProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台评论管理接口，查询、审核和删除分别检查操作权限及文章范围。
 */
@RestController
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
public class CommentController {

    private final ICommentService comments;

    /** 分页查询评论或一层回复，parentId为0查根节点，未传保留平铺；编辑前先查询详情版本。 */
    @GetMapping
    @SaCheckPermission("blog:comment:query")
    public Result<PageResult<CommentAdminVO>> list(@Valid @ModelAttribute CommentAdminQueryDTO query) {
        return Result.ok(comments.adminComments(query));
    }

    /** 查询整组对话，未传页码时定位当前记录。 */
    @GetMapping("/{commentId}/conversation")
    @SaCheckPermission("blog:comment:query")
    public Result<DiscussionConversationVO<CommentAdminVO>> conversation(@PathVariable String commentId,
            @Valid @ModelAttribute DiscussionConversationQueryDTO query) {
        return Result.ok(comments.conversation(IdUtils.parse(commentId), query));
    }

    /** 根据id查询评论详情，按查询权限限定范围，支持待审、驳回和非公开文章的评论。 */
    @GetMapping("/{commentId}")
    @SaCheckPermission("blog:comment:query")
    public ResponseEntity<Result<CommentAdminVO>> detail(@PathVariable String commentId) {
        return profile(comments.detail(IdUtils.parse(commentId)));
    }

    /** 批量审核评论，单条也走此接口，业务层统一检查权限、版本并处理失败。 */
    @PutMapping("/batch-review")
    @SaCheckPermission("blog:comment:review")
    public Result<BatchVersionsVO> review(@Valid @RequestBody BatchStatusDTO body) {
        return Result.ok(comments.reviewBatch(body.getItems(), body.getStatus()));
    }

    /** 批量删除评论及全部子回复，单条也走此接口，业务层统一检查权限、版本并处理失败。 */
    @PostMapping("/batch-delete")
    @SaCheckPermission("blog:comment:moderate")
    public Result<Void> delete(@Valid @RequestBody BatchIdsDTO body) {
        comments.moderateBatch(body.ids());
        return Result.ok(null);
    }

    /**
     * 返回后台评论资料，版本放在 X-Revision 响应头。
     */
    private ResponseEntity<Result<CommentAdminVO>> profile(CommentProfileVO value) {
        return ResponseEntity.ok().header("X-Revision", value.revision()).body(Result.ok(value.comment()));
    }
}
