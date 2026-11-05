package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.BatchIdsDTO;
import com.xuan.dto.DiscussionConversationQueryDTO;
import com.xuan.vo.DiscussionConversationVO;
import com.xuan.dto.BatchStatusDTO;
import com.xuan.dto.MessageAdminQueryDTO;
import com.xuan.dto.MessageEditDTO;
import com.xuan.model.ResourceVersion;
import com.xuan.properties.AuthProperties;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IMessageService;
import com.xuan.utils.IdUtils;
import com.xuan.utils.IpUtils;
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
 * 后台留言管理接口，查看私密留言需要本次操作权限的 ALL 范围。
 */
@RestController("adminMessageController")
@RequestMapping("/admin/messages")
@RequiredArgsConstructor
public class MessageController {

    private final IMessageService messages;
    private final AuthProperties auth;

    /** 分页查询后台留言或一层回复，parentId为0查根节点，未传保留平铺；总数使用相同筛选。 */
    @GetMapping
    @SaCheckPermission("blog:message:query")
    public Result<PageResult<MessagePrivateVO>> list(@Valid @ModelAttribute MessageAdminQueryDTO query) {
        return Result.ok(messages.adminMessages(query));
    }

    /** 查询整组对话，未传页码时定位当前记录。 */
    @GetMapping("/{id}/conversation")
    @SaCheckPermission("blog:message:query")
    public Result<DiscussionConversationVO<MessagePrivateVO>> conversation(@PathVariable String id,
            @Valid @ModelAttribute DiscussionConversationQueryDTO query) {
        return Result.ok(messages.conversation(IdUtils.parse(id), query));
    }

    /** 根据id查询留言详情，查询权限不能代替审核权限。 */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:message:query")
    public ResponseEntity<Result<MessagePrivateVO>> detail(@PathVariable String id) {
        return response(messages.adminDetail(IdUtils.parse(id)));
    }

    /** 批量审核留言，单条也走此接口，业务层统一检查权限、版本并处理失败。 */
    @PutMapping("/batch-review")
    @SaCheckPermission("blog:message:review")
    public Result<BatchVersionsVO> review(@Valid @RequestBody BatchStatusDTO body) {
        return Result.ok(messages.reviewBatch(body.getItems(), body.getStatus()));
    }

    /** 批量删除留言及全部子回复，单条也走此接口，业务层统一检查权限、版本并处理失败。 */
    @PostMapping("/batch-delete")
    @SaCheckPermission("blog:message:moderate")
    public Result<Void> delete(@Valid @RequestBody BatchIdsDTO body) {
        messages.deleteBatch(body.ids());
        return Result.ok(null);
    }

    /** 在后台回复留言，检查登录、审核权限和请求频率，不要求图形验证码。 */
    @PostMapping("/{id}/replies")
    @SaCheckPermission("blog:message:review")
    public ResponseEntity<Result<MessagePrivateVO>> reply(@PathVariable String id,
            @Valid @RequestBody MessageEditDTO request, HttpServletRequest httpRequest) {
        return ResponseEntity.status(201).body(Result.ok(messages.replyAdmin(IdUtils.parse(id), request,
                IpUtils.clientIp(httpRequest, auth.getTrustedProxies()), httpRequest.getHeader("User-Agent"))));
    }

    /** 返回后台留言资料，版本只放在响应头。 */
    private ResponseEntity<Result<MessagePrivateVO>> response(ResourceVersion<MessagePrivateVO> result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.value()));
    }
}
