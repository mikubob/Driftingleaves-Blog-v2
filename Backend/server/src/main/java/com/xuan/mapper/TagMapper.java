package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.TagQueryDTO;
import com.xuan.entity.BlogTag;
import org.apache.ibatis.annotations.Param;

/** 这里只维护标签字典，文章里的标签集合仍由文章业务一起保存。 */
public interface TagMapper extends BaseMapper<BlogTag> {
    /** 按名称字面查找并按 ID 升序分页，总数也用同一条件。 */
    IPage<BlogTag> tagPage(IPage<BlogTag> page, @Param("query") TagQueryDTO query);

    /** 在写事务里锁住标签，等待结束后再检查版本和当前权限。 */
    BlogTag lockById(@Param("id") long id);
}
