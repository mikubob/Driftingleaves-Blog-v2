package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.BatchBlockDTO;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.VisitorQueryDTO;
import com.xuan.entity.BlogVisitor;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IVisitorService;
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

    /** 单个和多个目标共用入口，权限、版本及失败结果由业务层统一处理。 */
    @PutMapping("/batch-block")
    @SaCheckPermission("blog:visitor:block")
    public Result<BatchVersionsVO> block(@Valid @RequestBody BatchBlockDTO body) {
        return Result.ok(visitors.blockBatch(body.getItems(), body.getBlockedUntil()));
    }

    /** 单个和多个目标共用入口，权限、版本及失败结果由业务层统一处理。 */
    @PutMapping("/batch-unblock")
    @SaCheckPermission("blog:visitor:unblock")
    public Result<BatchVersionsVO> unblock(@Valid @RequestBody BatchRevisionDTO body) {
        return Result.ok(visitors.unblockBatch(body.getItems()));
    }

    /** 单个和多个目标共用入口，权限、版本及失败结果由业务层统一处理。 */
    @PostMapping("/batch-delete")
    @SaCheckPermission("blog:visitor:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        visitors.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /** 版本只放响应头。 */
    private ResponseEntity<Result<BlogVisitor>> response(ResourceVersion<BlogVisitor> result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.value()));
    }
}
