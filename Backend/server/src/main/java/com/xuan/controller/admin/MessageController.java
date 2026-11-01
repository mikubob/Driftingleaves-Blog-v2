package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.BatchIdsDTO;
import com.xuan.dto.BatchStatusDTO;
import com.xuan.dto.MessageAdminQueryDTO;
import com.xuan.dto.MessageEditDTO;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IMessageService;
import com.xuan.store.CaptchaStore;
import com.xuan.utils.IdUtils;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.MessagePrivateVO;
import jakarta.servlet.http.HttpServletRequest;
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
 * 后台查看私密留言须本次独立权限提供ALL。
 */
@RestController("adminMessageController")
@RequestMapping("/admin/messages")
@RequiredArgsConstructor
public class MessageController {

    private final IMessageService messages;
    private final CaptchaStore captchas;

    /** 筛选与total使用相同条件。 */
    @GetMapping
    @SaCheckPermission("blog:message:query")
    public Result<PageResult<MessagePrivateVO>> list(@Valid @ModelAttribute MessageAdminQueryDTO query) {
        return Result.ok(messages.adminMessages(query));
    }

    /** 查询权限不能代替审核权限。 */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:message:query")
    public ResponseEntity<Result<MessagePrivateVO>> detail(@PathVariable String id) {
        return response(messages.adminDetail(IdUtils.parse(id)));
    }

    /** 单个和多个目标共用入口，权限、版本及失败结果由业务层统一处理。 */
    @PutMapping("/batch-review")
    @SaCheckPermission("blog:message:review")
    public Result<BatchVersionsVO> review(@Valid @RequestBody BatchStatusDTO body) {
        return Result.ok(messages.reviewBatch(body.getItems(), body.getStatus()));
    }

    /** 单个和多个目标共用入口，权限、版本及失败结果由业务层统一处理。 */
    @PostMapping("/batch-delete")
    @SaCheckPermission("blog:message:moderate")
    public Result<Void> delete(@Valid @RequestBody BatchIdsDTO body) {
        messages.deleteBatch(body.ids());
        return Result.ok(null);
    }

    /** 后台回复用实际操作者身份，沿用review权限，不接收管理员标记。 */
    @PostMapping("/{id}/replies")
    @SaCheckPermission("blog:message:review")
    public ResponseEntity<Result<MessagePrivateVO>> reply(@PathVariable String id,
            @Valid @RequestBody MessageEditDTO request, HttpServletRequest httpRequest) {
        captchas.verify(httpRequest.getHeader("X-Captcha-Id"), httpRequest.getHeader("X-Captcha-Code"));
        return ResponseEntity.status(201).body(Result.ok(messages.replyAdmin(IdUtils.parse(id), request)));
    }

    /** 响应体不含业务版本。 */
    private ResponseEntity<Result<MessagePrivateVO>> response(ResourceVersion<MessagePrivateVO> result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.value()));
    }
}
