package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.constant.HttpConstants;
import com.xuan.dto.BatchBinaryStatusDTO;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.MenuCreateDTO;
import com.xuan.dto.MenuUpdateDTO;
import com.xuan.result.Result;
import com.xuan.service.IMenuService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.MenuNodeVO;
import com.xuan.vo.MenuProfileVO;
import com.xuan.vo.MenuVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * 管理端菜单接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/menus")
@RequiredArgsConstructor
public class MenuController {

    private final IMenuService menuService;

    /**
     * 查询完整后台菜单树
     *
     * @return 菜单树列表
     */
    @GetMapping
    @SaCheckPermission("sys:menu:query")
    public Result<List<MenuNodeVO>> tree() {
        log.info("查询后台菜单树");
        List<MenuNodeVO> menuTree = menuService.tree();
        return Result.ok(menuTree);
    }

    /**
     * 根据 ID 查询菜单详情
     *
     * @param menuId 菜单 ID
     * @return 菜单详情及 X-Revision 响应头
     */
    @GetMapping("/{menuId}")
    @SaCheckPermission("sys:menu:query")
    public ResponseEntity<Result<MenuVO>> detail(@PathVariable String menuId) {
        log.info("根据 ID 查询菜单详情: menuId={}", menuId);
        MenuProfileVO profile = menuService.detail(IdUtils.parse(menuId));
        return profileResponse(profile);
    }

    /**
     * 新增菜单
     *
     * @param body 菜单创建参数
     * @return 201 响应及详情 Location
     */
    @PostMapping
    @SaCheckPermission("sys:menu:create")
    public ResponseEntity<Result<MenuVO>> create(@Valid @RequestBody MenuCreateDTO body) {
        log.info("新增菜单: menuCode={}", body.getMenuCode());
        MenuVO menu = menuService.create(body);
        URI location = URI.create("/api/v1/admin/menus/" + menu.menuId());
        return ResponseEntity.created(location).body(Result.ok(menu));
    }

    /**
     * 根据 ID 更新菜单
     *
     * @param menuId 菜单 ID
     * @param body 菜单更新参数
     * @param revision 请求头携带的 If-Revision 版本
     * @return 更新后的菜单详情及新 X-Revision 响应头
     */
    @PutMapping("/{menuId}")
    @SaCheckPermission("sys:menu:update")
    public ResponseEntity<Result<MenuVO>> update(@PathVariable String menuId,
            @Valid @RequestBody MenuUpdateDTO body,
            @RequestHeader(value = HttpConstants.HEADER_IF_REVISION, required = false) String revision) {
        log.info("更新菜单: menuId={}", menuId);
        MenuProfileVO profile = menuService.update(IdUtils.parse(menuId), body, revision);
        return profileResponse(profile);
    }

    /**
     * 批量修改菜单状态
     *
     * @param body 批量状态变更参数
     * @return 更新后的批量版本信息
     */
    @PutMapping("/batch-status")
    @SaCheckPermission("sys:menu:update")
    public Result<BatchVersionsVO> status(@Valid @RequestBody BatchBinaryStatusDTO body) {
        log.info("批量修改菜单状态: count={}, status={}", body.getItems().size(), body.getStatus());
        BatchVersionsVO versions = menuService.changeStatusBatch(body.getItems(), body.getStatus());
        return Result.ok(versions);
    }

    /**
     * 批量删除菜单
     *
     * @param body 批量操作参数
     * @return 成功结果
     */
    @PostMapping("/batch-delete")
    @SaCheckPermission("sys:menu:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        log.info("批量删除菜单: count={}", body.getItems().size());
        menuService.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /**
     * 组装菜单详情响应，版本放在 X-Revision 响应头。
     *
     * @param result 菜单详情与版本
     * @return 200 响应
     */
    private ResponseEntity<Result<MenuVO>> profileResponse(MenuProfileVO result) {
        return ResponseEntity.ok()
                .header(HttpConstants.HEADER_X_REVISION, result.revision())
                .body(Result.ok(result.menu()));
    }
}
