package com.xuan.mapper;

import com.xuan.vo.LikeStateVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 点赞按完整复合键操作，不套用单 ID CRUD，也不维护第二套计数。 */
@Mapper
public interface ArticleLikeMapper {
    /** 同一条 SQL 返回当前用户是否点赞和真实明细总数。 */
    LikeStateVO state(@Param("article") long article, @Param("user") long user);
    /** 已持有文章锁且确认不存在该复合键，唯一约束仍保留。 */
    void add(@Param("article") long article, @Param("user") long user);
    /** 不存在也成功，不能删到其他用户的点赞。 */
    void remove(@Param("article") long article, @Param("user") long user);
}
