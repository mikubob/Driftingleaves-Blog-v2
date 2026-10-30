package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.entity.BlogMusic;
import org.apache.ibatis.annotations.Param;
/** 查询与总数使用同样条件，写事务先锁操作者再锁资源。 */
public interface MusicMapper extends BaseMapper<BlogMusic> {
    /** 公开查询只取展示状态，关键词按字面包含。 */
    IPage<BlogMusic> resourcePage(IPage<BlogMusic> page, @Param("query") DisplayQueryDTO query, @Param("publicOnly") boolean publicOnly);
    /** 持有资源锁后，调用方重新检查原会话、ALL范围和版本。 */
    BlogMusic lockById(@Param("id") long id);
    /** 显式覆盖可空字段，不能让NULL被默认更新策略跳过。 */
    int replace(@Param("row") BlogMusic row);
}
