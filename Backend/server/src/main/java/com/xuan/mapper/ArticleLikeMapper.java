package com.xuan.mapper;

import com.xuan.vo.LikeStateVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 文章点赞数据接口，按文章和用户的组合主键操作，不另存点赞计数。
 */
@Mapper
public interface ArticleLikeMapper {

    /** 同一条 SQL 返回当前用户是否点赞和真实明细总数。 */
    LikeStateVO state(@Param("article") long article, @Param("user") long user);

    /** 已持有文章锁且确认不存在该复合键，唯一约束仍保留。 */
    void add(@Param("article") long article, @Param("user") long user);

    /** 不存在也成功，不能删到其他用户的点赞。 */
    void remove(@Param("article") long article, @Param("user") long user);
}
