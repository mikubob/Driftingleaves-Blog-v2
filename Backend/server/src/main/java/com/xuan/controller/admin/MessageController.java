package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.constant.HttpConstants;
import com.xuan.dto.BatchIdsDTO;
import com.xuan.dto.BatchStatusDTO;
import com.xuan.dto.DiscussionConversationQueryDTO;
import com.xuan.dto.MessageAdminQueryDTO;
import com.xuan.dto.MessageEditDTO;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IMessageService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.DiscussionConversationVO;
import com.xuan.vo.MessagePrivateVO;
import jakarta.servlet.http.HttpServletRequest;
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
 * 管理端留言接口
 */
@Slf4j
@RestController("adminMessageController")
@RequestMapping("/admin/messages")
@RequiredArgsConstructor
public class MessageController {

    private final IMessageService messageService;

    /**
     * 分页查询后台留言列表
     *
     * @param query 查询参数
     * @return 留言分页结果
     */
    @GetMapping
    @SaCheckPermission("blog:message:query")
    public Result<PageResult<MessagePrivateVO>> list(@Valid @ModelAttribute MessageAdminQueryDTO query) {
        log.info("分页查询后台留言列表: {}", query);
        PageResult<MessagePrivateVO> pageResult = messageService.adminMessages(query);
        return Result.ok(pageResult);
    }

    /**
     * 查询留言整组对话
     *
     * @param id 留言 ID
     * @param query 对话分页参数
     * @return 对话详情
     */
    @GetMapping("/{id}/conversation")
    @SaCheckPermission("blog:message:query")
    public Result<DiscussionConversationVO<MessagePrivateVO>> conversation(@PathVariable String id,
            @Valid @ModelAttribute DiscussionConversationQueryDTO query) {
        log.info("查询后台留言对话: id={}", id);
        DiscussionConversationVO<MessagePrivateVO> conversation = messageService.conversation(IdUtils.parse(id), query);
        return Result.ok(conversation);
    }

    /**
     * 根据 ID 查询留言详情
     *
     * @param id 留言 ID
     * @return 留言详情及 X-Revision 响应头
     */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:message:query")
    public ResponseEntity<Result<MessagePrivateVO>> detail(@PathVariable String id) {
        log.info("根据 ID 查询后台留言详情: id={}", id);
        ResourceVersion<MessagePrivateVO> result = messageService.adminDetail(IdUtils.parse(id));
        return versionResponse(result);
    }

    /**
     * 批量审核留言
     *
     * @param body 批量审核参数
     * @return 更新后的批量版本信息
     */
    @PutMapping("/batch-review")
    @SaCheckPermission("blog:message:review")
    public Result<BatchVersionsVO> review(@Valid @RequestBody BatchStatusDTO body) {
        log.info("批量审核留言: count={}, status={}", body.getItems().size(), body.getStatus());
        BatchVersionsVO versions = messageService.reviewBatch(body.getItems(), body.getStatus());
        return Result.ok(versions);
    }

    /**
     * 批量删除留言及子回复
     *
     * @param body 批量删除参数
     * @return 成功结果
     */
    @PostMapping("/batch-delete")
    @SaCheckPermission("blog:message:moderate")
    public Result<Void> delete(@Valid @RequestBody BatchIdsDTO body) {
        log.info("批量删除留言: count={}", body.ids().size());
        messageService.deleteBatch(body.ids());
        return Result.ok(null);
    }

    /**
     * 后台回复留言
     *
     * @param id 目标留言 ID
     * @param request 回复内容参数
     * @param httpRequest 当前 HTTP 请求
     * @return 201 响应及新建回复资料
     */
    @PostMapping("/{id}/replies")
    @SaCheckPermission("blog:message:review")
    public ResponseEntity<Result<MessagePrivateVO>> reply(@PathVariable String id,
            @Valid @RequestBody MessageEditDTO request,
            HttpServletRequest httpRequest) {
        log.info("后台回复留言: id={}", id);
        MessagePrivateVO reply = messageService.replyAdmin(IdUtils.parse(id), request, httpRequest);
        return ResponseEntity.status(201).body(Result.ok(reply));
    }

    /**
     * 组装带版本头的留言详情响应。
     *
     * @param result 留言详情与版本
     * @return 200 响应
     */
    private ResponseEntity<Result<MessagePrivateVO>> versionResponse(ResourceVersion<MessagePrivateVO> result) {
        return ResponseEntity.ok()
                .header(HttpConstants.HEADER_X_REVISION, result.revision())
                .body(Result.ok(result.value()));
    }
}
