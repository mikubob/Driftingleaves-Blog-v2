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
 * 本人邮件订阅接口，用户取当前会话，不接受 userId 参数。
 */
@RestController("meSubscriptionController")
@RequestMapping("/blog/me/rss-subscription")
@RequiredArgsConstructor
public class MySubscriptionController {

    private final ISubscriptionService subscriptions;

    /** 查询本人订阅，未申请时返回 null，不返回票据。 */
    @GetMapping
    public Result<SubscriptionVO> own() {
        return Result.ok(subscriptions.own());
    }

    /** 申请订阅或重发确认邮件，同账号和可信 IP 共用每分钟五次限制。 */
    @PutMapping
    @SaCheckPermission("blog:rss:subscribe")
    @RateLimit(value = "subscription-request", limit = 5, dimension = RateLimit.Dimension.USER)
    public Result<SubscriptionVO> request(@Valid @RequestBody SubscriptionWriteDTO request) {
        return Result.ok(subscriptions.request(request));
    }

    /** 确认订阅，当前会话必须属于申请邮件的用户。 */
    @PostMapping("/confirm")
    @SaCheckPermission("blog:rss:subscribe")
    @RateLimit(value = "subscription-confirm", limit = 10, dimension = RateLimit.Dimension.USER)
    public Result<SubscriptionVO> confirm(@Valid @RequestBody SubscriptionTicketDTO request) {
        return Result.ok(subscriptions.confirm(request.ticket()));
    }

    /** 取消本人订阅，不存在或已退订时也返回 200。 */
    @DeleteMapping
    @SaCheckPermission("blog:rss:unsubscribe")
    public Result<Void> cancel() {
        subscriptions.cancelOwn();
        return Result.ok(null);
    }
}
