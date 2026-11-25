package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.PageViewQueryDTO;
import com.xuan.entity.BlogPageView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 页面浏览记录数据访问接口
 */
@Mapper
public interface PageViewMapper extends BaseMapper<BlogPageView> {

    /**
     * 分页查询浏览记录列表
     *
     * @param page 分页参数
     * @param query 查询条件
     * @param visitor 访客 ID，可为空
     * @param user 用户 ID，可为空
     * @param article 文章 ID，可为空
     * @param begin 开始时间，可为空
     * @param end 结束时间，可为空
     * @return 浏览记录分页结果
     */
    IPage<BlogPageView> viewPage(IPage<BlogPageView> page,
            @Param("query") PageViewQueryDTO query,
            @Param("visitor") Long visitor,
            @Param("user") Long user,
            @Param("article") Long article,
            @Param("begin") LocalDateTime begin,
            @Param("end") LocalDateTime end);

    /**
     * 加行锁查询浏览记录，用于清理前校验。
     *
     * @param id 浏览记录 ID
     * @return 锁定的浏览记录，不存在时返回 null
     */
    BlogPageView lockById(@Param("id") long id);
}
