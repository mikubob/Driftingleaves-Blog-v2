package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.BatchBlockDTO;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.VisitorQueryDTO;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IVisitorService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.VisitorVO;
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
 * 后台访客管理接口，只管理统计标识，不改变用户、阅读或互动权限。
 */
@RestController
@RequestMapping("/admin/visitors")
@RequiredArgsConstructor
public class VisitorController {

    private final IVisitorService visitors;

    /** 分页查询访客资料及浏览次数，需要 ALL 范围。 */
    @GetMapping
    @SaCheckPermission("blog:visitor:query")
    public Result<PageResult<VisitorVO>> list(@Valid @ModelAttribute VisitorQueryDTO query) {
        return Result.ok(visitors.listVisitors(query));
    }

    /** 根据id查询访客详情，最近访问变化不影响访问限制的版本。 */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:visitor:query")
    public ResponseEntity<Result<VisitorVO>> detail(@PathVariable String id) {
        return response(visitors.detail(IdUtils.parse(id)));
    }

    /** 批量限制访客访问，单条也走此接口，业务层统一检查权限、版本并处理失败。 */
    @PutMapping("/batch-block")
    @SaCheckPermission("blog:visitor:block")
    public Result<BatchVersionsVO> block(@Valid @RequestBody BatchBlockDTO body) {
        return Result.ok(visitors.blockBatch(body.getItems(), body.getBlockedUntil()));
    }

    /** 批量解除访客限制，单条也走此接口，业务层统一检查权限、版本并处理失败。 */
    @PutMapping("/batch-unblock")
    @SaCheckPermission("blog:visitor:unblock")
    public Result<BatchVersionsVO> unblock(@Valid @RequestBody BatchRevisionDTO body) {
        return Result.ok(visitors.unblockBatch(body.getItems()));
    }

    /** 批量删除访客，单条也走此接口，业务层统一检查权限、版本并处理失败。 */
    @PostMapping("/batch-delete")
    @SaCheckPermission("blog:visitor:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        visitors.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /** 返回访客资料，版本只放在 X-Revision 响应头。 */
    private ResponseEntity<Result<VisitorVO>> response(ResourceVersion<VisitorVO> result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.value()));
    }
}
