package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.constant.HttpConstants;
import com.xuan.dto.BatchBinaryStatusDTO;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.PermissionCreateDTO;
import com.xuan.dto.PermissionQueryDTO;
import com.xuan.dto.PermissionUpdateDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IPermissionService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.PermissionProfileVO;
import com.xuan.vo.PermissionVO;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 管理端权限接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final IPermissionService permissionService;

    /**
     * 分页查询权限列表
     *
     * @param query 查询参数
     * @return 权限分页结果
     */
    @GetMapping
    @SaCheckPermission("sys:permission:query")
    public Result<PageResult<PermissionVO>> list(@Valid @ModelAttribute PermissionQueryDTO query) {
        log.info("分页查询权限列表: {}", query);
        PageResult<PermissionVO> pageResult = permissionService.listPermissions(query);
        return Result.ok(pageResult);
    }

    /**
     * 根据 ID 查询权限详情
     *
     * @param permissionId 权限 ID
     * @return 权限详情及 X-Revision 响应头
     */
    @GetMapping("/{permissionId}")
    @SaCheckPermission("sys:permission:query")
    public ResponseEntity<Result<PermissionVO>> detail(@PathVariable String permissionId) {
        log.info("根据 ID 查询权限详情: permissionId={}", permissionId);
        PermissionProfileVO profile = permissionService.detail(IdUtils.parse(permissionId));
        return profileResponse(profile);
    }

    /**
     * 新增权限
     *
     * @param body 权限创建参数
     * @return 201 响应及详情 Location
     */
    @PostMapping
    @SaCheckPermission("sys:permission:create")
    public ResponseEntity<Result<PermissionVO>> create(@Valid @RequestBody PermissionCreateDTO body) {
        log.info("新增权限: permissionCode={}", body.getPermissionCode());
        PermissionVO permission = permissionService.create(body);
        URI location = URI.create("/api/v1/admin/permissions/" + permission.getPermissionId());
        return ResponseEntity.created(location).body(Result.ok(permission));
    }

    /**
     * 根据 ID 更新权限
     *
     * @param permissionId 权限 ID
     * @param body 权限更新参数
     * @param revision 请求头携带的 If-Revision 版本
     * @return 更新后的权限详情及新 X-Revision 响应头
     */
    @PutMapping("/{permissionId}")
    @SaCheckPermission("sys:permission:update")
    public ResponseEntity<Result<PermissionVO>> update(@PathVariable String permissionId,
            @Valid @RequestBody PermissionUpdateDTO body,
            @RequestHeader(value = HttpConstants.HEADER_IF_REVISION, required = false) String revision) {
        log.info("更新权限: permissionId={}", permissionId);
        PermissionProfileVO profile = permissionService.update(IdUtils.parse(permissionId), body, revision);
        return profileResponse(profile);
    }

    /**
     * 批量修改权限状态
     *
     * @param body 批量状态变更参数
     * @return 更新后的批量版本信息
     */
    @PutMapping("/batch-status")
    @SaCheckPermission("sys:permission:update")
    public Result<BatchVersionsVO> status(@Valid @RequestBody BatchBinaryStatusDTO body) {
        log.info("批量修改权限状态: count={}, status={}", body.getItems().size(), body.getStatus());
        BatchVersionsVO versions = permissionService.changeStatusBatch(body.getItems(), body.getStatus());
        return Result.ok(versions);
    }

    /**
     * 批量删除权限
     *
     * @param body 批量操作参数
     * @return 成功结果
     */
    @PostMapping("/batch-delete")
    @SaCheckPermission("sys:permission:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        log.info("批量删除权限: count={}", body.getItems().size());
        permissionService.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /**
     * 组装权限详情响应，版本放在 X-Revision 响应头。
     *
     * @param result 权限详情与版本
     * @return 200 响应
     */
    private ResponseEntity<Result<PermissionVO>> profileResponse(PermissionProfileVO result) {
        return ResponseEntity.ok()
                .header(HttpConstants.HEADER_X_REVISION, result.getRevision())
                .body(Result.ok(result.getPermission()));
    }
}
