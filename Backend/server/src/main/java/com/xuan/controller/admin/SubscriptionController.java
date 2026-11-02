package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
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
 * 后台邮件订阅接口，查询、修改状态和删除分别要求 ALL 范围，不能代替邮箱确认。
 */
@RestController("adminSubscriptionController")
@RequestMapping("/admin/rss-subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final ISubscriptionService subscriptions;

    /** 分页查询订阅，不提供全量邮箱导出。 */
    @GetMapping
    @SaCheckPermission("blog:rss:query")
    public Result<PageResult<BlogRssSubscription>> list(@Valid @ModelAttribute SubscriptionQueryDTO query) {
        return Result.ok(subscriptions.listSubscriptions(query));
    }

    /** 根据id查询订阅详情，状态版本放在 X-Revision 响应头。 */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:rss:query")
    public ResponseEntity<Result<BlogRssSubscription>> detail(@PathVariable String id) {
        return response(subscriptions.detail(IdUtils.parse(id)));
    }

    /** 批量修改订阅状态，单条也走此接口，业务层统一检查权限、版本并处理失败。 */
    @PutMapping("/batch-status")
    @SaCheckPermission("blog:rss:update")
    public Result<BatchVersionsVO> status(@Valid @RequestBody BatchStatusDTO body) {
        return Result.ok(subscriptions.changeStatusBatch(body.getItems(), body.getStatus()));
    }

    /** 批量删除订阅，单条也走此接口，业务层统一检查权限、版本并处理失败。 */
    @PostMapping("/batch-delete")
    @SaCheckPermission("blog:rss:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        subscriptions.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /** 返回订阅资料，版本只放在响应头。 */
    private ResponseEntity<Result<BlogRssSubscription>> response(ResourceVersion<BlogRssSubscription> result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.value()));
    }
}
