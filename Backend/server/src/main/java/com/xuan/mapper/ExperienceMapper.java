package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.ExperienceQueryDTO;
import com.xuan.entity.CvExperience;
import org.apache.ibatis.annotations.Param;

/**
 * 经历数据接口，列表和总数使用相同条件，写入时先锁操作者再锁记录。
 */
public interface ExperienceMapper extends BaseMapper<CvExperience> {

    /** 分页查询经历，公开查询只取展示中的记录，关键词按普通文字匹配。 */
    IPage<CvExperience> resourcePage(IPage<CvExperience> page, @Param("query") ExperienceQueryDTO query,
            @Param("publicOnly") boolean publicOnly);

    /** 根据id锁住记录，拿到锁后由调用方重新检查原会话、ALL 范围和版本。 */
    CvExperience lockById(@Param("id") long id);

    /** 更新记录，可空字段传入 NULL 时也要写入，避免清空操作被默认更新策略忽略。 */
    int replace(@Param("row") CvExperience row);
}
