package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.entity.CvSkill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 简历技能数据访问接口
 */
@Mapper
public interface SkillMapper extends BaseMapper<CvSkill> {

    /**
     * 分页查询技能列表，公开查询仅返回启用记录。
     *
     * @param page 分页参数
     * @param query 查询条件
     * @param publicOnly 是否仅查公开记录
     * @return 技能分页结果
     */
    IPage<CvSkill> resourcePage(IPage<CvSkill> page,
            @Param("query") DisplayQueryDTO query,
            @Param("publicOnly") boolean publicOnly);

    /**
     * 加行锁查询技能记录，调用方须处于写事务内。
     *
     * @param id 技能 ID
     * @return 锁定的技能记录，不存在时返回 null
     */
    CvSkill lockById(@Param("id") long id);

    /**
     * 完整更新技能资料，显式写入可空字段以支持清空。
     *
     * @param row 待更新的技能实体
     * @return 受影响行数
     */
    int replace(@Param("row") CvSkill row);

    /**
     * 更新技能展示状态
     *
     * @param id 技能 ID
     * @param status 目标状态
     * @return 受影响行数
     */
    int updateStatus(@Param("id") long id, @Param("status") int status);
}
