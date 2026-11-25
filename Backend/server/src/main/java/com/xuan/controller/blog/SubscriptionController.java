package com.xuan.controller.blog;

import com.xuan.annotation.RateLimit;
import com.xuan.dto.SubscriptionTicketDTO;
import com.xuan.result.Result;
import com.xuan.service.ISubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 博客端邮件退订接口
 */
@Slf4j
@RestController("blogSubscriptionController")
@RequestMapping("/blog/rss-subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final ISubscriptionService subscriptionService;

    /**
     * 根据邮件票据取消订阅
     *
     * @param request 退订票据参数
     * @return 成功结果
     */
    @PostMapping("/unsubscribe")
    @RateLimit(value = "subscription-unsubscribe", limit = 30)
    public Result<Void> cancel(@Valid @RequestBody SubscriptionTicketDTO request) {
        log.info("根据邮件票据取消订阅");
        subscriptionService.cancelTicket(request.getTicket());
        return Result.ok(null);
    }
}
