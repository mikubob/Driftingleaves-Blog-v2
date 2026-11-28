package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.entity.BlogComment;
import com.xuan.entity.BlogSiteConfig;
import com.xuan.mapper.ArticleMapper.CommentNode;
import com.xuan.model.CommentRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 文章评论数据访问接口
 */
@Mapper
public interface CommentMapper extends BaseMapper<BlogComment> {

    /**
     * 加行锁查询 comment 站点配置，调用方须处于写事务内。
     *
     * @return 评论配置记录
     */
    BlogSiteConfig lockConfig();

    /**
     * 分页查询文章下对当前读者可见的顶层根评论节点，按创建时间倒序排列。
     *
     * @param page 分页参数
     * @param article 文章 ID
     * @param viewer 当前登录用户 ID，匿名访问为 null
     * @return 可见根评论节点分页结果
     */
    IPage<CommentNode> visibleRootPage(IPage<CommentNode> page,
            @Param("article") long article,
            @Param("viewer") Long viewer);

    /**
     * 批量查询文章下指定根评论的全部回复节点，走 idx_comment_root 索引。
     *
     * @param article 文章 ID
     * @param rootIds 根评论 ID 集合
     * @return 回复节点列表
     */
    List<CommentNode> nodesByRoots(@Param("article") long article,
            @Param("rootIds") Collection<Long> rootIds);

    /**
     * 查询单棵评论会话树的全部节点（根节点及全部回复），用于回复分页、会话详情与回复校验。
     *
     * @param article 文章 ID
     * @param rootId 会话根评论 ID
     * @return 会话树节点列表
     */
    List<CommentNode> treeNodes(@Param("article") long article,
            @Param("rootId") long rootId);

    /**
     * 批量查询文章下多棵评论会话树的全部节点（根节点及全部回复），供本人评论列表计算可见性。
     *
     * @param article 文章 ID
     * @param rootIds 会话根评论 ID 集合
     * @return 多棵会话树节点列表
     */
    List<CommentNode> treeNodesByRoots(@Param("article") long article,
            @Param("rootIds") Collection<Long> rootIds);

    /**
     * 加行锁查询单棵评论会话树的全部节点，调用方须先持有文章行锁。
     *
     * @param article 文章 ID
     * @param rootId 会话根评论 ID
     * @return 锁定的会话树节点列表
     */
    List<CommentNode> lockTree(@Param("article") long article,
            @Param("rootId") long rootId);

    /**
     * 统计指定根评论之前对当前用户可见的根评论数量（前台按时间倒序），用于计算跳转页码。
     *
     * @param article 文章 ID
     * @param viewer 当前登录用户 ID
     * @param createTime 目标根评论创建时间
     * @param commentId 目标根评论 ID
     * @return 排在目标之前的可见根评论条数
     */
    long visibleRootRank(@Param("article") long article,
            @Param("viewer") long viewer,
            @Param("createTime") LocalDateTime createTime,
            @Param("commentId") long commentId);

    /**
     * 根据已筛选可见的评论 ID 列表查询前台评论详情
     *
     * @param article 文章 ID
     * @param ids 评论 ID 列表
     * @param replies true 按回复正序排列，false 按根评论倒序排列
     * @return 评论记录列表
     */
    List<CommentRow> publicRows(@Param("article") long article,
            @Param("ids") List<Long> ids,
            @Param("replies") boolean replies);

    /**
     * 分页查询本人评论列表，文章公开时返回标题与 slug。
     *
     * @param page 分页参数
     * @param article 文章 ID，可为空
     * @param user 评论作者用户 ID
     * @param status 审核状态，可为空
     * @param now 当前 UTC 时间
     * @return 评论分页结果
     */
    IPage<CommentRow> commentPage(IPage<CommentRow> page,
            @Param("article") Long article,
            @Param("user") Long user,
            @Param("status") Integer status,
            @Param("now") LocalDateTime now);

    /**
     * 分页查询后台评论列表，parent 为 0 查根节点，为 null 保留平铺，author 限定所属文章作者范围。
     *
     * @param page 分页参数
     * @param article 文章 ID，可为空
     * @param user 评论作者用户 ID，可为空
     * @param status 审核状态，可为空
     * @param author SELF 范围文章作者 ID，ALL 范围为 null
     * @param parent 父评论 ID，可为空
     * @return 后台评论分页结果
     */
    IPage<CommentRow> adminPage(IPage<CommentRow> page,
            @Param("article") Long article,
            @Param("user") Long user,
            @Param("status") Integer status,
            @Param("author") Long author,
            @Param("parent") Long parent);

    /**
     * 按权限范围查询单条评论，lock 为 true 时加行锁。
     *
     * @param id 评论 ID
     * @param author SELF 范围文章作者 ID，ALL 范围为 null
     * @param user 评论作者用户 ID（本人操作时传入）
     * @param lock 是否加行锁
     * @return 评论记录，不存在或越界时返回 null
     */
    CommentRow scoped(@Param("id") long id,
            @Param("author") Long author,
            @Param("user") Long user,
            @Param("lock") boolean lock);

    /**
     * 更新评论审核状态，并在 SQL 中限制操作范围。
     *
     * @param comment 待更新的评论实体
     * @param author SELF 范围文章作者 ID，ALL 范围为 null
     * @param user 评论作者用户 ID，可为空
     * @return 受影响行数
     */
    int change(@Param("comment") BlogComment comment,
            @Param("author") Long author,
            @Param("user") Long user);
}
