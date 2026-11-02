package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.RoleCreateDTO;
import com.xuan.dto.RoleGrantDTO;
import com.xuan.dto.RoleQueryDTO;
import com.xuan.dto.RoleUpdateDTO;
import com.xuan.entity.SysRole;
import com.xuan.result.PageResult;
import com.xuan.vo.RoleProfileVO;
import com.xuan.vo.RoleVO;

import java.util.List;

/**
 * 角色业务接口，每次操作检查登录、后台入口和角色操作权限。
 */
public interface IRoleService extends IService<SysRole> {

    /** 分页查询角色，先检查后台入口和查询权限，再在同一个只读事务中读取，保留关联中的禁用项。 */
    PageResult<RoleVO> listRoles(RoleQueryDTO query);

    /** 根据id查询角色详情，资料、权限和菜单在同一个只读事务中读取，并一起计算版本。 */
    RoleProfileVO detail(long roleId);

    /** 新增角色，初始不分配权限和菜单，唯一键冲突时全部回滚。 */
    RoleVO create(RoleCreateDTO request);

    /** 根据id更新角色，加锁后重新检查版本，再完整替换资料和状态；影响授权时撤销全部关联用户的会话。 */
    RoleProfileVO update(long roleId, RoleUpdateDTO request, String revision);

    /** 删除自定义角色及三类关联，内置角色不能删，撤销会话失败就全部回滚。 */
    void delete(long roleId, String revision);

    /** 根据id更新角色权限和菜单，两者一起替换；补齐上级菜单并检查实际授予的权限，保留最后一个有效管理员。 */
    RoleProfileVO grants(long roleId, RoleGrantDTO request, String revision);
    /** 批量删除角色，单条也走此入口，任一失败则整批回滚。 */
    void deleteBatch(List<BatchItemDTO> items);
}
