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
 * 后台角色操作每次都要查当前身份、后台入口和具体业务权限。
 */
public interface IRoleService extends IService<SysRole> {

    /** 检查后台及业务权限后，在同一快照里分页，关联中的禁用项也保留。 */
    PageResult<RoleVO> listRoles(RoleQueryDTO query);

    /** 同一快照里取角色详情，用资料、权限和菜单一起计算整体版本。 */
    RoleProfileVO detail(long roleId);

    /** 新角色没有权限和菜单，唯一键冲突就整笔回滚。 */
    RoleVO create(RoleCreateDTO request);

    /** 资料和状态完整替换，锁内重查版本，影响授权时撤销全部关联用户会话。 */
    RoleProfileVO update(long roleId, RoleUpdateDTO request, String revision);

    /** 删除自定义角色及三类关联，内置角色不能删，撤销会话失败就全部回滚。 */
    void delete(long roleId, String revision);

    /** 权限和菜单一起替换，补齐祖先并检查明确授予的权限，必须保留最后管理员。 */
    RoleProfileVO grants(long roleId, RoleGrantDTO request, String revision);
    /** 单条和多条共用的事务入口，失败不保留部分数据库变更。 */
    void deleteBatch(List<BatchItemDTO> items);
}
