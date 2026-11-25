package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.ExperienceQueryDTO;
import com.xuan.entity.CvExperience;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 简历经历数据访问接口
 */
@Mapper
public interface ExperienceMapper extends BaseMapper<CvExperience> {

    /**
     * 分页查询经历列表，公开查询仅返回启用记录。
     *
     * @param page 分页参数
     * @param query 查询条件
     * @param publicOnly 是否仅查公开记录
     * @return 经历分页结果
     */
    IPage<CvExperience> resourcePage(IPage<CvExperience> page,
            @Param("query") ExperienceQueryDTO query,
            @Param("publicOnly") boolean publicOnly);

    /**
     * 加行锁查询经历记录，调用方须处于写事务内。
     *
     * @param id 经历 ID
     * @return 锁定的经历记录，不存在时返回 null
     */
    CvExperience lockById(@Param("id") long id);

    /**
     * 完整更新经历资料，显式写入可空字段以支持清空。
     *
     * @param row 待更新的经历实体
     * @return 受影响行数
     */
    int replace(@Param("row") CvExperience row);

    /**
     * 更新经历展示状态
     *
     * @param id 经历 ID
     * @param status 目标状态
     * @return 受影响行数
     */
    int updateStatus(@Param("id") long id, @Param("status") int status);
}
