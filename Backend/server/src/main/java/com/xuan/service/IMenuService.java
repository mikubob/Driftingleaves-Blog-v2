package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.MenuCreateDTO;
import com.xuan.dto.MenuUpdateDTO;
import com.xuan.entity.SysMenu;
import com.xuan.vo.MenuNodeVO;
import com.xuan.vo.MenuProfileVO;
import com.xuan.vo.MenuVO;

import java.util.List;

/**
 * 菜单是全站资源，身份、后台入口和具体权限都要查，菜单授权不能代替接口授权。
 */
public interface IMenuService extends IService<SysMenu> {

    /** 同一快照里取完整树，空目录、禁用项、隐藏项和按钮都保留，每层排序固定。 */
    List<MenuNodeVO> tree();

    /** 检查权限后取菜单详情和版本，不存在返回 404。 */
    MenuProfileVO detail(long menuId);

    /** 持有结构锁时检查字段、祖先和访问权限，创建菜单不自动给角色授权。 */
    MenuVO create(MenuCreateDTO request);

    /** 结构锁内重查身份、版本和受影响的树，再完整替换资料，不自动补角色祖先或权限。 */
    MenuProfileVO update(long menuId, MenuUpdateDTO request, String revision);

    /** 锁内先查版本，有子节点就报 RESOURCE_IN_USE；删叶子只级联 role_menu，不撤销会话。 */
    void delete(long menuId, String revision);
}
