package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.constant.HttpConstants;
import com.xuan.dto.AdminUserCreateDTO;
import com.xuan.dto.AdminUserQueryDTO;
import com.xuan.dto.AdminUserUpdateDTO;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.UserRolesDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IUserService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.AdminUserProfileVO;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.UserAdminVO;
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
 * 管理端用户接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    /**
     * 分页查询用户列表
     *
     * @param query 查询参数
     * @return 用户分页结果
     */
    @GetMapping
    @SaCheckPermission("sys:user:query")
    public Result<PageResult<UserAdminVO>> list(@Valid @ModelAttribute AdminUserQueryDTO query) {
        log.info("分页查询用户列表: {}", query);
        Long roleId = IdUtils.parse(query.getRoleId());
        PageResult<UserAdminVO> pageResult = userService.adminList(query, roleId);
        return Result.ok(pageResult);
    }

    /**
     * 根据 ID 查询用户详情
     *
     * @param userId 用户 ID
     * @return 用户详情及 X-Revision 响应头
     */
    @GetMapping("/{userId}")
    @SaCheckPermission("sys:user:query")
    public ResponseEntity<Result<UserAdminVO>> detail(@PathVariable String userId) {
        log.info("根据 ID 查询用户详情: userId={}", userId);
        AdminUserProfileVO profile = userService.adminDetail(IdUtils.parse(userId));
        return profileResponse(profile);
    }

    /**
     * 创建用户
     *
     * @param body 用户创建参数
     * @return 201 响应及详情 Location
     */
    @PostMapping
    @SaCheckPermission("sys:user:create")
    public ResponseEntity<Result<UserAdminVO>> create(@Valid @RequestBody AdminUserCreateDTO body) {
        log.info("创建用户: username={}", body.getUsername());
        UserAdminVO user = userService.adminCreate(body);
        URI location = URI.create("/api/v1/admin/users/" + user.getUserId());
        return ResponseEntity.created(location).body(Result.ok(user));
    }

    /**
     * 根据 ID 更新用户资料
     *
     * @param userId 用户 ID
     * @param body 用户更新参数
     * @param revision 请求头携带的 If-Revision 版本
     * @return 更新后的用户详情及新 X-Revision 响应头
     */
    @PutMapping("/{userId}")
    @SaCheckPermission("sys:user:update")
    public ResponseEntity<Result<UserAdminVO>> update(@PathVariable String userId,
            @Valid @RequestBody AdminUserUpdateDTO body,
            @RequestHeader(value = HttpConstants.HEADER_IF_REVISION, required = false) String revision) {
        log.info("更新用户资料: userId={}", userId);
        AdminUserProfileVO profile = userService.adminUpdate(IdUtils.parse(userId), body, revision);
        return profileResponse(profile);
    }

    /**
     * 根据 ID 更新用户角色
     *
     * @param userId 用户 ID
     * @param body 角色分配参数
     * @param revision 请求头携带的 If-Revision 版本
     * @return 更新后的用户详情及新 X-Revision 响应头
     */
    @PutMapping("/{userId}/roles")
    @SaCheckPermission("sys:user:assignRole")
    public ResponseEntity<Result<UserAdminVO>> roles(@PathVariable String userId,
            @Valid @RequestBody UserRolesDTO body,
            @RequestHeader(value = HttpConstants.HEADER_IF_REVISION, required = false) String revision) {
        log.info("分配用户角色: userId={}", userId);
        AdminUserProfileVO profile = userService.adminAssignRoles(IdUtils.parse(userId), body, revision);
        return profileResponse(profile);
    }

    /**
     * 批量启用用户
     *
     * @param body 批量操作参数
     * @return 更新后的批量版本信息
     */
    @PutMapping("/batch-enable")
    @SaCheckPermission("sys:user:update")
    public Result<BatchVersionsVO> enable(@Valid @RequestBody BatchRevisionDTO body) {
        log.info("批量启用用户: count={}", body.getItems().size());
        BatchVersionsVO versions = userService.adminSetStatusBatch(body.getItems(), 1);
        return Result.ok(versions);
    }

    /**
     * 批量禁用用户
     *
     * @param body 批量操作参数
     * @return 更新后的批量版本信息
     */
    @PutMapping("/batch-disable")
    @SaCheckPermission("sys:user:update")
    public Result<BatchVersionsVO> disable(@Valid @RequestBody BatchRevisionDTO body) {
        log.info("批量禁用用户: count={}", body.getItems().size());
        BatchVersionsVO versions = userService.adminSetStatusBatch(body.getItems(), 0);
        return Result.ok(versions);
    }

    /**
     * 批量删除用户
     *
     * @param body 批量操作参数
     * @return 成功结果
     */
    @PostMapping("/batch-delete")
    @SaCheckPermission("sys:user:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        log.info("批量删除用户: count={}", body.getItems().size());
        userService.adminDeleteBatch(body.getItems());
        return Result.ok(null);
    }

    /**
     * 组装用户详情响应，版本放在 X-Revision 响应头。
     *
     * @param result 用户详情与版本
     * @return 200 响应
     */
    private ResponseEntity<Result<UserAdminVO>> profileResponse(AdminUserProfileVO result) {
        return ResponseEntity.ok()
                .header(HttpConstants.HEADER_X_REVISION, result.getRevision())
                .body(Result.ok(result.getUser()));
    }
}
