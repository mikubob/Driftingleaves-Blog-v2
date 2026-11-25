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
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本人邮件订阅接口
 */
@Slf4j
@RestController("meSubscriptionController")
@RequestMapping("/blog/me/rss-subscription")
@RequiredArgsConstructor
public class MySubscriptionController {

    private final ISubscriptionService subscriptionService;

    /**
     * 查询本人订阅状态
     *
     * @return 订阅信息，未申请时返回 null
     */
    @GetMapping
    public Result<SubscriptionVO> own() {
        log.info("查询本人邮件订阅状态");
        SubscriptionVO subscription = subscriptionService.own();
        return Result.ok(subscription);
    }

    /**
     * 申请订阅或重发确认邮件
     *
     * @param request 订阅参数
     * @return 订阅信息
     */
    @PutMapping
    @SaCheckPermission("blog:rss:subscribe")
    @RateLimit(value = "subscription-request", limit = 5, dimension = RateLimit.Dimension.USER)
    public Result<SubscriptionVO> request(@Valid @RequestBody SubscriptionWriteDTO request) {
        log.info("申请邮件订阅");
        SubscriptionVO subscription = subscriptionService.request(request);
        return Result.ok(subscription);
    }

    /**
     * 确认本人邮件订阅
     *
     * @param request 订阅确认票据参数
     * @return 确认后的订阅信息
     */
    @PostMapping("/confirm")
    @SaCheckPermission("blog:rss:subscribe")
    @RateLimit(value = "subscription-confirm", limit = 10, dimension = RateLimit.Dimension.USER)
    public Result<SubscriptionVO> confirm(@Valid @RequestBody SubscriptionTicketDTO request) {
        log.info("确认本人邮件订阅");
        SubscriptionVO subscription = subscriptionService.confirm(request.getTicket());
        return Result.ok(subscription);
    }

    /**
     * 取消本人邮件订阅
     *
     * @return 成功结果
     */
    @DeleteMapping
    @SaCheckPermission("blog:rss:unsubscribe")
    public Result<Void> cancel() {
        log.info("取消本人邮件订阅");
        subscriptionService.cancelOwn();
        return Result.ok(null);
    }
}
