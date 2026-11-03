package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.entity.BlogComment;
import com.xuan.entity.BlogSiteConfig;
import com.xuan.model.CommentRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 评论数据接口，SQL 限制操作范围，写入时依次锁住配置、文章和评论。
 */
@Mapper
public interface CommentMapper extends BaseMapper<BlogComment> {

    /** 只锁 comment 配置，不顺带实现站点配置管理；锁保持到事务结束。 */
    BlogSiteConfig lockConfig();

    /** 分页查询直接回复，调用前先确认父评论及全部上级评论可见；父评论为 null 时查询根评论。 */
    IPage<CommentRow> publicPage(IPage<CommentRow> page, @Param("article") long article,
            @Param("parent") Long parent);

    /** 本人只按 user 筛选；后台的 author 是本次 query 独立的文章作者范围。 */
    IPage<CommentRow> commentPage(IPage<CommentRow> page, @Param("article") Long article,
            @Param("user") Long user, @Param("status") Integer status,
            @Param("author") Long author);

    /** 分页查询后台评论及直接回复标记；parent为0查根节点，null保留平铺，author始终限定文章范围。 */
    IPage<CommentRow> adminPage(IPage<CommentRow> page, @Param("article") Long article,
            @Param("user") Long user, @Param("status") Integer status,
            @Param("author") Long author, @Param("parent") Long parent);

    /** 查询、审核、管理删除分别传自己的 author 范围；本人删除额外固定 user。 */
    CommentRow scoped(@Param("id") long id, @Param("author") Long author,
            @Param("user") Long user, @Param("lock") boolean lock);

    /** 锁内写审核结果，SQL 再限制当前操作范围，不改文章、作者和父节点。 */
    int change(@Param("comment") BlogComment comment, @Param("author") Long author, @Param("user") Long user);
}
