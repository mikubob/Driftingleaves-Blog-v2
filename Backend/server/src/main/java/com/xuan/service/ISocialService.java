package com.xuan.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.dto.SocialWriteDTO;
import com.xuan.entity.HomeSocialLink;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.vo.SocialPublicVO;
/** 全站展示与后台维护共用同一份资料。 */
public interface ISocialService extends IService<HomeSocialLink> {
    /** 只返回展示状态与公开字段。 */
    List<SocialPublicVO> publicItems();
    /** 检查查询权限的ALL范围后分页读取。 */
    PageResult<HomeSocialLink> listItems(DisplayQueryDTO query);
    /** 一致快照读取资料和业务版本。 */
    ResourceVersion<HomeSocialLink> detail(long id);
    /** 锁操作者后创建，成功日志与资料一起提交。 */
    ResourceVersion<HomeSocialLink> create(SocialWriteDTO request);
    /** 资源锁后重查原会话、ALL和版本，完整替换。 */
    ResourceVersion<HomeSocialLink> update(long id, SocialWriteDTO request, String revision);
    /** 资源锁后核对版本，再物理删除元数据。 */
    void delete(long id, String revision);
}
