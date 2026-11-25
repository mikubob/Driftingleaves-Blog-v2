package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.entity.BlogMusic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 音乐数据访问接口
 */
@Mapper
public interface MusicMapper extends BaseMapper<BlogMusic> {

    /**
     * 分页查询音乐列表，公开查询仅返回启用记录。
     *
     * @param page 分页参数
     * @param query 查询条件
     * @param publicOnly 是否仅查公开记录
     * @return 音乐分页结果
     */
    IPage<BlogMusic> resourcePage(IPage<BlogMusic> page,
            @Param("query") DisplayQueryDTO query,
            @Param("publicOnly") boolean publicOnly);

    /**
     * 加行锁查询音乐记录，调用方须处于写事务内。
     *
     * @param id 音乐 ID
     * @return 锁定的音乐记录，不存在时返回 null
     */
    BlogMusic lockById(@Param("id") long id);

    /**
     * 完整更新音乐资料，显式写入可空字段以支持清空。
     *
     * @param row 待更新的音乐实体
     * @return 受影响行数
     */
    int replace(@Param("row") BlogMusic row);

    /**
     * 检查是否存在任意状态曲目引用指定文件地址
     *
     * @param url 文件访问地址
     * @return 是否被引用
     */
    boolean hasFileReference(@Param("url") String url);

    /**
     * 加行锁查询一条引用指定文件地址的公开曲目
     *
     * @param url 文件访问地址
     * @return 锁定的公开曲目，无匹配时返回 null
     */
    BlogMusic lockPublicFileReference(@Param("url") String url);

    /**
     * 更新音乐展示状态
     *
     * @param id 音乐 ID
     * @param status 目标状态
     * @return 受影响行数
     */
    int updateStatus(@Param("id") long id, @Param("status") int status);
}
