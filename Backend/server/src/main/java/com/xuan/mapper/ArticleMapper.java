package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.entity.BlogArticle;
import com.xuan.entity.BlogArticleContent;
import com.xuan.entity.BlogCategory;
import com.xuan.entity.BlogFile;
import com.xuan.entity.BlogTag;
import com.xuan.model.ArticleFilter;
import com.xuan.model.ArticleRow;
import com.xuan.vo.ArchiveVO;
import com.xuan.vo.ArticlePublicVO.Navigation;
import com.xuan.vo.AuthorPublicVO;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章数据访问接口
 */
@Mapper
public interface ArticleMapper extends BaseMapper<BlogArticle> {

    /**
     * 分页查询文章列表，列表和总数使用相同筛选条件，仅搜索关键词时关联正文。
     *
     * @param page 分页参数
     * @param filter 文章筛选条件
     * @return 文章分页结果
     */
    IPage<ArticleRow> articlePage(IPage<ArticleRow> page, @Param("filter") ArticleFilter filter);

    /**
     * 分页查询有公开文章的作者列表
     *
     * @param page 分页参数
     * @param now 当前 UTC 时间
     * @return 作者公开资料分页结果
     */
    IPage<AuthorPublicVO> authorPage(IPage<AuthorPublicVO> page, @Param("now") LocalDateTime now);

    /**
     * 根据用户 ID 查询作者公开资料，无公开文章时返回 null。
     *
     * @param id 作者用户 ID
     * @param now 当前 UTC 时间
     * @return 作者公开资料
     */
    AuthorPublicVO publicAuthor(@Param("id") long id, @Param("now") LocalDateTime now);

    /**
     * 分页查询当前用户点赞的公开文章列表
     *
     * @param page 分页参数
     * @param user 用户 ID
     * @param now 当前 UTC 时间
     * @return 点赞文章分页结果
     */
    IPage<ArticleRow> likedPage(IPage<ArticleRow> page, @Param("user") long user,
            @Param("now") LocalDateTime now);

    /**
     * 加锁查询文章下的完整评论节点，调用方须先持有文章行锁。
     *
     * @param id 文章 ID
     * @return 评论节点列表
     */
    List<CommentNode> lockComments(@Param("id") long id);

    /**
     * 按文章 ID 与评论 ID 删除单条评论，调用方按从叶到根顺序调用。
     *
     * @param id 文章 ID
     * @param comment 评论 ID
     */
    void deleteComment(@Param("id") long id, @Param("comment") long comment);

    /**
     * 永久删除回收站中的文章，并在 SQL 中限制操作者作者范围。
     *
     * @param id 文章 ID
     * @param author SELF 范围作者 ID，ALL 范围为 null
     */
    void deleteScoped(@Param("id") long id, @Param("author") Long author);

    /**
     * 按权限范围查询文章，lock 为 true 时加行锁（调用方须在事务内并先锁文件）。
     *
     * @param id 文章 ID
     * @param author SELF 范围作者 ID，ALL 范围为 null
     * @param lock 是否加行锁
     * @return 范围内的文章记录，不存在或越界时返回 null
     */
    ArticleRow scoped(@Param("id") long id, @Param("author") Long author, @Param("lock") boolean lock);

    /**
     * 根据区分大小写的 slug 查询公开文章，lock 为 true 时加行锁。
     *
     * @param slug 文章 slug
     * @param now 当前 UTC 时间
     * @param lock 是否加行锁
     * @return 公开文章记录，非公开或不存在时返回 null
     */
    ArticleRow publicBySlug(@Param("slug") String slug, @Param("now") LocalDateTime now, @Param("lock") boolean lock);

    /**
     * 根据文章 ID 查询公开文章
     *
     * @param id 文章 ID
     * @param now 当前 UTC 时间
     * @return 公开文章记录，非公开或不存在时返回 null
     */
    ArticleRow publicById(@Param("id") long id, @Param("now") LocalDateTime now);

    /**
     * 批量查询公开文章的最新阅读量与点赞数
     *
     * @param ids 文章 ID 列表
     * @param now 当前 UTC 时间
     * @return 公开文章计数列表
     */
    List<ArticleRow> publicCounts(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);

    /**
     * 根据文章 ID 查询正文内容
     *
     * @param id 文章 ID
     * @return 文章正文记录，不存在时返回 null
     */
    BlogArticleContent content(@Param("id") long id);

    /**
     * 根据文章 ID 查询关联的标签列表，按标签 ID 升序排列。
     *
     * @param id 文章 ID
     * @return 标签列表
     */
    List<BlogTag> tags(@Param("id") long id);

    /**
     * 加共享锁查询文章当前关联的标签 ID 列表，防止检查版本后标签被级联删除。
     *
     * @param id 文章 ID
     * @return 按升序排列的标签 ID 列表
     */
    List<Long> lockCurrentTags(@Param("id") long id);

    /**
     * 根据分类 ID 查询分类记录
     *
     * @param id 分类 ID
     * @return 分类记录，不存在时返回 null
     */
    BlogCategory category(@Param("id") long id);

    /**
     * 加共享锁检查分类是否存在
     *
     * @param id 分类 ID
     * @return 存在的分类 ID，不存在时返回 null
     */
    Long lockCategory(@Param("id") long id);

    /**
     * 加共享锁检查标签是否存在，调用方按标签 ID 升序调用。
     *
     * @param id 标签 ID
     * @return 存在的标签 ID，不存在时返回 null
     */
    Long lockTag(@Param("id") long id);

    /**
     * 加行锁查询文件记录，调用方按文件 ID 升序锁定且早于文章锁。
     *
     * @param id 文件 ID
     * @return 文件记录，不存在时返回 null
     */
    BlogFile lockFile(@Param("id") long id);

    /**
     * 按上传者权限范围查询文件记录
     *
     * @param id 文件 ID
     * @param uploader SELF 范围上传者 ID，ALL 范围为 null
     * @return 范围内的文件记录，不存在或越界时返回 null
     */
    BlogFile scopedFile(@Param("id") long id, @Param("uploader") Long uploader);

    /**
     * 更新文章可写资料，并在 SQL 中限制操作者作者范围。
     *
     * @param article 待更新的文章实体
     * @param author SELF 范围作者 ID，ALL 范围为 null
     * @return 受影响行数
     */
    int updateArticle(@Param("article") BlogArticle article, @Param("author") Long author);

    /**
     * 保存或更新文章 Markdown 正文及服务端渲染的安全 HTML
     *
     * @param id 文章 ID
     * @param markdown Markdown 原文
     * @param html 清理后的安全 HTML
     */
    void saveContent(@Param("id") long id, @Param("markdown") String markdown, @Param("html") String html);

    /**
     * 清空文章的全部标签关联
     *
     * @param id 文章 ID
     */
    void clearTags(@Param("id") long id);

    /**
     * 新增一条文章与标签的复合主键关联记录
     *
     * @param id 文章 ID
     * @param tag 标签 ID
     */
    void addTag(@Param("id") long id, @Param("tag") long tag);

    /**
     * 原子累加一次公开文章阅读量，保持原更新时间不变。
     *
     * @param id 文章 ID
     */
    void incrementViews(@Param("id") long id);

    /**
     * 按 UTC 月份倒序分页查询公开文章归档
     *
     * @param page 分页参数
     * @param now 当前 UTC 时间
     * @return 归档分页结果
     */
    IPage<ArchiveVO> archives(IPage<ArchiveVO> page, @Param("now") LocalDateTime now);

    /**
     * 查询当前公开文章的上一篇或下一篇导航
     *
     * @param article 当前公开文章
     * @param earlier true 查询更早一篇，false 查询更晚一篇
     * @param now 当前 UTC 时间
     * @return 相邻文章导航，无相邻文章时返回 null
     */
    Navigation adjacent(@Param("article") BlogArticle article, @Param("earlier") boolean earlier,
            @Param("now") LocalDateTime now);

    /**
     * 查询同分类下的相关公开文章推荐（最多 6 篇，排除当前文章）
     *
     * @param article 当前公开文章
     * @param now 当前 UTC 时间
     * @return 相关文章导航列表
     */
    List<Navigation> related(@Param("article") BlogArticle article, @Param("now") LocalDateTime now);

    /**
     * 查询热门公开文章排行
     *
     * @param from 统计开始时间（全站排行时为 null）
     * @param until 统计结束时间（全站排行时为 null）
     * @param now 当前 UTC 时间
     * @param likes true 按点赞数排序，false 按阅读量排序
     * @param limit 返回条数上限
     * @return 热门文章列表
     */
    List<ArticleRow> rankings(@Param("from") LocalDateTime from, @Param("until") LocalDateTime until,
            @Param("now") LocalDateTime now, @Param("likes") boolean likes, @Param("limit") int limit);

    /**
     * 查询文章下全部评论的最小树节点信息，用于可见性校验。
     *
     * @param id 文章 ID
     * @return 评论节点列表
     */
    List<CommentNode> commentNodes(@Param("id") long id);

    /**
     * 批量查询多篇文章的评论节点信息
     *
     * @param ids 文章 ID 列表
     * @return 评论节点列表
     */
    List<CommentNode> commentNodesByArticles(@Param("ids") List<Long> ids);

    /**
     * 评论可见性校验节点模型
     */
    @Data
    class CommentNode {

        private Long commentId;
        private Long articleId;
        private Long userId;
        private Long parentId;
        private Integer status;
    }
}
