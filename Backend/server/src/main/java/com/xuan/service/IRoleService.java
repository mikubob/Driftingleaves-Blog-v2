package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.RoleCreateDTO;
import com.xuan.dto.RoleGrantDTO;
import com.xuan.dto.RoleQueryDTO;
import com.xuan.dto.RoleUpdateDTO;
import com.xuan.entity.SysRole;
import com.xuan.result.PageResult;
import com.xuan.vo.RoleProfileVO;
import com.xuan.vo.RoleVO;

/** 后台角色管理；各操作检查当前身份、后台入口及对应业务权限。 */
public interface IRoleService extends IService<SysRole> {
    /** 双权限检查后从一致快照分页，关联集合包含禁用项。 */
    PageResult<RoleVO> listRoles(RoleQueryDTO query);
    /** 从一致快照查询角色详情及资料、权限、菜单共同生成的聚合版本。 */
    RoleProfileVO detail(long roleId);
    /** 创建无权限、无菜单角色，唯一冲突整笔回滚。 */
    RoleVO create(RoleCreateDTO request);
    /** 完整替换资料与状态，锁内复核版本；影响授权时撤销全部关联用户会话。 */
    RoleProfileVO update(long roleId, RoleUpdateDTO request, String revision);
    /** 删除自定义角色及三类关联；内置角色禁止删除，撤销失败整笔回滚。 */
    void delete(long roleId, String revision);
    /** 原子替换权限及菜单，补齐祖先并校验显式权限，保护最后管理员。 */
    RoleProfileVO grants(long roleId, RoleGrantDTO request, String revision);
}
