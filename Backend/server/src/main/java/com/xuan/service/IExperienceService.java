package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.ExperienceQueryDTO;
import com.xuan.dto.ExperienceWriteDTO;
import com.xuan.entity.CvExperience;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.vo.ExperiencePublicVO;
/** 全站展示与后台维护共用同一份资料。 */
public interface IExperienceService extends IService<CvExperience> {
    /** 只返回展示状态与公开字段。 */
    PageResult<ExperiencePublicVO> publicItems(ExperienceQueryDTO query);
    /** 检查查询权限的ALL范围后分页读取。 */
    PageResult<CvExperience> listItems(ExperienceQueryDTO query);
    /** 一致快照读取资料和业务版本。 */
    ResourceVersion<CvExperience> detail(long id);
    /** 锁操作者后创建，成功日志与资料一起提交。 */
    ResourceVersion<CvExperience> create(ExperienceWriteDTO request);
    /** 资源锁后重查原会话、ALL和版本，完整替换。 */
    ResourceVersion<CvExperience> update(long id, ExperienceWriteDTO request, String revision);
    /** 资源锁后核对版本，再物理删除元数据。 */
    void delete(long id, String revision);
}
