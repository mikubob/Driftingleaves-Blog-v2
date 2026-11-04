package com.xuan.controller.blog;

import cn.dev33.satoken.annotation.SaCheckPermission;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本人留言接口，只操作当前用户的留言，有 ALL 范围也不能读取他人的本人详情。
 */
@RestController("meMessageController")
@RequestMapping("/blog/me/messages")
@RequiredArgsConstructor
public class MyMessageController {

    private final IMessageService messages;

    /** 分页查询本人留言，包括待审、驳回和私密记录。 */
    @GetMapping
    public Result<PageResult<MessagePrivateVO>> list(@Valid @ModelAttribute MessageOwnQueryDTO query) {
        return Result.ok(messages.ownMessages(query));
    }

    /** 根据id查询本人留言及版本。 */
    @GetMapping("/{id}")
    public ResponseEntity<Result<MessagePrivateVO>> detail(@PathVariable String id) {
        return response(messages.ownDetail(IdUtils.parse(id)));
    }

    /** 删除本人留言及其全部子回复，不需要版本。 */
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
