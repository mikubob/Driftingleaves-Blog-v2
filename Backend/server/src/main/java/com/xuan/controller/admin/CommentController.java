package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.BatchIdsDTO;
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
 * 后台入口之外，查询、审核、管理删除还要分别检查自己的权限和文章范围。
 */
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

    /** 单个和多个目标共用入口，权限、版本及失败结果由业务层统一处理。 */
    @PutMapping("/batch-review")
    @SaCheckPermission("blog:comment:review")
    public Result<BatchVersionsVO> review(@Valid @RequestBody BatchStatusDTO body) {
        return Result.ok(comments.reviewBatch(body.getItems(), body.getStatus()));
    }

    /** 单个和多个目标共用入口，权限、版本及失败结果由业务层统一处理。 */
    @PostMapping("/batch-delete")
    @SaCheckPermission("blog:comment:moderate")
    public Result<Void> delete(@Valid @RequestBody BatchIdsDTO body) {
        comments.moderateBatch(body.ids());
        return Result.ok(null);
    }

    /**
     * 把业务版本写入 X-Revision，响应体保留后台评论资料。
     */
    private ResponseEntity<Result<CommentAdminVO>> profile(CommentProfileVO value) {
        return ResponseEntity.ok().header("X-Revision", value.revision()).body(Result.ok(value.comment()));
    }
}
