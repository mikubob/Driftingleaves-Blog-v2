package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.PermissionCreateDTO;
import com.xuan.dto.PermissionQueryDTO;
import com.xuan.dto.PermissionUpdateDTO;
import com.xuan.entity.SysPermission;
import com.xuan.result.PageResult;
import com.xuan.vo.PermissionProfileVO;
import com.xuan.vo.PermissionVO;

/** 全局权限字典业务；当前有效身份、后台入口及具体操作权限均必须满足。 */
public interface IPermissionService extends IService<SysPermission> {
    /** 同一只读快照按编码或名称字面包含及模块、状态过滤，ID 升序分页。 */
    PageResult<PermissionVO> listPermissions(PermissionQueryDTO query);
    /** 鉴权后读取完整字典资料与业务版本，不存在返回 404。 */
    PermissionProfileVO detail(long permissionId);
    /** 创建字典项，不建立后端能力或角色授权；重复编码返回 409。 */
    PermissionVO create(PermissionCreateDTO request);
    /** 锁内验证版本后完整替换资料；状态变化撤销全部关联用户会话，Redis 失败回滚。 */
    PermissionProfileVO update(long permissionId, PermissionUpdateDTO request, String revision);
    /** 锁内验证版本后删除并级联清理角色关联；固定后端权限或菜单引用拒绝，撤销失败回滚。 */
    void delete(long permissionId, String revision);
}
