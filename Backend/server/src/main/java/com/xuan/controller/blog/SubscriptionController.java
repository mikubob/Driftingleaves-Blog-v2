package com.xuan.controller.blog;

import com.xuan.annotation.RateLimit;
import com.xuan.dto.SubscriptionTicketDTO;
import com.xuan.result.Result;
import com.xuan.service.ISubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 邮件退订接口，票据仅用于对应订阅，GET 不修改订阅状态。
 */
@RestController("blogSubscriptionController")
@RequestMapping("/blog/rss-subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final ISubscriptionService subscriptions;

    /** 根据邮件票据取消订阅，只能取消票据对应的订阅和轮次。 */
    @PostMapping("/unsubscribe")
    @RateLimit(value = "subscription-unsubscribe", limit = 30)
    public Result<Void> cancel(@Valid @RequestBody SubscriptionTicketDTO request) {
        subscriptions.cancelTicket(request.ticket());
        return Result.ok(null);
    }
}
