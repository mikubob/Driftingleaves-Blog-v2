package com.xuan.controller.blog;

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
 * 邮件链接只携带作用域受限的退订票据，GET不改变状态。
 */
@RestController("blogSubscriptionController")
@RequestMapping("/blog/rss-subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final ISubscriptionService subscriptions;

    /** 只允许退订票据绑定的同一条订阅及轮次。 */
    @PostMapping("/unsubscribe")
    public Result<Void> cancel(@Valid @RequestBody SubscriptionTicketDTO request) {
        subscriptions.cancelTicket(request.ticket());
        return Result.ok(null);
    }
}
