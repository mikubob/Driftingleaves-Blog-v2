package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 权限字典接口先检查身份和后台入口，再检查各项业务权限。
 */
@RestController
@RequestMapping("/admin/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final IPermissionService permissions;

    /**
     * 检查后台入口和查询权限后分页取字典，禁用项也能查。
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
     * 检查后台入口和查询权限后取详情及版本。
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
     * 检查后台入口和创建权限后建字典项，不自动给角色授权，也不会凭空增加后端功能。
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
     * 检查后台入口和更新权限后完整替换资料、状态，编码不能改。
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

    /**
     * 检查后台入口和删除权限后再进入锁事务；固定权限或仍被菜单引用的权限不能删。
     *
     * @param permissionId 目标权限 ID
     * @param revision 当前详情版本
     * @return 200 空结果，无 X-Revision；角色关联及全部会话撤销共同受失败回滚约束
     */
    @DeleteMapping("/{permissionId}")
    @SaCheckPermission("sys:permission:delete")
    public Result<Void> delete(@PathVariable String permissionId,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        permissions.delete(id(permissionId), revision);
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
