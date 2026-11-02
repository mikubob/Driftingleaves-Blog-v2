package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.BatchIdsDTO;
import com.xuan.dto.OperationLogQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IOperationLogService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.OperationLogDetailVO;
import com.xuan.vo.OperationLogVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台操作日志接口，只提供查询和清理。
 */
@RestController
@RequestMapping("/admin/operation-logs")
@RequiredArgsConstructor
public class OperationLogController {

    private final IOperationLogService logs;

    /** 分页查询操作日志，列表不返回操作参数摘要。 */
    @GetMapping
    @SaCheckPermission("sys:log:query")
    public Result<PageResult<OperationLogVO>> list(@Valid @ModelAttribute OperationLogQueryDTO query) {
        return Result.ok(logs.listLogs(query));
    }

    /** 根据id查询操作日志详情，记录不可编辑，不返回版本。 */
    @GetMapping("/{id}")
    @SaCheckPermission("sys:log:query")
    public Result<OperationLogDetailVO> detail(@PathVariable String id) {
        return Result.ok(logs.detail(IdUtils.parse(id)));
    }

    /** 批量删除操作日志，单条也走此接口，业务层统一检查权限、版本并处理失败。 */
    @PostMapping("/batch-delete")
    @SaCheckPermission("sys:log:delete")
    public Result<Void> delete(@Valid @RequestBody BatchIdsDTO body) {
        logs.deleteBatch(body.ids());
        return Result.ok(null);
    }
}
