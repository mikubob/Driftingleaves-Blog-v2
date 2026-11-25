package com.xuan.controller.blog;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.constant.HttpConstants;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本人评论管理接口
 */
@Slf4j
@RestController("ownCommentController")
@RequestMapping("/blog/me/comments")
@RequiredArgsConstructor
public class MyCommentController {

    private final ICommentService commentService;

    /**
     * 分页查询本人评论列表
     *
     * @param query 查询参数
     * @return 本人评论分页结果
     */
    @GetMapping
    public Result<PageResult<CommentOwnVO>> list(@Valid @ModelAttribute CommentOwnQueryDTO query) {
        log.info("分页查询本人评论列表: {}", query);
        PageResult<CommentOwnVO> pageResult = commentService.ownComments(query);
        return Result.ok(pageResult);
    }

    /**
     * 根据 ID 查询本人评论详情
     *
     * @param commentId 评论 ID
     * @return 本人评论详情及 X-Revision 响应头
     */
    @GetMapping("/{commentId}")
    public ResponseEntity<Result<CommentOwnVO>> detail(@PathVariable String commentId) {
        log.info("根据 ID 查询本人评论详情: commentId={}", commentId);
        ResourceVersion<CommentOwnVO> result = commentService.ownDetail(IdUtils.parse(commentId));
        return versionResponse(result);
    }

    /**
     * 删除本人评论及其子回复
     *
     * @param commentId 评论 ID
     * @param body 请求体，必须为空
     * @return 成功结果
     */
    @DeleteMapping("/{commentId}")
    @SaCheckPermission("blog:comment:deleteOwn")
    public Result<Void> delete(@PathVariable String commentId, @RequestBody(required = false) String body) {
        log.info("删除本人评论: commentId={}", commentId);
        if (body != null) {
            throw new BusinessException(400, "VALIDATION_ERROR", "本接口不接受请求体");
        }
        commentService.deleteOwn(IdUtils.parse(commentId));
        return Result.ok(null);
    }

    /**
     * 组装带版本头的评论详情响应。
     *
     * @param result 评论详情与版本
     * @return 200 响应
     */
    private ResponseEntity<Result<CommentOwnVO>> versionResponse(ResourceVersion<CommentOwnVO> result) {
        return ResponseEntity.ok()
                .header(HttpConstants.HEADER_X_REVISION, result.getRevision())
                .body(Result.ok(result.getValue()));
    }
}
