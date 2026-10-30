package com.xuan.controller.me;

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

/** 本人资料和安全设置入口，不接收客户端目标用户 ID。 */
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class MeController {
    private final IUserService users;
    private final AuthorizationProvider authorization;

    /**
     * 查询当前登录用户资料，并返回聚合版本响应头。
     * <p>目标身份只从会话取得，X-Revision 供后续本人资料修改原样携带。</p>
     *
     * @return 本人资料及 X-Revision 响应头
     */
    @GetMapping
    public ResponseEntity<Result<MeVO>> me() {
        return profile(users.me());
    }

    /**
     * 按出现性语义修改本人资料，并返回新的聚合版本。
     * <p>缺失字段保持原值，null 仅清空可空字段；缺失版本由 Service 返回 428，过期版本返回 412。</p>
     *
     * @param body 已校验的资料白名单字段及其出现性
     * @param revision 原样携带的带引号 If-Revision；可缺失以交由业务层区分错误
     * @return 更新后的本人资料和对应 X-Revision
     */
    @PatchMapping("/profile")
    public ResponseEntity<Result<MeVO>> patch(@Valid @RequestBody ProfilePatchDTO body,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(users.patch(body, revision));
    }

    /**
     * 设置或修改当前用户的本站密码。
     * <p>最近认证、旧密码或 OAuth 身份检查及全会话撤销由 Service 完成，成功后前端需重新登录。</p>
     *
     * @param body 已校验长度和 UTF-8 字节边界的密码请求
     * @return data 为 null 的成功结果
     */
    @PutMapping("/password")
    public Result<Void> password(@Valid @RequestBody PasswordSetDTO body) {
        users.setPassword(body);
        return Result.ok(null);
    }

    /**
     * 清除当前用户的本站密码，并撤销全部本站会话。
     * <p>Service 在用户锁内验证原密码和剩余可用登录方式，禁止删除最后一种登录方式。</p>
     *
     * @param body 包含当前密码的已校验请求
     * @return data 为 null 的成功结果
     */
    @DeleteMapping("/password")
    public Result<Void> clearPassword(@Valid @RequestBody PasswordClearDTO body) {
        users.clearPassword(body);
        return Result.ok(null);
    }

    /**
     * 查询当前启用用户的有效角色、权限及逐权限数据范围。
     * <p>普通 member 也可调用；返回结果不等于授予后台入口，也不代替资源 SQL 的归属过滤。</p>
     *
     * @return 按字典序排列的有效授权及权限到 ALL/SELF 的映射
     */
    @GetMapping("/capabilities")
    public Result<CapabilitiesVO> capabilities() {
        return Result.ok(authorization.capabilities(users.requireCurrent()));
    }

    /**
     * 查询当前用户可访问的后台菜单树。
     * <p>无 admin:access 时为空列表；返回已授权隐藏项和按钮，按钮不应被前端当作页面路由。</p>
     *
     * @return 完成权限交集和祖先链过滤的菜单树
     */
    @GetMapping("/menus")
    public Result<List<MenuNodeVO>> menus() {
        return Result.ok(authorization.menus(users.requireCurrent()));
    }

    /**
     * 查询当前用户的第三方绑定列表。
     * <p>只列当前配置可用的平台，仅公开平台和绑定时间，不返回绑定记录 ID、uuid 或平台 Token。</p>
     *
     * @return 按平台升序排列的绑定列表，无绑定时为空列表
     */
    @GetMapping("/oauth-bindings")
    public Result<List<OAuthBindingVO>> bindings() {
        return Result.ok(users.bindings());
    }

    /**
     * 解绑当前用户指定平台的身份，并撤销全部本站会话。
     * <p>Service 检查最近认证及最后登录方式；未绑定或平台未启用时按业务契约返回错误。</p>
     *
     * @param source 待解绑的服务端白名单平台标识
     * @return data 为 null 的成功结果
     */
    @DeleteMapping("/oauth-bindings/{source}")
    public Result<Void> unbind(@PathVariable String source) {
        users.unbind(source);
        return Result.ok(null);
    }

    /**
     * 将本人资料查询结果转换为 HTTP 响应。
     * <p>版本只放入 X-Revision 响应头，正文使用脱敏资料模型，不泄露内部聚合结构。</p>
     *
     * @param result 用户服务生成的资料投影与带引号版本
     * @return 统一成功响应及原样保留的版本响应头
     */
    private ResponseEntity<Result<MeVO>> profile(UserProfileVO result) {
        // 将服务生成的带引号版本原样放入响应头，响应体仅包含公开资料投影。
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.user()));
    }
}
