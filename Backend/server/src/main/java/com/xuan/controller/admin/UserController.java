package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.AdminUserCreateDTO;
import com.xuan.dto.AdminUserQueryDTO;
import com.xuan.dto.AdminUserUpdateDTO;
import com.xuan.dto.UserRolesDTO;
import com.xuan.exception.BusinessException;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IUserService;
import com.xuan.vo.AdminUserProfileVO;
import com.xuan.vo.UserAdminVO;
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

/** 后台用户入口；后台入口权限由公共拦截器检查，各操作另需明确业务权限。 */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserController {
    private final IUserService users;

    /**
     * 分页查询全局用户，不受 SELF 收缩；关键词和角色过滤由同一 SQL 条件落实。
     * @param query 已校验的分页及过滤参数
     * @return 无逐条版本的脱敏分页结果
     */
    @GetMapping
    @SaCheckPermission("sys:user:query")
    public Result<PageResult<UserAdminVO>> list(@Valid @ModelAttribute AdminUserQueryDTO query) {
        return Result.ok(users.adminList(query, query.roleId() == null ? null : id(query.roleId())));
    }

    /**
     * 查询全局用户详情及用户聚合版本，允许查看禁用用户。
     * @param userId 正十进制用户 ID
     * @return 资料、完整角色集合和 X-Revision；不存在返回 404
     */
    @GetMapping("/{userId}")
    @SaCheckPermission("sys:user:query")
    public ResponseEntity<Result<UserAdminVO>> detail(@PathVariable String userId) {
        return profile(users.adminDetail(id(userId)));
    }

    /**
     * 创建有本站密码但未分配角色的用户，不自动签发会话。
     * @param body 已校验的创建字段，不接受 roleIds
     * @return 201 资料及对应详情 Location
     */
    @PostMapping
    @SaCheckPermission("sys:user:create")
    public ResponseEntity<Result<UserAdminVO>> create(@Valid @RequestBody AdminUserCreateDTO body) {
        UserAdminVO user = users.adminCreate(body);
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + user.userId())).body(Result.ok(user));
    }

    /**
     * 完整替换资料和状态；Service 在锁内校验版本并在禁用时撤销全部会话。
     * @param userId 目标用户 ID
     * @param body 含可空字段的完整资料
     * @param revision 带引号的版本；缺失交由 Service 返回 428
     * @return 更新资料及新 X-Revision
     */
    @PutMapping("/{userId}")
    @SaCheckPermission("sys:user:update")
    public ResponseEntity<Result<UserAdminVO>> update(@PathVariable String userId,
            @Valid @RequestBody AdminUserUpdateDTO body,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(users.adminUpdate(id(userId), body, revision));
    }

    /**
     * 完整替换用户角色；Service 保护最后管理员，提交前撤销目标全部会话。
     * @param userId 目标用户 ID
     * @param body 去重且最多一百项的角色集合，空集合清空
     * @param revision 用户详情的聚合版本
     * @return 更新资料及新 X-Revision
     */
    @PutMapping("/{userId}/roles")
    @SaCheckPermission("sys:user:assignRole")
    public ResponseEntity<Result<UserAdminVO>> roles(@PathVariable String userId,
            @Valid @RequestBody UserRolesDTO body,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(users.adminAssignRoles(id(userId), body, revision));
    }

    /**
     * 校验 HTTP 路径和查询 ID，不接受前导零、符号、空白或 Long 溢出。
     * @param value 原始 ID 文本
     * @return 已确认的内部 Long ID
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
     * 将内部聚合结果转换为统一响应，版本只放入响应头。
     * @param result 资料及其带引号版本
     * @return 成功响应及 X-Revision
     */
    private ResponseEntity<Result<UserAdminVO>> profile(AdminUserProfileVO result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.user()));
    }
}
