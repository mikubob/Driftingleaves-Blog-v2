package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.MenuCreateDTO;
import com.xuan.dto.MenuUpdateDTO;
import com.xuan.entity.SysMenu;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.MenuNodeVO;
import com.xuan.vo.MenuProfileVO;
import com.xuan.vo.MenuVO;

import java.util.List;

/**
 * 菜单业务接口，菜单全站共用，检查登录、后台入口和操作权限，菜单授权不能代替接口授权。
 */
public interface IMenuService extends IService<SysMenu> {

    /** 查询完整后台菜单树，在同一个只读事务中读取，保留空目录、禁用菜单、隐藏菜单和按钮，每层按固定顺序排列。 */
    List<MenuNodeVO> tree();

    /** 检查权限后取菜单详情和版本，不存在返回 404。 */
    MenuProfileVO detail(long menuId);

    /** 新增菜单，拿到结构锁后检查字段、上级菜单和访问权限，不自动给角色授权。 */
    MenuVO create(MenuCreateDTO request);

    /** 根据id更新菜单，拿到结构锁后重新检查身份、版本和受影响的菜单，再完整替换资料；不自动补齐角色的上级菜单或权限。 */
    MenuProfileVO update(long menuId, MenuUpdateDTO request, String revision);

    /** 批量删除菜单，单条也走此入口，任一失败则整批回滚。 */
    void deleteBatch(List<BatchItemDTO> items);
    /** 批量修改状态，单条也走此入口，全部版本通过后写入。 */
    BatchVersionsVO changeStatusBatch(List<BatchItemDTO> items, int status);

}
