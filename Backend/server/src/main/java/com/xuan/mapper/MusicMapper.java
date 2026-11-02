package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.entity.BlogMusic;
import org.apache.ibatis.annotations.Param;
/**
 * 查询与总数使用同样条件，写事务先锁操作者再锁资源。
 */
public interface MusicMapper extends BaseMapper<BlogMusic> {
    /** 公开查询只取展示状态，关键词按字面包含。 */
    IPage<BlogMusic> resourcePage(IPage<BlogMusic> page, @Param("query") DisplayQueryDTO query, @Param("publicOnly") boolean publicOnly);
    /** 持有资源锁后，调用方重新检查原会话、ALL范围和版本。 */
    BlogMusic lockById(@Param("id") long id);
    /** 显式覆盖可空字段，不能让NULL被默认更新策略跳过。 */
    int replace(@Param("row") BlogMusic row);

    /** 删除文件时检查全部状态，隐藏曲目的引用同样保留。 */
    boolean hasFileReference(@Param("url") String url);

    /** 文件锁后锁定一个当前公开引用，状态变化与读取按同一音乐行锁排序。 */
    BlogMusic lockPublicFileReference(@Param("url") String url);
}
