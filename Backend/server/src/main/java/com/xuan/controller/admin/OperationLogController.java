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
 * 后台日志有查询和清理入口，没有创建或更新入口。
 */
@RestController
@RequestMapping("/admin/operation-logs")
@RequiredArgsConstructor
public class OperationLogController {

    private final IOperationLogService logs;

    /** 列表不返回摘要。 */
    @GetMapping
    @SaCheckPermission("sys:log:query")
    public Result<PageResult<OperationLogVO>> list(@Valid @ModelAttribute OperationLogQueryDTO query) {
        return Result.ok(logs.listLogs(query));
    }

    /** 不可编辑记录不返回版本。 */
    @GetMapping("/{id}")
    @SaCheckPermission("sys:log:query")
    public Result<OperationLogDetailVO> detail(@PathVariable String id) {
        return Result.ok(logs.detail(IdUtils.parse(id)));
    }

    /** 单个和多个目标共用入口，权限、版本及失败结果由业务层统一处理。 */
    @PostMapping("/batch-delete")
    @SaCheckPermission("sys:log:delete")
    public Result<Void> delete(@Valid @RequestBody BatchIdsDTO body) {
        logs.deleteBatch(body.ids());
        return Result.ok(null);
    }
}
