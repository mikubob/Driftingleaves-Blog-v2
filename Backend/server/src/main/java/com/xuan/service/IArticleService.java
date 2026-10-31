package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.AdminArticleQueryDTO;
import com.xuan.dto.ArticleQueryDTO;
import com.xuan.dto.ArticleWriteDTO;
import com.xuan.dto.PageQueryDTO;
import com.xuan.entity.BlogArticle;
import com.xuan.model.ArticleRow;
import com.xuan.result.PageResult;
import com.xuan.vo.ArchiveVO;
import com.xuan.vo.ArticleAdminVO;
import com.xuan.vo.ArticleProfileVO;
import com.xuan.vo.ArticlePublicDetailVO;
import com.xuan.vo.ArticlePublicVO;
import com.xuan.vo.AuthorPublicVO;
import com.xuan.vo.HotArticleVO;
import com.xuan.vo.LikedArticleVO;

import java.util.List;

/**
 * 文章资料、正文、标签、引用和版本一起处理，按约定在同一事务提交。
 */
public interface IArticleService extends IService<BlogArticle> {

    /** 公开列表只查符合公开条件的文章，所有筛选同时满足，分页和总数取同一快照。 */
    PageResult<ArticlePublicVO> publicArticles(ArticleQueryDTO query);

    /** 公开集合的静态片段，由 publicArticles 在同一事务补充当前计数。 */
    PageResult<ArticlePublicVO> cachedPublicArticles(ArticleQueryDTO query);

    /** 仅在 publicDetail 已持文章锁、校验当前正文及附件后从容器代理调用。 */
    ArticlePublicDetailVO cachedPublicDetail(ArticleRow row, String markdown, String html);

    /** 仅列出拥有 PUBLIC 文章的作者，按公开文章数倒序、作者 ID 升序分页。 */
    PageResult<AuthorPublicVO> publicAuthors(PageQueryDTO query);

    /** 作者详情只含公开资料，没有 PUBLIC 文章与用户不存在均返回 404。 */
    AuthorPublicVO publicAuthor(long userId);

    /** 当前启用用户的点赞文章，按点赞时间倒序分页，隐藏文章不返回。 */
    PageResult<LikedArticleVO> likedArticles(PageQueryDTO query);

    /** 公开详情只给安全 HTML，成功加一次阅读，失败连计数一起回滚。 */
    ArticlePublicDetailVO publicDetail(String slug);

    /** 公开归档按 UTC 月份倒序，total 数的是月份。 */
    PageResult<ArchiveVO> archives(int page, int pageSize);

    /** 后台列表按 query 权限限制作者，客户端 authorId 不能扩大 SELF。 */
    PageResult<ArticleAdminVO> adminArticles(AdminArticleQueryDTO query);

    /** 后台详情也能看回收站，返回文章整体版本；越界和不存在都报 404。 */
    ArticleProfileVO detail(long id);

    /** 作者取当前会话，状态固定草稿；正文或关联失败时整个创建回滚。 */
    ArticleProfileVO create(ArticleWriteDTO request);

    /** 锁事务里完整替换文章内容，已发布文章还要单独满足 publish 的范围。 */
    ArticleProfileVO update(long id, ArticleWriteDTO request, String revision);

    /** 这里只发布或下架，首次发布时间只写一次，目标状态没变也要查版本。 */
    ArticleProfileVO changeStatus(long id, int status, String revision);

    /** 回收只写 deletedAt，正文、slug、标签和互动都保留；同版本重复回收仍成功。 */
    void recycle(long id, String revision);

    /** 恢复后固定下架，首次发布时间保留；不在回收站就返回 409。 */
    ArticleProfileVO restore(long id, String revision);

    /** delete 与 purge 分别满足权限范围及版本后，永久删除回收站聚合，保留文件和浏览历史。 */
    void purge(long id, String revision);

    /** 按累计阅读或点赞排全站、本月榜，本月用 UTC 首次发布时间筛选，不增加阅读量。 */
    List<HotArticleVO> rankings(String period, String metric, int limit);
}
