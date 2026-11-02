package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.ExperienceQueryDTO;
import com.xuan.dto.ExperienceWriteDTO;
import com.xuan.entity.CvExperience;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.vo.ExperiencePublicVO;

import java.util.List;

/**
 * 经历业务接口，公开展示和后台管理使用同一份资料。
 */
public interface IExperienceService extends IService<CvExperience> {

    /** 查询公开经历列表，只返回展示中的记录和允许公开的字段。 */
    PageResult<ExperiencePublicVO> publicItems(ExperienceQueryDTO query);

    /** 分页查询后台经历，查询权限必须有 ALL 范围。 */
    PageResult<CvExperience> listItems(ExperienceQueryDTO query);

    /** 根据id查询经历详情及版本，资料和版本在同一个只读事务中读取。 */
    ResourceVersion<CvExperience> detail(long id);

    /** 新增经历，先锁住操作者，资料和成功日志一起提交。 */
    ResourceVersion<CvExperience> create(ExperienceWriteDTO request);

    /** 根据id更新经历，加锁后重新检查原会话、ALL 范围和版本，再完整替换资料。 */
    ResourceVersion<CvExperience> update(long id, ExperienceWriteDTO request, String revision);

    /** 根据id删除经历，加锁并检查版本后，从数据库删除记录。 */
    void delete(long id, String revision);
    /** 批量删除经历，单条也走此入口，所有数据库修改一起提交。 */
    void deleteBatch(List<BatchItemDTO> items);
}
