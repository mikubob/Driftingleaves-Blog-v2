package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.constant.HttpConstants;
import com.xuan.dto.BatchBinaryStatusDTO;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.RoleCreateDTO;
import com.xuan.dto.RoleGrantDTO;
import com.xuan.dto.RoleQueryDTO;
import com.xuan.dto.RoleUpdateDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IRoleService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.RoleProfileVO;
import com.xuan.vo.RoleVO;
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
 * 管理端角色接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/roles")
@RequiredArgsConstructor
public class RoleController {

    private final IRoleService roleService;

    /**
     * 分页查询角色列表
     *
     * @param query 查询参数
     * @return 角色分页结果
     */
    @GetMapping
    @SaCheckPermission("sys:role:query")
    public Result<PageResult<RoleVO>> list(@Valid @ModelAttribute RoleQueryDTO query) {
        log.info("分页查询角色列表: {}", query);
        PageResult<RoleVO> pageResult = roleService.listRoles(query);
        return Result.ok(pageResult);
    }

    /**
     * 根据 ID 查询角色详情
     *
     * @param roleId 角色 ID
     * @return 角色详情及 X-Revision 响应头
     */
    @GetMapping("/{roleId}")
    @SaCheckPermission("sys:role:query")
    public ResponseEntity<Result<RoleVO>> detail(@PathVariable String roleId) {
        log.info("根据 ID 查询角色详情: roleId={}", roleId);
        RoleProfileVO profile = roleService.detail(IdUtils.parse(roleId));
        return profileResponse(profile);
    }

    /**
     * 新增角色
     *
     * @param body 角色创建参数
     * @return 201 响应及详情 Location
     */
    @PostMapping
    @SaCheckPermission("sys:role:create")
    public ResponseEntity<Result<RoleVO>> create(@Valid @RequestBody RoleCreateDTO body) {
        log.info("新增角色: roleCode={}", body.getRoleCode());
        RoleVO role = roleService.create(body);
        URI location = URI.create("/api/v1/admin/roles/" + role.roleId());
        return ResponseEntity.created(location).body(Result.ok(role));
    }

    /**
     * 根据 ID 更新角色
     *
     * @param roleId 角色 ID
     * @param body 角色更新参数
     * @param revision 请求头携带的 If-Revision 版本
     * @return 更新后的角色详情及新 X-Revision 响应头
     */
    @PutMapping("/{roleId}")
    @SaCheckPermission("sys:role:update")
    public ResponseEntity<Result<RoleVO>> update(@PathVariable String roleId,
            @Valid @RequestBody RoleUpdateDTO body,
            @RequestHeader(value = HttpConstants.HEADER_IF_REVISION, required = false) String revision) {
        log.info("更新角色: roleId={}", roleId);
        RoleProfileVO profile = roleService.update(IdUtils.parse(roleId), body, revision);
        return profileResponse(profile);
    }

    /**
     * 批量修改角色状态
     *
     * @param body 批量状态变更参数
     * @return 更新后的批量版本信息
     */
    @PutMapping("/batch-status")
    @SaCheckPermission("sys:role:update")
    public Result<BatchVersionsVO> status(@Valid @RequestBody BatchBinaryStatusDTO body) {
        log.info("批量修改角色状态: count={}, status={}", body.getItems().size(), body.getStatus());
        BatchVersionsVO versions = roleService.changeStatusBatch(body.getItems(), body.getStatus());
        return Result.ok(versions);
    }

    /**
     * 批量删除角色
     *
     * @param body 批量操作参数
     * @return 成功结果
     */
    @PostMapping("/batch-delete")
    @SaCheckPermission("sys:role:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        log.info("批量删除角色: count={}", body.getItems().size());
        roleService.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /**
     * 根据 ID 更新角色权限与菜单授权
     *
     * @param roleId 角色 ID
     * @param body 授权参数
     * @param revision 请求头携带的 If-Revision 版本
     * @return 更新后的角色详情及新 X-Revision 响应头
     */
    @PutMapping("/{roleId}/grants")
    @SaCheckPermission("sys:role:grant")
    public ResponseEntity<Result<RoleVO>> grants(@PathVariable String roleId,
            @Valid @RequestBody RoleGrantDTO body,
            @RequestHeader(value = HttpConstants.HEADER_IF_REVISION, required = false) String revision) {
        log.info("更新角色授权: roleId={}", roleId);
        RoleProfileVO profile = roleService.grants(IdUtils.parse(roleId), body, revision);
        return profileResponse(profile);
    }

    /**
     * 组装角色详情响应，版本放在 X-Revision 响应头。
     *
     * @param result 角色详情与版本
     * @return 200 响应
     */
    private ResponseEntity<Result<RoleVO>> profileResponse(RoleProfileVO result) {
        return ResponseEntity.ok()
                .header(HttpConstants.HEADER_X_REVISION, result.revision())
                .body(Result.ok(result.role()));
    }
}
