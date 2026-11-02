package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.AdminArticleQueryDTO;
import com.xuan.dto.ArticleQueryDTO;
import com.xuan.dto.ArticleWriteDTO;
import com.xuan.dto.BatchItemDTO;
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
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.HotArticleVO;
import com.xuan.vo.LikedArticleVO;

import java.util.List;

/**
 * 文章业务接口，资料、正文、标签、附件引用和版本在同一事务中处理。
 */
public interface IArticleService extends IService<BlogArticle> {

    /** 分页查询公开文章，所有筛选条件都要满足，列表和总数在同一个只读事务中查询。 */
    PageResult<ArticlePublicVO> publicArticles(ArticleQueryDTO query);

    /** 缓存公开文章列表中不随互动变化的资料，由 publicArticles 在同一事务中补上当前计数。 */
    PageResult<ArticlePublicVO> cachedPublicArticles(ArticleQueryDTO query);

    /** 缓存公开文章详情，只能由 publicDetail 先锁住文章、检查正文和附件后，通过 Spring 代理调用。 */
    ArticlePublicDetailVO cachedPublicDetail(ArticleRow row, String markdown, String html);

    /** 分页查询有公开文章的作者，先按公开文章数降序，再按作者id升序排列。 */
    PageResult<AuthorPublicVO> publicAuthors(PageQueryDTO query);

    /** 根据id查询作者公开资料，作者没有公开文章或用户不存在时都返回 404。 */
    AuthorPublicVO publicAuthor(long userId);

    /** 分页查询本人点赞的文章，当前用户必须启用，按点赞时间降序排列，不返回隐藏文章。 */
    PageResult<LikedArticleVO> likedArticles(PageQueryDTO query);

    /** 根据slug查询公开文章详情，只返回清理后的 HTML；成功增加一次阅读量，失败时阅读量也回滚。 */
    ArticlePublicDetailVO publicDetail(String slug);

    /** 按 UTC 月份查询公开文章归档，月份降序排列，total 表示月份数量。 */
    PageResult<ArchiveVO> archives(int page, int pageSize);

    /** 分页查询后台文章，按查询权限限制作者范围，客户端传入 authorId 也不能扩大 SELF 范围。 */
    PageResult<ArticleAdminVO> adminArticles(AdminArticleQueryDTO query);

    /** 根据id查询文章详情及版本，回收站文章也能查；无权查看或文章不存在都返回 404。 */
    ArticleProfileVO detail(long id);

    /** 创建文章草稿，作者取当前登录用户；正文或关联保存失败时，整个创建一起回滚。 */
    ArticleProfileVO create(ArticleWriteDTO request);

    /** 根据id更新文章，加锁后完整替换内容；已发布文章还要检查发布权限及范围。 */
    ArticleProfileVO update(long id, ArticleWriteDTO request, String revision);

    /** 根据id发布或下架文章，首次发布时间只写一次，状态不变时也要检查版本。 */
    ArticleProfileVO changeStatus(long id, int status, String revision);

    /** 根据id将文章移入回收站，只更新 deletedAt，保留正文、slug、标签和互动记录；使用同一版本重复操作仍返回成功。 */
    void recycle(long id, String revision);

    /** 根据id恢复回收站文章，恢复后设为下架，保留首次发布时间；文章不在回收站时返回 409。 */
    ArticleProfileVO restore(long id, String revision);

    /** 根据id永久删除回收站文章及相关记录，保留文件和浏览历史；删除和彻底删除的权限范围、版本都要检查。 */
    void purge(long id, String revision);

    /** 按累计阅读或点赞排全站、本月榜，本月用 UTC 首次发布时间筛选，不增加阅读量。 */
    List<HotArticleVO> rankings(String period, String metric, int limit);
    /** 批量发布或下架文章，单条也走此入口，任一失败则整批回滚。 */
    BatchVersionsVO changeStatusBatch(List<BatchItemDTO> items, int status);
    /** 批量将文章移入回收站，单条也走此入口，任一失败则整批回滚。 */
    void recycleBatch(List<BatchItemDTO> items);
    /** 批量恢复回收站文章，单条也走此入口，任一失败则整批回滚。 */
    BatchVersionsVO restoreBatch(List<BatchItemDTO> items);
    /** 批量永久删除回收站文章，单条也走此入口，任一失败则整批回滚。 */
    void purgeBatch(List<BatchItemDTO> items);
}
