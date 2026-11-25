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
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端操作日志接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/operation-logs")
@RequiredArgsConstructor
public class OperationLogController {

    private final IOperationLogService operationLogService;

    /**
     * 分页查询操作日志列表
     *
     * @param query 查询参数
     * @return 操作日志分页结果
     */
    @GetMapping
    @SaCheckPermission("sys:log:query")
    public Result<PageResult<OperationLogVO>> list(@Valid @ModelAttribute OperationLogQueryDTO query) {
        log.info("分页查询操作日志列表: {}", query);
        PageResult<OperationLogVO> pageResult = operationLogService.listLogs(query);
        return Result.ok(pageResult);
    }

    /**
     * 根据 ID 查询操作日志详情
     *
     * @param id 日志 ID
     * @return 操作日志详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("sys:log:query")
    public Result<OperationLogDetailVO> detail(@PathVariable String id) {
        log.info("根据 ID 查询操作日志详情: id={}", id);
        OperationLogDetailVO detail = operationLogService.detail(IdUtils.parse(id));
        return Result.ok(detail);
    }

    /**
     * 批量删除操作日志
     *
     * @param body 批量删除参数
     * @return 成功结果
     */
    @PostMapping("/batch-delete")
    @SaCheckPermission("sys:log:delete")
    public Result<Void> delete(@Valid @RequestBody BatchIdsDTO body) {
        log.info("批量删除操作日志: count={}", body.ids().size());
        operationLogService.deleteBatch(body.ids());
        return Result.ok(null);
    }
}
