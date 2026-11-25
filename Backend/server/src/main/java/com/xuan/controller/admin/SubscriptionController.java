package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.constant.HttpConstants;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.BatchStatusDTO;
import com.xuan.dto.SubscriptionQueryDTO;
import com.xuan.entity.BlogRssSubscription;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ISubscriptionService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.BatchVersionsVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端邮件订阅接口
 */
@Slf4j
@RestController("adminSubscriptionController")
@RequestMapping("/admin/rss-subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final ISubscriptionService subscriptionService;

    /**
     * 分页查询邮件订阅列表
     *
     * @param query 查询参数
     * @return 订阅分页结果
     */
    @GetMapping
    @SaCheckPermission("blog:rss:query")
    public Result<PageResult<BlogRssSubscription>> list(@Valid @ModelAttribute SubscriptionQueryDTO query) {
        log.info("分页查询邮件订阅列表: {}", query);
        PageResult<BlogRssSubscription> pageResult = subscriptionService.listSubscriptions(query);
        return Result.ok(pageResult);
    }

    /**
     * 根据 ID 查询订阅详情
     *
     * @param id 订阅 ID
     * @return 订阅详情及 X-Revision 响应头
     */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:rss:query")
    public ResponseEntity<Result<BlogRssSubscription>> detail(@PathVariable String id) {
        log.info("根据 ID 查询订阅详情: id={}", id);
        ResourceVersion<BlogRssSubscription> result = subscriptionService.detail(IdUtils.parse(id));
        return versionResponse(result);
    }

    /**
     * 批量修改订阅状态
     *
     * @param body 批量状态变更参数
     * @return 更新后的批量版本信息
     */
    @PutMapping("/batch-status")
    @SaCheckPermission("blog:rss:update")
    public Result<BatchVersionsVO> status(@Valid @RequestBody BatchStatusDTO body) {
        log.info("批量修改订阅状态: count={}, status={}", body.getItems().size(), body.getStatus());
        BatchVersionsVO versions = subscriptionService.changeStatusBatch(body.getItems(), body.getStatus());
        return Result.ok(versions);
    }

    /**
     * 批量删除订阅
     *
     * @param body 批量删除参数
     * @return 成功结果
     */
    @PostMapping("/batch-delete")
    @SaCheckPermission("blog:rss:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        log.info("批量删除订阅: count={}", body.getItems().size());
        subscriptionService.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /**
     * 组装带版本头的订阅详情响应。
     *
     * @param result 订阅详情与版本
     * @return 200 响应
     */
    private ResponseEntity<Result<BlogRssSubscription>> versionResponse(ResourceVersion<BlogRssSubscription> result) {
        return ResponseEntity.ok()
                .header(HttpConstants.HEADER_X_REVISION, result.revision())
                .body(Result.ok(result.value()));
    }
}
