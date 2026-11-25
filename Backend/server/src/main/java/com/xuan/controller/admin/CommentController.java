package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.constant.HttpConstants;
import com.xuan.dto.BatchIdsDTO;
import com.xuan.dto.BatchStatusDTO;
import com.xuan.dto.CommentAdminQueryDTO;
import com.xuan.dto.DiscussionConversationQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ICommentService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.CommentAdminVO;
import com.xuan.vo.CommentProfileVO;
import com.xuan.vo.DiscussionConversationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 管理端文章评论接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
public class CommentController {

    private final ICommentService commentService;

    /**
     * 分页查询后台评论列表
     *
     * @param query 查询参数
     * @return 评论分页结果
     */
    @GetMapping
    @SaCheckPermission("blog:comment:query")
    public Result<PageResult<CommentAdminVO>> list(@Valid @ModelAttribute CommentAdminQueryDTO query) {
        log.info("分页查询后台评论列表: {}", query);
        PageResult<CommentAdminVO> pageResult = commentService.adminComments(query);
        return Result.ok(pageResult);
    }

    /**
     * 查询评论整组对话
     *
     * @param commentId 评论 ID
     * @param query 对话分页参数
     * @return 对话详情
     */
    @GetMapping("/{commentId}/conversation")
    @SaCheckPermission("blog:comment:query")
    public Result<DiscussionConversationVO<CommentAdminVO>> conversation(@PathVariable String commentId,
            @Valid @ModelAttribute DiscussionConversationQueryDTO query) {
        log.info("查询后台评论对话: commentId={}", commentId);
        DiscussionConversationVO<CommentAdminVO> conversation = commentService.conversation(IdUtils.parse(commentId), query);
        return Result.ok(conversation);
    }

    /**
     * 根据 ID 查询评论详情
     *
     * @param commentId 评论 ID
     * @return 评论详情及 X-Revision 响应头
     */
    @GetMapping("/{commentId}")
    @SaCheckPermission("blog:comment:query")
    public ResponseEntity<Result<CommentAdminVO>> detail(@PathVariable String commentId) {
        log.info("根据 ID 查询后台评论详情: commentId={}", commentId);
        CommentProfileVO profile = commentService.detail(IdUtils.parse(commentId));
        return profileResponse(profile);
    }

    /**
     * 批量审核评论
     *
     * @param body 批量审核参数
     * @return 更新后的批量版本信息
     */
    @PutMapping("/batch-review")
    @SaCheckPermission("blog:comment:review")
    public Result<BatchVersionsVO> review(@Valid @RequestBody BatchStatusDTO body) {
        log.info("批量审核评论: count={}, status={}", body.getItems().size(), body.getStatus());
        BatchVersionsVO versions = commentService.reviewBatch(body.getItems(), body.getStatus());
        return Result.ok(versions);
    }

    /**
     * 批量删除评论及子回复
     *
     * @param body 批量删除参数
     * @return 成功结果
     */
    @PostMapping("/batch-delete")
    @SaCheckPermission("blog:comment:moderate")
    public Result<Void> delete(@Valid @RequestBody BatchIdsDTO body) {
        log.info("批量删除评论: count={}", body.ids().size());
        commentService.moderateBatch(body.ids());
        return Result.ok(null);
    }

    /**
     * 组装带版本头的评论详情响应。
     *
     * @param value 评论详情与版本
     * @return 200 响应
     */
    private ResponseEntity<Result<CommentAdminVO>> profileResponse(CommentProfileVO value) {
        return ResponseEntity.ok()
                .header(HttpConstants.HEADER_X_REVISION, value.revision())
                .body(Result.ok(value.comment()));
    }
}
