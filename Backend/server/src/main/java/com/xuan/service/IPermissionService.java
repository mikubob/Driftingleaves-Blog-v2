package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.PermissionCreateDTO;
import com.xuan.dto.PermissionQueryDTO;
import com.xuan.dto.PermissionUpdateDTO;
import com.xuan.entity.SysPermission;
import com.xuan.result.PageResult;
import com.xuan.vo.PermissionProfileVO;
import com.xuan.vo.PermissionVO;

/**
 * 权限字典是全站资源，当前身份、后台入口和本次操作权限都必须有效。
 */
public interface IPermissionService extends IService<SysPermission> {

    /** 同一只读快照里按编码、名称文字及模块、状态筛选，ID 升序分页。 */
    PageResult<PermissionVO> listPermissions(PermissionQueryDTO query);

    /** 检查权限后取完整字典资料和版本，不存在返回 404。 */
    PermissionProfileVO detail(long permissionId);

    /** 创建字典项不代表增加后端功能，也不自动授予角色，重复编码返回 409。 */
    PermissionVO create(PermissionCreateDTO request);

    /** 锁内查版本后完整替换资料；状态变了就撤销全部关联用户会话，Redis 失败就回滚。 */
    PermissionProfileVO update(long permissionId, PermissionUpdateDTO request, String revision);

    /** 锁内查版本后删除并清理角色关联，固定权限或仍被菜单引用的权限不能删，撤销会话失败就回滚。 */
    void delete(long permissionId, String revision);
}
