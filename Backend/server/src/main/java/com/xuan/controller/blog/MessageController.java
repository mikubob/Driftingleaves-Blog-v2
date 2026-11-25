package com.xuan.controller.blog;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.annotation.RateLimit;
import com.xuan.dto.MessageCreateDTO;
import com.xuan.dto.MessagePublicQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IMessageService;
import com.xuan.vo.MessagePrivateVO;
import com.xuan.vo.MessagePublicVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 博客端留言接口
 */
@Slf4j
@RestController("blogMessageController")
@RequestMapping("/blog/messages")
@RequiredArgsConstructor
public class MessageController {

    private final IMessageService messageService;

    /**
     * 分页查询前台留言列表
     *
     * @param query 查询参数
     * @return 留言分页结果
     */
    @GetMapping
    public Result<PageResult<MessagePublicVO>> list(@Valid @ModelAttribute MessagePublicQueryDTO query) {
        log.info("分页查询前台留言列表: {}", query);
        PageResult<MessagePublicVO> pageResult = messageService.publicMessages(query);
        return Result.ok(pageResult);
    }

    /**
     * 发表留言
     *
     * @param request 留言创建参数
     * @param httpRequest 当前 HTTP 请求
     * @return 201 响应及新建留言资料
     */
    @PostMapping
    @SaCheckPermission("blog:message:create")
    @RateLimit(value = "message-create", limit = 10, dimension = RateLimit.Dimension.USER)
    public ResponseEntity<Result<MessagePrivateVO>> create(@Valid @RequestBody MessageCreateDTO request,
            HttpServletRequest httpRequest) {
        log.info("发表前台留言");
        MessagePrivateVO message = messageService.createWithCaptcha(request, httpRequest);
        return ResponseEntity.status(201).body(Result.ok(message));
    }
}
