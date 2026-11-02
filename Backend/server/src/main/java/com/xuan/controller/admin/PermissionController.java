package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.PermissionCreateDTO;
import com.xuan.dto.PermissionQueryDTO;
import com.xuan.dto.PermissionUpdateDTO;
import com.xuan.exception.BusinessException;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IPermissionService;
import com.xuan.vo.PermissionProfileVO;
import com.xuan.vo.PermissionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 后台权限管理接口，检查登录、后台入口和操作权限。
 */
@RestController
@RequestMapping("/admin/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final IPermissionService permissions;

    /**
     * 分页查询权限，先检查后台入口和查询权限，禁用项也能查。
     *
     * @param query 已校验的分页和过滤条件
     * @return ID 升序分页，不提供逐条 X-Revision
     */
    @GetMapping
    @SaCheckPermission("sys:permission:query")
    public Result<PageResult<PermissionVO>> list(@Valid @ModelAttribute PermissionQueryDTO query) {
        return Result.ok(permissions.listPermissions(query));
    }

    /**
     * 根据id查询权限详情及版本，先检查后台入口和查询权限。
     *
     * @param permissionId 正十进制权限 ID
     * @return 200 详情及 X-Revision，不存在返回 404
     */
    @GetMapping("/{permissionId}")
    @SaCheckPermission("sys:permission:query")
    public ResponseEntity<Result<PermissionVO>> detail(@PathVariable String permissionId) {
        return profile(permissions.detail(id(permissionId)));
    }

    /**
     * 新增权限，先检查后台入口和创建权限，不自动给角色授权，也不会新增后端功能。
     *
     * @param body 已校验的创建字段，不接受审计或授权字段
     * @return 201 资料及详情 Location，无 X-Revision
     */
    @PostMapping
    @SaCheckPermission("sys:permission:create")
    public ResponseEntity<Result<PermissionVO>> create(@Valid @RequestBody PermissionCreateDTO body) {
        PermissionVO permission = permissions.create(body);
        return ResponseEntity.created(URI.create("/api/v1/admin/permissions/" + permission.permissionId()))
                .body(Result.ok(permission));
    }

    /**
     * 根据id更新权限，先检查后台入口和更新权限，再完整替换资料及状态，权限编码不可修改。
     *
     * @param permissionId 目标权限 ID
     * @param body 完整资料，说明可为 null 但不可省略
     * @param revision 详情版本，缺失交由锁事务返回 428
     * @return 更新资料及新 X-Revision；保护或会话撤销失败整笔回滚
     */
    @PutMapping("/{permissionId}")
    @SaCheckPermission("sys:permission:update")
    public ResponseEntity<Result<PermissionVO>> update(@PathVariable String permissionId,
            @Valid @RequestBody PermissionUpdateDTO body,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(permissions.update(id(permissionId), body, revision));
    }

    /** 批量删除权限，单条也走此接口，业务层统一检查权限、版本并处理失败。 */
    @PostMapping("/batch-delete")
    @SaCheckPermission("sys:permission:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        permissions.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /**
     * 检查路径 ID，前导零、符号、小数或超过 Long 范围都拒绝。
     *
     * @param value 原始路径文本
     * @return 内部权限 ID
     */
    private long id(String value) {
        if (value.matches("[1-9][0-9]{0,18}")) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException exception) {
                throw new BusinessException(400, "VALIDATION_ERROR", "ID超出范围");
            }
        }
        throw new BusinessException(400, "VALIDATION_ERROR", "ID须为正十进制字符串");
    }

    /**
     * 版本只放响应头，响应体返回约定的字典资料。
     *
     * @param result 同一事务读取的资料和版本
     * @return 200 详情与 X-Revision
     */
    private ResponseEntity<Result<PermissionVO>> profile(PermissionProfileVO result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.permission()));
    }
}
