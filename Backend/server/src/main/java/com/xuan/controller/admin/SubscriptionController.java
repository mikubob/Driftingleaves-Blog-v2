package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.SubscriptionQueryDTO;
import com.xuan.dto.SubscriptionStatusDTO;
import com.xuan.entity.BlogRssSubscription;
import com.xuan.model.ResourceVersion;
import com.xuan.result.Result;
import com.xuan.result.PageResult;
import com.xuan.service.ISubscriptionService;
import com.xuan.utils.IdUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ModelAttribute;


/** 订阅后台按独立ALL权限查询、管理状态和删除，不能代替邮箱确认。 */
@RestController("adminSubscriptionController")
@RequestMapping("/admin/rss-subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {
    private final ISubscriptionService subscriptions;
    /** 无全量邮箱导出。 */
    @GetMapping
    @SaCheckPermission("blog:rss:query")
    public Result<PageResult<BlogRssSubscription>> list(@Valid @ModelAttribute SubscriptionQueryDTO query) { return Result.ok(subscriptions.listSubscriptions(query)); }
    /** 可编辑状态的版本放响应头。 */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:rss:query")
    public ResponseEntity<Result<BlogRssSubscription>> detail(@PathVariable String id) { return response(subscriptions.detail(IdUtils.parse(id))); }
    /** 待验证不能由后台直接激活。 */
    @PutMapping("/{id}/status")
    @SaCheckPermission("blog:rss:update")
    public ResponseEntity<Result<BlogRssSubscription>> status(@PathVariable String id, @Valid @RequestBody SubscriptionStatusDTO request,
            @RequestHeader(value = "If-Revision", required = false) String revision) { return response(subscriptions.changeStatus(IdUtils.parse(id), request.status(), revision)); }
    /** 删除后旧邮件链接全部失效。 */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:rss:delete")
    public Result<Void> delete(@PathVariable String id, @RequestHeader(value = "If-Revision", required = false) String revision) {
        subscriptions.delete(IdUtils.parse(id), revision); return Result.ok(null);
    }
    /** 版本不写到响应体中。 */
    private ResponseEntity<Result<BlogRssSubscription>> response(ResourceVersion<BlogRssSubscription> result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.value()));
    }
}
