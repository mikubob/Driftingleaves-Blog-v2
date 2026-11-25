package com.xuan.controller.auth;

import com.xuan.annotation.RateLimit;
import com.xuan.constant.HttpConstants;
import com.xuan.dto.PasswordClearDTO;
import com.xuan.dto.PasswordSetDTO;
import com.xuan.dto.ProfilePatchDTO;
import com.xuan.result.Result;
import com.xuan.service.IUserService;
import com.xuan.vo.CapabilitiesVO;
import com.xuan.vo.MeVO;
import com.xuan.vo.MenuNodeVO;
import com.xuan.vo.OAuthBindingVO;
import com.xuan.vo.UserProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 本人资料与安全设置接口
 */
@Slf4j
@RestController
@RequestMapping({"/admin/me", "/blog/me"})
@RequiredArgsConstructor
public class MeController {

    private final IUserService userService;

    /**
     * 查询当前用户资料及版本
     *
     * @return 本人资料及 X-Revision 响应头
     */
    @GetMapping
    public ResponseEntity<Result<MeVO>> me() {
        log.info("查询当前用户资料");
        UserProfileVO profile = userService.me();
        return profileResponse(profile);
    }

    /**
     * 修改当前用户资料
     *
     * @param body 修改的资料字段
     * @param revision 请求头携带的 If-Revision 版本
     * @return 更新后的本人资料及 X-Revision 响应头
     */
    @PatchMapping("/profile")
    public ResponseEntity<Result<MeVO>> patch(@Valid @RequestBody ProfilePatchDTO body,
            @RequestHeader(value = HttpConstants.HEADER_IF_REVISION, required = false) String revision) {
        log.info("修改当前用户资料");
        UserProfileVO profile = userService.patch(body, revision);
        return profileResponse(profile);
    }

    /**
     * 设置或修改当前用户密码
     *
     * @param body 密码设置参数
     * @return 成功结果
     */
    @PutMapping("/password")
    @RateLimit(value = "me.password", limit = 10, dimension = RateLimit.Dimension.USER)
    public Result<Void> password(@Valid @RequestBody PasswordSetDTO body) {
        log.info("设置或修改当前用户密码");
        userService.setPassword(body);
        return Result.ok(null);
    }

    /**
     * 清除当前用户密码
     *
     * @param body 清除密码参数
     * @return 成功结果
     */
    @DeleteMapping("/password")
    @RateLimit(value = "me.password", limit = 10, dimension = RateLimit.Dimension.USER)
    public Result<Void> clearPassword(@Valid @RequestBody PasswordClearDTO body) {
        log.info("清除当前用户密码");
        userService.clearPassword(body);
        return Result.ok(null);
    }

    /**
     * 查询当前用户角色、权限及数据范围
     *
     * @return 权限能力视图
     */
    @GetMapping("/capabilities")
    public Result<CapabilitiesVO> capabilities() {
        log.info("查询当前用户权限能力");
        CapabilitiesVO capabilities = userService.currentCapabilities();
        return Result.ok(capabilities);
    }

    /**
     * 查询当前用户可访问的菜单树
     *
     * @return 菜单树列表
     */
    @GetMapping("/menus")
    public Result<List<MenuNodeVO>> menus() {
        log.info("查询当前用户菜单树");
        List<MenuNodeVO> menuList = userService.currentMenus();
        return Result.ok(menuList);
    }

    /**
     * 查询当前用户的第三方绑定列表
     *
     * @return 第三方绑定列表
     */
    @GetMapping("/oauth-bindings")
    public Result<List<OAuthBindingVO>> bindings() {
        log.info("查询当前用户第三方绑定");
        List<OAuthBindingVO> bindingList = userService.bindings();
        return Result.ok(bindingList);
    }

    /**
     * 解绑当前用户的指定第三方平台
     *
     * @param source 平台标识
     * @return 成功结果
     */
    @DeleteMapping("/oauth-bindings/{source}")
    public Result<Void> unbind(@PathVariable String source) {
        log.info("解绑当前用户第三方平台: source={}", source);
        userService.unbind(source);
        return Result.ok(null);
    }

    /**
     * 组装本人资料响应，版本放在 X-Revision 响应头。
     *
     * @param result 用户资料与版本
     * @return 200 响应
     */
    private ResponseEntity<Result<MeVO>> profileResponse(UserProfileVO result) {
        return ResponseEntity.ok()
                .header(HttpConstants.HEADER_X_REVISION, result.revision())
                .body(Result.ok(result.user()));
    }
}
