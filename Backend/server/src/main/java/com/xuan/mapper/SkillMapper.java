package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.entity.CvSkill;
import org.apache.ibatis.annotations.Param;

/**
 * 技能数据接口，列表和总数使用相同条件，写入时先锁操作者再锁记录。
 */
public interface SkillMapper extends BaseMapper<CvSkill> {

    /** 分页查询技能，公开查询只取展示中的记录，关键词按普通文字匹配。 */
    IPage<CvSkill> resourcePage(IPage<CvSkill> page, @Param("query") DisplayQueryDTO query,
            @Param("publicOnly") boolean publicOnly);

    /** 根据id锁住记录，拿到锁后由调用方重新检查原会话、ALL 范围和版本。 */
    CvSkill lockById(@Param("id") long id);

    /** 更新记录，可空字段传入 NULL 时也要写入，避免清空操作被默认更新策略忽略。 */
    int replace(@Param("row") CvSkill row);
}
