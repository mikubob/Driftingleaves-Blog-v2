package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.PermissionCreateDTO;
import com.xuan.dto.PermissionQueryDTO;
import com.xuan.dto.PermissionUpdateDTO;
import com.xuan.entity.SysPermission;
import com.xuan.result.PageResult;
import com.xuan.vo.PermissionProfileVO;
import com.xuan.vo.PermissionVO;

import java.util.List;

/**
 * 权限业务接口，权限全站共用，每次检查登录、后台入口和本次操作权限。
 */
public interface IPermissionService extends IService<SysPermission> {

    /** 分页查询权限，按编码、名称、模块和状态筛选，在同一个只读事务中按id升序查询。 */
    PageResult<PermissionVO> listPermissions(PermissionQueryDTO query);

    /** 检查权限后取完整字典资料和版本，不存在返回 404。 */
    PermissionProfileVO detail(long permissionId);

    /** 创建字典项不代表增加后端功能，也不自动授予角色，重复编码返回 409。 */
    PermissionVO create(PermissionCreateDTO request);

    /** 根据id更新权限，加锁并检查版本后完整替换资料；状态改变时撤销全部关联用户的会话，Redis 操作失败就回滚。 */
    PermissionProfileVO update(long permissionId, PermissionUpdateDTO request, String revision);

    /** 根据id删除权限，加锁并检查版本后删除记录和角色关联；固定权限或仍被菜单引用的权限不能删除，撤销会话失败就回滚。 */
    void delete(long permissionId, String revision);
    /** 批量删除权限，单条也走此入口，任一失败则整批回滚。 */
    void deleteBatch(List<BatchItemDTO> items);
}
