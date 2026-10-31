package com.xuan.controller.me;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.MessageEditDTO;
import com.xuan.dto.MessageOwnQueryDTO;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IMessageService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.MessagePrivateVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本人留言只操作当前user_id，ALL也不能读取他人本人详情。
 */
@RestController("meMessageController")
@RequestMapping("/me/messages")
@RequiredArgsConstructor
public class MessageController {

    private final IMessageService messages;

    /** 本人保留待审、驳回、私密与占位记录。 */
    @GetMapping
    public Result<PageResult<MessagePrivateVO>> list(@Valid @ModelAttribute MessageOwnQueryDTO query) {
        return Result.ok(messages.ownMessages(query));
    }

    /** 编辑前获取当前业务版本。 */
    @GetMapping("/{id}")
    public ResponseEntity<Result<MessagePrivateVO>> detail(@PathVariable String id) {
        return response(messages.ownDetail(IdUtils.parse(id)));
    }

    /** 编辑只能替换正文、格式和通知意愿，并重新审核。 */
    @PutMapping("/{id}")
    @SaCheckPermission("blog:message:updateOwn")
    public ResponseEntity<Result<MessagePrivateVO>> edit(@PathVariable String id,
            @Valid @RequestBody MessageEditDTO request,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return response(messages.edit(IdUtils.parse(id), request, revision));
    }

    /** 删除不需要版本，重复删除保持占位。 */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:message:deleteOwn")
    public Result<Void> delete(@PathVariable String id) {
        messages.deleteOwn(IdUtils.parse(id));
        return Result.ok(null);
    }

    /** 版本只放响应头。 */
    private ResponseEntity<Result<MessagePrivateVO>> response(ResourceVersion<MessagePrivateVO> result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.value()));
    }
}
