package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.MenuCreateDTO;
import com.xuan.dto.MenuUpdateDTO;
import com.xuan.entity.SysMenu;
import com.xuan.vo.MenuNodeVO;
import com.xuan.vo.MenuProfileVO;
import com.xuan.vo.MenuVO;
import java.util.List;

/** 全局菜单管理；有效身份、后台入口及具体菜单权限共同校验，菜单不改变独立接口授权。 */
public interface IMenuService extends IService<SysMenu> {
    /** 一致快照内读取完整树，保留空目录、禁用、隐藏和按钮，逐层稳定排序。 */
    List<MenuNodeVO> tree();
    /** 鉴权后读取完整菜单详情及业务版本，不存在返回 404。 */
    MenuProfileVO detail(long menuId);
    /** 结构锁内校验字段、祖先和门禁，创建但不隐式授予任何角色。 */
    MenuVO create(MenuCreateDTO request);
    /** 结构锁内复核身份、版本及全部受影响树，完整替换资料，不自动补角色祖先或权限。 */
    MenuProfileVO update(long menuId, MenuUpdateDTO request, String revision);
    /** 锁内校验版本；有子节点返回 RESOURCE_IN_USE，叶子仅级联 role_menu，不撤销会话。 */
    void delete(long menuId, String revision);
}
