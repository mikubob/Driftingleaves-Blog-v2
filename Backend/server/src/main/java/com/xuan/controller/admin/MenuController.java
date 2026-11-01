package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.MenuCreateDTO;
import com.xuan.dto.MenuUpdateDTO;
import com.xuan.exception.BusinessException;
import com.xuan.result.Result;
import com.xuan.service.IMenuService;
import com.xuan.vo.MenuNodeVO;
import com.xuan.vo.MenuProfileVO;
import com.xuan.vo.MenuVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 菜单接口先检查身份和后台入口，再检查各项菜单操作权限。
 */
@RestController
@RequestMapping("/admin/menus")
@RequiredArgsConstructor
public class MenuController {

    private final IMenuService menus;

    /**
     * 检查后台入口和查询权限后取完整后台树，本人能访问哪些菜单走另一个接口。
     *
     * @return 200 全状态 MenuNode 列表，无逐条 X-Revision
     */
    @GetMapping
    @SaCheckPermission("sys:menu:query")
    public Result<List<MenuNodeVO>> tree() {
        return Result.ok(menus.tree());
    }

    /**
     * 检查后台入口和查询权限后取菜单详情及版本。
     *
     * @param menuId 正十进制菜单 ID
     * @return 200 详情及 X-Revision，不存在返回 404
     */
    @GetMapping("/{menuId}")
    @SaCheckPermission("sys:menu:query")
    public ResponseEntity<Result<MenuVO>> detail(@PathVariable String menuId) {
        return profile(menus.detail(id(menuId)));
    }

    /**
     * 检查后台入口和创建权限后建菜单，不自动给角色授权或增加接口权限。
     *
     * @param body 已校验的创建字段和默认值，不接受客户端审计字段
     * @return 201 资料和详情 Location，无 X-Revision
     */
    @PostMapping
    @SaCheckPermission("sys:menu:create")
    public ResponseEntity<Result<MenuVO>> create(@Valid @RequestBody MenuCreateDTO body) {
        MenuVO menu = menus.create(body);
        return ResponseEntity.created(URI.create("/api/v1/admin/menus/" + menu.menuId())).body(Result.ok(menu));
    }

    /**
     * 检查后台入口和更新权限后完整替换菜单，创建后的 menuCode 不能改。
     *
     * @param menuId 目标菜单 ID
     * @param body 全部可写字段，可空字段不可省略
     * @param revision 当前详情版本；缺失交由锁事务返回 428
     * @return 200 详情和新 X-Revision，结构或版本冲突拒绝全部写入
     */
    @PutMapping("/{menuId}")
    @SaCheckPermission("sys:menu:update")
    public ResponseEntity<Result<MenuVO>> update(@PathVariable String menuId,
            @Valid @RequestBody MenuUpdateDTO body,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(menus.update(id(menuId), body, revision));
    }

    /** 单个和多个目标共用入口，权限、版本及失败结果由业务层统一处理。 */
    @PostMapping("/batch-delete")
    @SaCheckPermission("sys:menu:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        menus.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /**
     * 检查路径 ID，前导零、符号、小数或超过 Long 范围都拒绝。
     *
     * @param value 原始路径文本
     * @return 内部菜单 ID
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
     * 版本放响应头，响应体只返回约定的菜单字段。
     *
     * @param result 同一事务得到的详情及版本
     * @return 200 详情及 X-Revision
     */
    private ResponseEntity<Result<MenuVO>> profile(MenuProfileVO result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.menu()));
    }
}
