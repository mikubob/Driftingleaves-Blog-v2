package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.entity.BlogComment;
import com.xuan.entity.BlogSiteConfig;
import com.xuan.model.CommentRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 评论数据接口，SQL 限制操作范围，写入时依次锁住配置、文章和评论。
 */
@Mapper
public interface CommentMapper extends BaseMapper<BlogComment> {

    /** 只锁 comment 配置，不顺带实现站点配置管理；锁保持到事务结束。 */
    BlogSiteConfig lockConfig();

    /** 读取已确认可见的当前页评论，根评论倒序、整组回复正序。 */
    List<CommentRow> publicRows(@Param("article") long article,
            @Param("ids") List<Long> ids, @Param("replies") boolean replies);

    /** 本人只按 user 筛选；后台的 author 是本次 query 独立的文章作者范围。 */
    IPage<CommentRow> commentPage(IPage<CommentRow> page, @Param("article") Long article,
            @Param("user") Long user, @Param("status") Integer status,
            @Param("author") Long author);

    /** 分页查询后台评论及直接回复标记；parent为0查根节点，null保留平铺，author始终限定文章范围。 */
    IPage<CommentRow> adminPage(IPage<CommentRow> page, @Param("article") Long article,
            @Param("user") Long user, @Param("status") Integer status,
            @Param("author") Long author, @Param("parent") Long parent);

    /** 查询、审核、管理删除分别传自己的 author 范围；本人查询和删除固定 user。 */
    CommentRow scoped(@Param("id") long id, @Param("author") Long author,
            @Param("user") Long user, @Param("lock") boolean lock);

    /** 锁内写审核结果，SQL 再限制当前操作范围，不改文章、作者和父节点。 */
    int change(@Param("comment") BlogComment comment, @Param("author") Long author, @Param("user") Long user);
}
