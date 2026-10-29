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

/** 后台角色入口；公共拦截器检查有效身份和后台入口，各接口另需业务权限。 */
@RestController
@RequestMapping("/admin/roles")
@RequiredArgsConstructor
public class RoleController {
    private final IRoleService roles;

    /**
     * 双权限检查后分页查询全局角色，SELF 不收缩字典资源。
     * @param query 已校验的分页、关键词及状态
     * @return 含完整授权关联的分页；无逐条 X-Revision
     */
    @GetMapping
    @SaCheckPermission("sys:role:query")
    public Result<PageResult<RoleVO>> list(@Valid @ModelAttribute RoleQueryDTO query) {
        return Result.ok(roles.listRoles(query));
    }

    /**
     * 双权限检查后读取角色详情及聚合版本，允许查询禁用角色。
     * @param roleId 正十进制角色 ID
     * @return 详情及 X-Revision；不存在返回 404
     */
    @GetMapping("/{roleId}")
    @SaCheckPermission("sys:role:query")
    public ResponseEntity<Result<RoleVO>> detail(@PathVariable String roleId) {
        return profile(roles.detail(id(roleId)));
    }

    /**
     * 双权限检查后创建空授权角色，不接受关联和审计字段。
     * @param body 已校验创建白名单
     * @return 201 角色及详情 Location，无 X-Revision
     */
    @PostMapping
    @SaCheckPermission("sys:role:create")
    public ResponseEntity<Result<RoleVO>> create(@Valid @RequestBody RoleCreateDTO body) {
        RoleVO role = roles.create(body);
        return ResponseEntity.created(URI.create("/api/v1/admin/roles/" + role.roleId())).body(Result.ok(role));
    }

    /**
     * 双权限检查后完整替换资料及状态；版本和管理员保护由锁内事务落实。
     * @param roleId 角色 ID
     * @param body 完整白名单，说明可为 null 但不可省略
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
     * 双权限检查后删除自定义角色，关联清理及全部会话撤销共同受事务失败约束。
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
     * 双权限检查后共同完整替换权限及菜单；补齐祖先但不隐式增加权限。
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
     * 校验路径 ID，拒绝前导零、符号、小数和 Long 溢出。
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
     * 将聚合版本放入响应头，响应体仅包含角色白名单。
     * @param result 同一事务读取的角色及版本
     * @return 200 详情与 X-Revision
     */
    private ResponseEntity<Result<RoleVO>> profile(RoleProfileVO result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.role()));
    }
}
