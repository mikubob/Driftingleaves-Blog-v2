package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.entity.HomeSocialLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 社交链接数据访问接口
 */
@Mapper
public interface SocialMapper extends BaseMapper<HomeSocialLink> {

    /**
     * 分页查询社交链接列表，公开查询仅返回启用记录。
     *
     * @param page 分页参数
     * @param query 查询条件
     * @param publicOnly 是否仅查公开记录
     * @return 社交链接分页结果
     */
    IPage<HomeSocialLink> resourcePage(IPage<HomeSocialLink> page,
            @Param("query") DisplayQueryDTO query,
            @Param("publicOnly") boolean publicOnly);

    /**
     * 查询全部公开展示的社交链接列表，按排序值升序排列。
     *
     * @return 公开社交链接列表
     */
    List<HomeSocialLink> publicItems();

    /**
     * 加行锁查询社交链接记录，调用方须处于写事务内。
     *
     * @param id 社交链接 ID
     * @return 锁定的社交链接记录，不存在时返回 null
     */
    HomeSocialLink lockById(@Param("id") long id);

    /**
     * 完整更新社交链接资料，显式写入可空字段以支持清空。
     *
     * @param row 待更新的社交链接实体
     * @return 受影响行数
     */
    int replace(@Param("row") HomeSocialLink row);

    /**
     * 更新社交链接展示状态
     *
     * @param id 社交链接 ID
     * @param status 目标状态
     * @return 受影响行数
     */
    int updateStatus(@Param("id") long id, @Param("status") int status);
}
