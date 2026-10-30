package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.OperationLogQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IOperationLogService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.OperationLogVO;
import com.xuan.vo.OperationLogDetailVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;


/** 后台日志有查询和清理入口，没有创建或更新入口。 */
@RestController
@RequestMapping("/admin/operation-logs")
@RequiredArgsConstructor
public class OperationLogController {
    private final IOperationLogService logs;
    /** 列表不返回摘要。 */
    @GetMapping
    @SaCheckPermission("sys:log:query")
    public Result<PageResult<OperationLogVO>> list(@Valid @ModelAttribute OperationLogQueryDTO query) { return Result.ok(logs.listLogs(query)); }
    /** 不可编辑记录不返回版本。 */
    @GetMapping("/{id}")
    @SaCheckPermission("sys:log:query")
    public Result<OperationLogDetailVO> detail(@PathVariable String id) { return Result.ok(logs.detail(IdUtils.parse(id))); }
    /** 清理行为会由服务事务新增一条审计。 */
    @DeleteMapping("/{id}")
    @SaCheckPermission("sys:log:delete")
    public Result<Void> delete(@PathVariable String id) { logs.delete(IdUtils.parse(id)); return Result.ok(null); }
}
