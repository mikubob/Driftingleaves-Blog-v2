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
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公开条件、操作范围和排序都写进文章 SQL，正文和关联由业务事务一起保存。
 */
@Mapper
public interface ArticleMapper extends BaseMapper<BlogArticle> {

    /**
     * 列表和总数用同样的条件，只有搜关键词时才读取正文。
     *
     * @param page 分页容器，保留超末页页码
     * @param filter 已校验的PUBLIC/作者范围及AND过滤
     * @return 同一过滤条件的元数据分页
     */
    IPage<ArticleRow> articlePage(IPage<ArticleRow> page, @Param("filter") ArticleFilter filter);

    /**
     * 后台详情按当前操作的作者范围查，lock=true 时调用方要有 READ COMMITTED 写事务。
     *
     * @param id 文章ID
     * @param author SELF作者ID，ALL为null
     * @param lock 是否取得文章行锁；调用方先锁文件
     * @return 范围内文章，缺失或越界为null
     */
    ArticleRow scoped(@Param("id") long id, @Param("author") Long author, @Param("lock") boolean lock);

    /**
     * slug 区分大小写且只查公开文章，加锁后才能确定阅读计数和下架谁先提交。
     *
     * @param slug 大小写敏感标识
     * @param now 服务端当前UTC
     * @param lock 是否仅锁文章行以确定公开阅读顺序
     * @return PUBLIC文章，其他情况为null
     */
    ArticleRow publicBySlug(@Param("slug") String slug, @Param("now") LocalDateTime now, @Param("lock") boolean lock);

    /** 评论和点赞也复用文章 PUBLIC 条件；写入先用 scoped 锁文章，再按最新状态查这里。 */
    ArticleRow publicById(@Param("id") long id, @Param("now") LocalDateTime now);

    /**
     * 正文要和文章资料在同一快照或行锁事务里读取，不能只靠 HTML 缓存判断附件引用。
     *
     * @param id 文章ID
     * @return 文章正文，没查到为 null
     */
    BlogArticleContent content(@Param("id") long id);

    /**
     * 关联返回当前字典资料，标签按 ID 从小到大排。
     *
     * @param id 文章ID
     * @return 当前完整标签资料，空为[]
     */
    List<BlogTag> tags(@Param("id") long id);

    /**
     * 拿到文章锁后，锁住当前标签关联及字典，避免检查版本后标签又被级联删除。
     *
     * @param id 已持有行锁的文章ID
     * @return 升序完整标签ID，锁保持至事务结束
     */
    List<Long> lockCurrentTags(@Param("id") long id);

    /**
     * 取当前分类，是否隐藏由应用返回前台资料时处理。
     *
     * @param id 分类ID
     * @return 当前分类或null
     */
    BlogCategory category(@Param("id") long id);

    /**
     * 持有文章锁后再锁分类，避免检查通过后分类被删而只写了一半关联。
     *
     * @param id 候选分类ID
     * @return 存在的分类ID或null，共享锁保持至提交
     */
    Long lockCategory(@Param("id") long id);

    /**
     * 标签按 ID 升序逐个加锁，不存在由业务报 400，不能吞掉外键异常。
     *
     * @param id 候选标签ID，调用方按升序调用
     * @return 存在的标签ID或null
     */
    Long lockTag(@Param("id") long id);

    /**
     * 文件按 ID 升序锁定，而且要早于文章锁；删除文件和写引用都遵守这个顺序。
     *
     * @param id 文件ID，调用方按升序调用
     * @return 锁定元数据或null，锁保持至提交
     */
    BlogFile lockFile(@Param("id") long id);

    /**
     * 拿到文件锁后，在 SQL 里检查 file:query 的上传者范围，文章的 ALL 不能扩大文件的 SELF。
     *
     * @param id 已提前锁定的文件ID
     * @param uploader 文件SELF上传者ID，ALL为null
     * @return 范围内文件或null
     */
    BlogFile scopedFile(@Param("id") long id, @Param("uploader") Long uploader);

    /**
     * 替换全部可写资料，SQL 还要限制本次操作范围，不改作者和计数。
     *
     * @param article 已通过锁内版本及状态检查的候选元数据
     * @param author 本次操作的SELF作者ID，ALL为null
     * @return 受影响文章数
     */
    int updateArticle(@Param("article") BlogArticle article, @Param("author") Long author);

    /**
     * 文章资料、服务端正文和标签要在同一事务一起提交。
     *
     * @param id 新建或已锁定文章ID
     * @param markdown 原始Markdown
     * @param html 服务端安全HTML缓存
     */
    void saveContent(@Param("id") long id, @Param("markdown") String markdown, @Param("html") String html);

    /**
     * 先清旧关联，再逐条插入新关联，任何失败都回滚整篇文章的修改。
     *
     * @param id 已锁定文章ID
     */
    void clearTags(@Param("id") long id);

    /**
     * 文章标签用完整的两列主键插入，不能虚构单 ID 的增删改查。
     *
     * @param id 文章ID
     * @param tag 已验证并持锁的标签ID
     */
    void addTag(@Param("id") long id, @Param("tag") long tag);

    /**
     * 已锁住公开文章后，原子增加一次阅读，保留原修改时间。
     *
     * @param id 已锁定PUBLIC文章ID
     */
    void incrementViews(@Param("id") long id);

    /**
     * 按 UTC 月份倒序分页，只数公开文章，total 表示月份数，不是文章数。
     *
     * @param page 月份分页容器
     * @param now 服务端当前UTC
     * @return 月份倒序分页和月份总数
     */
    IPage<ArchiveVO> archives(IPage<ArchiveVO> page, @Param("now") LocalDateTime now);

    /**
     * 按发布时间和 ID 找上下篇，不读取或修改候选正文，也不增加它们的计数。
     *
     * @param article 当前PUBLIC文章
     * @param earlier true取更早最近文章，false取更晚最近文章
     * @param now 服务端当前UTC
     * @return 相邻公开导航或null
     */
    Navigation adjacent(@Param("article") BlogArticle article, @Param("earlier") boolean earlier,
            @Param("now") LocalDateTime now);

    /**
     * 同一个可展示分类里最多推荐六篇公开文章，排除当前文章。
     *
     * @param article 当前PUBLIC文章
     * @param now 服务端当前UTC
     * @return 发布时间和ID倒序的公开导航列表
     */
    List<Navigation> related(@Param("article") BlogArticle article, @Param("now") LocalDateTime now);

    /**
     * 指标由服务端枚举选择，本月只筛首次发布时间，热度仍用累计计数。
     *
     * @param from UTC候选月份下界，site为null
     * @param until UTC候选月份上界，site为null
     * @param now 服务端当前UTC
     * @param likes true按当前点赞，false按累计阅读
     * @param limit 已校验的1..20
     * @return 热度、首次发布时间和ID倒序的PUBLIC文章
     */
    List<ArticleRow> rankings(@Param("from") LocalDateTime from, @Param("until") LocalDateTime until,
            @Param("now") LocalDateTime now, @Param("likes") boolean likes, @Param("limit") int limit);

    /**
     * 这里只取当前文章评论的父子关系和状态，应用逐层查祖先，不受 SQL 递归深度限制。
     *
     * @param id 当前文章ID
     * @return 完整评论链结构，不含正文和私人资料
     */
    List<CommentNode> commentNodes(@Param("id") long id);

    /**
     * 计数只要父子关系和状态，不带评论正文或作者私人资料。
     */
    @Data
    class CommentNode {

        private Long commentId;
        private Long parentId;
        private Integer status;
    }
}
