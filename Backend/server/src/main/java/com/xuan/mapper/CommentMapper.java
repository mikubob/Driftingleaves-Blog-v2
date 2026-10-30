package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.entity.BlogComment;
import com.xuan.entity.BlogSiteConfig;
import com.xuan.model.CommentRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 评论范围落到 SQL，所有写入都先拿配置锁、文章锁，再锁评论。 */
@Mapper
public interface CommentMapper extends BaseMapper<BlogComment> {
    /** 只锁 comment 配置，不顺带实现站点配置管理；锁保持到事务结束。 */
    BlogSiteConfig lockConfig();

    /** 父节点已经确认完整祖先链可见，这里只分页它的直接回复；null 查根节点。 */
    IPage<CommentRow> publicPage(IPage<CommentRow> page, @Param("article") long article,
                                @Param("parent") Long parent);

    /** 本人只按 user 筛选；后台的 author 是本次 query 独立的文章作者范围。 */
    IPage<CommentRow> commentPage(IPage<CommentRow> page, @Param("article") Long article,
                                 @Param("user") Long user, @Param("status") Integer status,
                                 @Param("author") Long author);

    /** 查询、审核、管理删除分别传自己的 author 范围；本人删除额外固定 user。 */
    CommentRow scoped(@Param("id") long id, @Param("author") Long author,
                      @Param("user") Long user, @Param("lock") boolean lock);

    /** 锁内写审核或占位结果，SQL 再限制当前操作范围，不改文章、作者和父节点。 */
    int change(@Param("comment") BlogComment comment, @Param("author") Long author, @Param("user") Long user);
}
