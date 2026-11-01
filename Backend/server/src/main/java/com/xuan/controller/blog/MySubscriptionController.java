package com.xuan.controller.blog;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.annotation.RateLimit;
import com.xuan.dto.SubscriptionTicketDTO;
import com.xuan.dto.SubscriptionWriteDTO;
import com.xuan.result.Result;
import com.xuan.service.ISubscriptionService;
import com.xuan.vo.SubscriptionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本人订阅操作只使用当前会话，不从请求接收userId。
 */
@RestController("meSubscriptionController")
@RequestMapping("/blog/me/rss-subscription")
@RequiredArgsConstructor
public class MySubscriptionController {

    private final ISubscriptionService subscriptions;

    /** 未申请返回null，不返回任何票据。 */
    @GetMapping
    public Result<SubscriptionVO> own() {
        return Result.ok(subscriptions.own());
    }

    /** 同账号和可信IP共用每分钟5次申请/重发限制。 */
    @PutMapping
    @SaCheckPermission("blog:rss:subscribe")
    @RateLimit(value = "subscription-request", limit = 5, dimension = RateLimit.Dimension.USER)
    public Result<SubscriptionVO> request(@Valid @RequestBody SubscriptionWriteDTO request) {
        return Result.ok(subscriptions.request(request));
    }

    /** Bearer必须属于邮件申请的同一用户。 */
    @PostMapping("/confirm")
    @SaCheckPermission("blog:rss:subscribe")
    @RateLimit(value = "subscription-confirm", limit = 10, dimension = RateLimit.Dimension.USER)
    public Result<SubscriptionVO> confirm(@Valid @RequestBody SubscriptionTicketDTO request) {
        return Result.ok(subscriptions.confirm(request.ticket()));
    }

    /** 退订不存在或已退订均幂等200。 */
    @DeleteMapping
    @SaCheckPermission("blog:rss:unsubscribe")
    public Result<Void> cancel() {
        subscriptions.cancelOwn();
        return Result.ok(null);
    }
}
