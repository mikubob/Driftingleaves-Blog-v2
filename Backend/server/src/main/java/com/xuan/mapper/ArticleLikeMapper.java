package com.xuan.mapper;

import com.xuan.vo.LikeStateVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 文章点赞数据访问接口
 */
@Mapper
public interface ArticleLikeMapper {

    /**
     * 查询当前用户对文章的点赞状态及真实点赞总数
     *
     * @param article 文章 ID
     * @param user 用户 ID
     * @return 点赞状态与总数
     */
    LikeStateVO state(@Param("article") long article, @Param("user") long user);

    /**
     * 新增一条文章点赞记录，调用方须已持有文章行锁。
     *
     * @param article 文章 ID
     * @param user 用户 ID
     */
    void add(@Param("article") long article, @Param("user") long user);

    /**
     * 删除指定用户对文章的点赞记录
     *
     * @param article 文章 ID
     * @param user 用户 ID
     */
    void remove(@Param("article") long article, @Param("user") long user);
}
