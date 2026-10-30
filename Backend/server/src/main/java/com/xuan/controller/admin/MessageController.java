package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.result.Result;
import com.xuan.result.PageResult;
import com.xuan.service.IMessageService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.MessagePrivateVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ModelAttribute;
import com.xuan.dto.MessageAdminQueryDTO;
import com.xuan.dto.MessageEditDTO;
import com.xuan.dto.CommentReviewDTO;
import com.xuan.model.ResourceVersion;

/** 后台查看私密留言须本次独立权限提供ALL。 */
@RestController("adminMessageController")
@RequestMapping("/admin/messages")
@RequiredArgsConstructor
public class MessageController {
    private final IMessageService messages;
    /** 筛选与total使用相同条件。 */
    @GetMapping
    @SaCheckPermission("blog:message:query")
    public Result<PageResult<MessagePrivateVO>> list(@Valid @ModelAttribute MessageAdminQueryDTO query) { return Result.ok(messages.adminMessages(query)); }
    /** 查询权限不能代替审核权限。 */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:message:query")
    public ResponseEntity<Result<MessagePrivateVO>> detail(@PathVariable String id) { return response(messages.adminDetail(IdUtils.parse(id))); }
    /** 通过也不改变原私密性；同目标仍检查版本。 */
    @PutMapping("/{id}/review")
    @SaCheckPermission("blog:message:review")
    public ResponseEntity<Result<MessagePrivateVO>> review(@PathVariable String id, @Valid @RequestBody CommentReviewDTO request,
            @RequestHeader(value = "If-Revision", required = false) String revision) { return response(messages.review(IdUtils.parse(id), request.status(), revision)); }
    /** 删除只清原文并占位，不物理级联子树。 */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:message:moderate")
    public Result<Void> delete(@PathVariable String id) { messages.deleteAdmin(IdUtils.parse(id)); return Result.ok(null); }
    /** 后台回复用实际操作者身份，沿用review权限，不接收管理员标记。 */
    @PostMapping("/{id}/replies")
    @SaCheckPermission("blog:message:review")
    public ResponseEntity<Result<MessagePrivateVO>> reply(@PathVariable String id, @Valid @RequestBody MessageEditDTO request) {
        return ResponseEntity.status(201).body(Result.ok(messages.replyAdmin(IdUtils.parse(id), request)));
    }
    /** 响应体不含业务版本。 */
    private ResponseEntity<Result<MessagePrivateVO>> response(ResourceVersion<MessagePrivateVO> result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.value()));
    }
}
