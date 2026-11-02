package com.xuan.controller.blog;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.annotation.RateLimit;
import com.xuan.dto.MessageCreateDTO;
import com.xuan.dto.MessagePublicQueryDTO;
import com.xuan.properties.AuthProperties;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IMessageService;
import com.xuan.store.CaptchaStore;
import com.xuan.utils.IpUtils;
import com.xuan.vo.MessagePrivateVO;
import com.xuan.vo.MessagePublicVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台会话只影响本人/私密可见性，管理员仍需从后台路径管理。
 */
@RestController("blogMessageController")
@RequestMapping("/blog/messages")
@RequiredArgsConstructor
public class MessageController {

    private final IMessageService messages;
    private final CaptchaStore captchas;
    private final AuthProperties auth;

    /** 默认根节点，每次只展开一层，total不计不可见内容。 */
    @GetMapping
    public Result<PageResult<MessagePublicVO>> list(@Valid @ModelAttribute MessagePublicQueryDTO query) {
        return Result.ok(messages.publicMessages(query));
    }

    /** 普通前台发表每用户和可信IP一分钟十次，不自动给管理员免审。 */
    @PostMapping
    @SaCheckPermission("blog:message:create")
    @RateLimit(value = "message-create", limit = 10, dimension = RateLimit.Dimension.USER)
    public ResponseEntity<Result<MessagePrivateVO>> create(@Valid @RequestBody MessageCreateDTO request,
            HttpServletRequest httpRequest) {
        captchas.verify(httpRequest.getHeader("X-Captcha-Id"), httpRequest.getHeader("X-Captcha-Code"));
        return ResponseEntity.status(201).body(Result.ok(messages.create(request,
                IpUtils.clientIp(httpRequest, auth.getTrustedProxies()), httpRequest.getHeader("User-Agent"))));
    }
}
