package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.TagQueryDTO;
import com.xuan.entity.BlogTag;
import com.xuan.vo.TagPublicDetailVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 文章标签数据访问接口
 */
@Mapper
public interface TagMapper extends BaseMapper<BlogTag> {

    /**
     * 根据 slug 查询标签详情及关联公开文章数
     *
     * @param slug 标签 slug
     * @param now 当前 UTC 时间
     * @return 公开标签详情，不存在时返回 null
     */
    TagPublicDetailVO publicBySlug(@Param("slug") String slug, @Param("now") LocalDateTime now);

    /**
     * 分页查询标签及关联公开文章数
     *
     * @param page 分页参数
     * @param now 当前 UTC 时间
     * @return 公开标签分页结果
     */
    IPage<TagPublicDetailVO> publicPage(IPage<TagPublicDetailVO> page, @Param("now") LocalDateTime now);

    /**
     * 分页查询后台标签列表，按标签 ID 升序排列。
     *
     * @param page 分页参数
     * @param query 查询条件
     * @return 标签分页结果
     */
    IPage<BlogTag> tagPage(IPage<BlogTag> page, @Param("query") TagQueryDTO query);

    /**
     * 加行锁查询标签记录，调用方须处于写事务内。
     *
     * @param id 标签 ID
     * @return 锁定的标签记录，不存在时返回 null
     */
    BlogTag lockById(@Param("id") long id);
}
