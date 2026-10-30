package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.entity.BlogPageView;
import com.xuan.dto.PageViewQueryDTO;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

/** 浏览明细只新增或删除，不能编辑历史身份与资源关联。 */
public interface PageViewMapper extends BaseMapper<BlogPageView> {
    /** 筛选及total一致，所有ID均由入口解析为正Long。 */
    IPage<BlogPageView> viewPage(IPage<BlogPageView> page, @Param("query") PageViewQueryDTO query,
            @Param("visitor") Long visitor, @Param("user") Long user, @Param("article") Long article,
            @Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);
    /** 显式清理前锁住目标记录，调用方之后重查原Token与ALL。 */
    BlogPageView lockById(@Param("id") long id);
}
