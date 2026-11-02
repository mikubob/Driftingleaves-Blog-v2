package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.TagQueryDTO;
import com.xuan.entity.BlogTag;
import com.xuan.vo.TagPublicDetailVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 标签数据接口，只维护标签资料，文章标签关联由文章业务保存。
 */
public interface TagMapper extends BaseMapper<BlogTag> {

    /** 文章按关联表复合键计数，不存在的 slug 返回 null。 */
    TagPublicDetailVO publicBySlug(@Param("slug") String slug, @Param("now") LocalDateTime now);

    /** 按名称字面查找并按 ID 升序分页，总数也用同一条件。 */
    IPage<BlogTag> tagPage(IPage<BlogTag> page, @Param("query") TagQueryDTO query);

    /** 在写事务里锁住标签，等待结束后再检查版本和当前权限。 */
    BlogTag lockById(@Param("id") long id);
}
