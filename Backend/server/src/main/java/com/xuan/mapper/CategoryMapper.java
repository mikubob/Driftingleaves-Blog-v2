package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.CategoryQueryDTO;
import com.xuan.entity.BlogCategory;
import org.apache.ibatis.annotations.Param;

/**
 * 分类查询和维护放在这里，删除后的文章关联交给外键处理。
 */
public interface CategoryMapper extends BaseMapper<BlogCategory> {

    /** 列表和总数用同样的条件；公开查询只保留展示中的分类。 */
    IPage<BlogCategory> categoryPage(IPage<BlogCategory> page, @Param("query") CategoryQueryDTO query,
            @Param("publicOnly") boolean publicOnly);

    /** 先锁住分类再检查版本，调用方要在写事务里使用。 */
    BlogCategory lockById(@Param("id") long id);

    /** 把可空说明也写进去，避免清空说明时被默认更新策略忽略。 */
    int updateCategory(@Param("category") BlogCategory category);
}
