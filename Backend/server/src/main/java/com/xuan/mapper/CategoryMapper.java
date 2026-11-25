package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.CategoryQueryDTO;
import com.xuan.entity.BlogCategory;
import com.xuan.vo.CategoryPublicDetailVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 文章分类数据访问接口
 */
@Mapper
public interface CategoryMapper extends BaseMapper<BlogCategory> {

    /**
     * 根据 slug 查询公开展示中的分类详情及公开文章数
     *
     * @param slug 分类 slug
     * @param now 当前 UTC 时间
     * @return 公开分类详情，隐藏或不存在时返回 null
     */
    CategoryPublicDetailVO publicBySlug(@Param("slug") String slug, @Param("now") LocalDateTime now);

    /**
     * 分页查询公开展示中的分类及公开文章数
     *
     * @param page 分页参数
     * @param now 当前 UTC 时间
     * @return 公开分类分页结果
     */
    IPage<CategoryPublicDetailVO> publicPage(IPage<CategoryPublicDetailVO> page, @Param("now") LocalDateTime now);

    /**
     * 分页查询后台分类列表
     *
     * @param page 分页参数
     * @param query 查询条件
     * @return 分类分页结果
     */
    IPage<BlogCategory> categoryPage(IPage<BlogCategory> page, @Param("query") CategoryQueryDTO query);

    /**
     * 加行锁查询分类记录，调用方须处于写事务内。
     *
     * @param id 分类 ID
     * @return 锁定的分类记录，不存在时返回 null
     */
    BlogCategory lockById(@Param("id") long id);

    /**
     * 更新分类展示状态
     *
     * @param id 分类 ID
     * @param status 目标状态
     * @return 受影响行数
     */
    int updateStatus(@Param("id") long id, @Param("status") int status);

    /**
     * 更新分类资料，显式写入可空字段以支持清空说明。
     *
     * @param category 待更新的分类实体
     * @return 受影响行数
     */
    int updateCategory(@Param("category") BlogCategory category);
}
