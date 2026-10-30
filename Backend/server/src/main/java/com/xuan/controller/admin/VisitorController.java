package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.VisitorBlockDTO;
import com.xuan.dto.VisitorQueryDTO;
import com.xuan.entity.BlogVisitor;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IVisitorService;
import com.xuan.utils.IdUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计访客仅用于统计治理，不影响用户、阅读或互动权限。
 */
@RestController
@RequestMapping("/admin/visitors")
@RequiredArgsConstructor
public class VisitorController {

    private final IVisitorService visitors;

    /** 只在ALL后台读取脱敏IP和UA。 */
    @GetMapping
    @SaCheckPermission("blog:visitor:query")
    public Result<PageResult<BlogVisitor>> list(@Valid @ModelAttribute VisitorQueryDTO query) {
        return Result.ok(visitors.listVisitors(query));
    }

    /** 最近访问变化不参与限制字段的版本。 */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:visitor:query")
    public ResponseEntity<Result<BlogVisitor>> detail(@PathVariable String id) {
        return response(visitors.detail(IdUtils.parse(id)));
    }

    /** null表示永久，字段必须显式出现。 */
    @PutMapping("/{id}/block")
    @SaCheckPermission("blog:visitor:block")
    public ResponseEntity<Result<BlogVisitor>> block(@PathVariable String id, @Valid @RequestBody VisitorBlockDTO body,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return response(visitors.block(IdUtils.parse(id), body.getBlockedUntil(), revision));
    }

    /** 无请求体，相同目标也校验版本。 */
    @PutMapping("/{id}/unblock")
    @SaCheckPermission("blog:visitor:unblock")
    public ResponseEntity<Result<BlogVisitor>> unblock(@PathVariable String id,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return response(visitors.unblock(IdUtils.parse(id), revision));
    }

    /** 清理访客保留PV，UV随外键置NULL可能下降。 */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:visitor:delete")
    public Result<Void> delete(@PathVariable String id,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        visitors.delete(IdUtils.parse(id), revision);
        return Result.ok(null);
    }

    /** 版本只放响应头。 */
    private ResponseEntity<Result<BlogVisitor>> response(ResourceVersion<BlogVisitor> result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.value()));
    }
}
