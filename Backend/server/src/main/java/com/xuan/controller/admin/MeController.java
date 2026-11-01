package com.xuan.controller.admin;

import com.xuan.annotation.RateLimit;
import com.xuan.dto.PasswordClearDTO;
import com.xuan.dto.PasswordSetDTO;
import com.xuan.dto.ProfilePatchDTO;
import com.xuan.result.Result;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.IUserService;
import com.xuan.vo.CapabilitiesVO;
import com.xuan.vo.MeVO;
import com.xuan.vo.MenuNodeVO;
import com.xuan.vo.OAuthBindingVO;
import com.xuan.vo.UserProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 这里操作当前用户的资料和安全设置，不收客户端目标用户 ID。
 */
@RestController("adminMeController")
@RequestMapping("/admin/me")
@RequiredArgsConstructor
public class MeController {

    private final IUserService users;
    private final AuthorizationProvider authorization;

    /**
     * 从会话确定当前用户，返回资料和 X-Revision；修改时要原样带回这个版本。
     *
     * @return 本人资料及 X-Revision 响应头
     */
    @GetMapping
    public ResponseEntity<Result<MeVO>> me() {
        return profile(users.me());
    }

    /**
     * 只改实际传来的字段，没传就保持，null 只能清空可空字段；版本没传返回 428，过期返回 412。
     *
     * @param body 已校验的允许修改的资料字段，以及客户端有没有传
     * @param revision 原样携带的带引号 If-Revision；可缺失以交由业务层区分错误
     * @return 更新后的本人资料和对应 X-Revision
     */
    @PatchMapping("/profile")
    public ResponseEntity<Result<MeVO>> patch(@Valid @RequestBody ProfilePatchDTO body,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(users.patch(body, revision));
    }

    /**
     * 设置或修改当前用户密码，最近认证、旧密码或 OAuth 身份和全部会话撤销都由业务层处理；成功后要重新登录。
     *
     * @param body 已校验的长度和 UTF-8 字节限制的密码请求
     * @return data 为 null 的成功结果
     */
    @PutMapping("/password")
    @RateLimit(value = "me.password", limit = 10, dimension = RateLimit.Dimension.USER)
    public Result<Void> password(@Valid @RequestBody PasswordSetDTO body) {
        users.setPassword(body);
        return Result.ok(null);
    }

    /**
     * 清除当前用户密码并撤销全部会话；用户锁内核对旧密码和剩余登录方式，不能删掉最后一种。
     *
     * @param body 包含当前密码的已校验的请求
     * @return data 为 null 的成功结果
     */
    @DeleteMapping("/password")
    @RateLimit(value = "me.password", limit = 10, dimension = RateLimit.Dimension.USER)
    public Result<Void> clearPassword(@Valid @RequestBody PasswordClearDTO body) {
        users.clearPassword(body);
        return Result.ok(null);
    }

    /**
     * 查询当前启用用户的角色、权限及每项范围，member 也能查；这不表示获得后台入口，资源 SQL 仍要检查归属。
     *
     * @return 按字典序排列的有效授权及权限到 ALL/SELF 的映射
     */
    @GetMapping("/capabilities")
    public Result<CapabilitiesVO> capabilities() {
        return Result.ok(authorization.capabilities(users.requireCurrent()));
    }

    /**
     * 查询本人能访问的菜单；没有 admin:access 就返回空列表，已授权的隐藏项和按钮保留，按钮不能生成页面路由。
     *
     * @return 完成权限交集和祖先链过滤的菜单树
     */
    @GetMapping("/menus")
    public Result<List<MenuNodeVO>> menus() {
        return Result.ok(authorization.menus(users.requireCurrent()));
    }

    /**
     * 只列当前配置可用的平台和绑定时间，不返回绑定 ID、uuid 或平台 Token。
     *
     * @return 按平台升序排列的绑定列表，无绑定时为空列表
     */
    @GetMapping("/oauth-bindings")
    public Result<List<OAuthBindingVO>> bindings() {
        return Result.ok(users.bindings());
    }

    /**
     * 解绑本人指定平台并撤销全部会话；先查最近认证和剩余登录方式，未绑定或平台关闭按约定报错。
     *
     * @param source 待解绑的服务端允许的平台标识
     * @return data 为 null 的成功结果
     */
    @DeleteMapping("/oauth-bindings/{source}")
    public Result<Void> unbind(@PathVariable String source) {
        users.unbind(source);
        return Result.ok(null);
    }

    /**
     * 把本人资料转成 HTTP 响应，版本只放 X-Revision，响应体只给已去掉敏感信息的资料。
     *
     * @param result 用户服务生成的返回资料与带引号版本
     * @return 统一成功响应及原样保留的版本响应头
     */
    private ResponseEntity<Result<MeVO>> profile(UserProfileVO result) {
        // 服务端生成的带引号版本原样放响应头，响应体只返回约定的资料。
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.user()));
    }
}
