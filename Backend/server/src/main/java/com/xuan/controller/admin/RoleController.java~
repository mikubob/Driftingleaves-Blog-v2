package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.RoleCreateDTO;
import com.xuan.dto.RoleGrantDTO;
import com.xuan.dto.RoleQueryDTO;
import com.xuan.dto.RoleUpdateDTO;
import com.xuan.exception.BusinessException;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IRoleService;
import com.xuan.vo.RoleProfileVO;
import com.xuan.vo.RoleVO;
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
 * 后台角色接口先检查身份和入口权限，各项操作还要检查对应业务权限。
 */
@RestController
@RequestMapping("/admin/roles")
@RequiredArgsConstructor
public class RoleController {
    private final IRoleService roles;

    /**
     * 检查后台入口和查询权限后分页取全站角色，SELF 不缩小这个字典范围。
     * @param query 已校验的分页、关键词及状态
     * @return 含完整授权关联的分页；无逐条 X-Revision
     */
    @GetMapping
    @SaCheckPermission("sys:role:query")
    public Result<PageResult<RoleVO>> list(@Valid @ModelAttribute RoleQueryDTO query) {
        return Result.ok(roles.listRoles(query));
    }

    /**
     * 检查后台入口和查询权限后取角色详情及整体版本，禁用角色也能查。
     * @param roleId 正十进制角色 ID
     * @return 详情及 X-Revision；不存在返回 404
     */
    @GetMapping("/{roleId}")
    @SaCheckPermission("sys:role:query")
    public ResponseEntity<Result<RoleVO>> detail(@PathVariable String roleId) {
        return profile(roles.detail(id(roleId)));
    }

    /**
     * 检查后台入口和创建权限后建空授权角色，不收关联集合和时间字段。
     * @param body 已校验的创建字段
     * @return 201 角色及详情 Location，无 X-Revision
     */
    @PostMapping
    @SaCheckPermission("sys:role:create")
    public ResponseEntity<Result<RoleVO>> create(@Valid @RequestBody RoleCreateDTO body) {
        RoleVO role = roles.create(body);
        return ResponseEntity.created(URI.create("/api/v1/admin/roles/" + role.roleId())).body(Result.ok(role));
    }

    /**
     * 检查后台入口和更新权限后完整替换资料、状态，版本和最后管理员在锁事务里检查。
     * @param roleId 角色 ID
     * @param body 全部允许字段，说明可为 null 但不可省略
     * @param revision 单个详情版本，缺失交由 Service 返回 428
     * @return 更新详情及新 X-Revision
     */
    @PutMapping("/{roleId}")
    @SaCheckPermission("sys:role:update")
    public ResponseEntity<Result<RoleVO>> update(@PathVariable String roleId,
            @Valid @RequestBody RoleUpdateDTO body,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(roles.update(id(roleId), body, revision));
    }

    /**
     * 检查后台入口和删除权限后删自定义角色，清关联、撤销全部会话有任何失败都要回滚。
     * @param roleId 目标角色
     * @param revision 当前详情版本，内置角色禁止删除
     * @return 200 空结果，无 X-Revision
     */
    @DeleteMapping("/{roleId}")
    @SaCheckPermission("sys:role:delete")
    public Result<Void> delete(@PathVariable String roleId,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        roles.delete(id(roleId), revision);
        return Result.ok(null);
    }

    /**
     * 检查后台入口和授权权限后一起替换权限、菜单，补齐祖先，但不能自动增加接口权限。
     * @param roleId 目标角色
     * @param body 两个必填且不重复的集合，空数组允许清空
     * @param revision 资料和授权共用的详情版本
     * @return 最终集合及新 X-Revision，任一失败整笔回滚
     */
    @PutMapping("/{roleId}/grants")
    @SaCheckPermission("sys:role:grant")
    public ResponseEntity<Result<RoleVO>> grants(@PathVariable String roleId,
            @Valid @RequestBody RoleGrantDTO body,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(roles.grants(id(roleId), body, revision));
    }

    /**
     * 检查路径 ID，前导零、符号、小数或超过 Long 范围都拒绝。
     * @param value 原始路径文本
     * @return 内部 Long ID
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
     * 角色整体版本放响应头，响应体只给约定的角色字段。
     * @param result 同一事务读取的角色及版本
     * @return 200 详情与 X-Revision
     */
    private ResponseEntity<Result<RoleVO>> profile(RoleProfileVO result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.role()));
    }
}
