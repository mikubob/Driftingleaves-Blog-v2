package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.entity.BlogMusic;
import org.apache.ibatis.annotations.Param;
/**
 * 音乐数据接口，列表和总数使用相同条件，写入时先锁操作者再锁记录。
 */
public interface MusicMapper extends BaseMapper<BlogMusic> {
    /** 分页查询音乐，公开查询只取展示中的记录，关键词按普通文字匹配。 */
    IPage<BlogMusic> resourcePage(IPage<BlogMusic> page, @Param("query") DisplayQueryDTO query, @Param("publicOnly") boolean publicOnly);
    /** 根据id锁住记录，拿到锁后由调用方重新检查原会话、ALL 范围和版本。 */
    BlogMusic lockById(@Param("id") long id);
    /** 更新记录，可空字段传入 NULL 时也要写入，避免清空操作被默认更新策略忽略。 */
    int replace(@Param("row") BlogMusic row);

    /** 删除文件时检查全部状态，隐藏曲目的引用同样保留。 */
    boolean hasFileReference(@Param("url") String url);

    /** 文件锁后锁定一个当前公开引用，状态变化与读取按同一音乐行锁排序。 */
    BlogMusic lockPublicFileReference(@Param("url") String url);
    /** 全部目标加锁并通过版本检查后，只更新状态。 */
    int updateStatus(@Param("id") long id, @Param("status") int status);

}
