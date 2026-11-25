package com.xuan.controller.blog;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.constant.HttpConstants;
import com.xuan.dto.MessageOwnQueryDTO;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IMessageService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.MessagePrivateVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本人留言管理接口
 */
@Slf4j
@RestController("meMessageController")
@RequestMapping("/blog/me/messages")
@RequiredArgsConstructor
public class MyMessageController {

    private final IMessageService messageService;

    /**
     * 分页查询本人留言列表
     *
     * @param query 查询参数
     * @return 本人留言分页结果
     */
    @GetMapping
    public Result<PageResult<MessagePrivateVO>> list(@Valid @ModelAttribute MessageOwnQueryDTO query) {
        log.info("分页查询本人留言列表: {}", query);
        PageResult<MessagePrivateVO> pageResult = messageService.ownMessages(query);
        return Result.ok(pageResult);
    }

    /**
     * 根据 ID 查询本人留言详情
     *
     * @param id 留言 ID
     * @return 本人留言详情及 X-Revision 响应头
     */
    @GetMapping("/{id}")
    public ResponseEntity<Result<MessagePrivateVO>> detail(@PathVariable String id) {
        log.info("根据 ID 查询本人留言详情: id={}", id);
        ResourceVersion<MessagePrivateVO> result = messageService.ownDetail(IdUtils.parse(id));
        return versionResponse(result);
    }

    /**
     * 删除本人留言及其子回复
     *
     * @param id 留言 ID
     * @return 成功结果
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:message:deleteOwn")
    public Result<Void> delete(@PathVariable String id) {
        log.info("删除本人留言: id={}", id);
        messageService.deleteOwn(IdUtils.parse(id));
        return Result.ok(null);
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
