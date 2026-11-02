package com.xuan.controller.blog;

import com.xuan.dto.ArticleQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IArticleService;
import com.xuan.vo.ArchiveVO;
import com.xuan.vo.ArticlePublicDetailVO;
import com.xuan.vo.ArticlePublicVO;
import com.xuan.vo.HotArticleVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 博客文章接口，只查询公开文章，后台 Token 也不能扩大范围。
 */
@RestController("publicArticleController")
@RequestMapping("/blog")
@RequiredArgsConstructor
public class ArticleController {

    private final IArticleService articles;

    /**
     * 分页查询公开文章，所有筛选条件都要满足，分类隐藏时返回空页，登录后也不扩大范围。
     *
     * @param query 已校验的分页、字面关键词、分类、标签及UTC月份
     * @return 200公开列表，不增加阅读
     */
    @GetMapping("/articles")
    public Result<PageResult<ArticlePublicVO>> list(@Valid @ModelAttribute ArticleQueryDTO query) {
        return Result.ok(articles.publicArticles(query));
    }

    /**
     * 根据slug查询公开文章详情，区分大小写，成功后增加一次阅读量，失败不计数。
     *
     * @param slug 已校验的ASCII分词标识，最长200
     * @return 200安全公开详情，非PUBLIC统一404
     */
    @GetMapping("/articles/{slug}")
    public Result<ArticlePublicDetailVO> detail(@PathVariable @Size(max = 200)
    @Pattern(regexp = "[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*") String slug) {
        return Result.ok(articles.publicDetail(slug));
    }

    /**
     * 分页查询文章归档，按 UTC 月份降序排列，页码超出允许范围时返回 400。
     *
     * @param page 默认1，范围1..100000
     * @param pageSize 默认10，范围1..100
     * @return 200月份分页，total为月份数量
     */
    @GetMapping("/archives")
    public Result<PageResult<ArchiveVO>> archives(@RequestParam(defaultValue = "1") @Min(1) @Max(100000) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return Result.ok(articles.archives(page, pageSize));
    }

    /**
     * 查询热门文章排行，只返回公开文章，不增加阅读量，参数错误时返回 400。
     *
     * @param period site全站或month本月首次发布候选，默认site
     * @param metric view累计阅读或like当前点赞，默认view
     * @param limit 默认5，范围1..20
     * @return 200稳定排序的约定的热门文章资料
     */
    @GetMapping("/article-rankings")
    public Result<List<HotArticleVO>> rankings(
            @RequestParam(defaultValue = "site") @Pattern(regexp = "site|month") String period,
            @RequestParam(defaultValue = "view") @Pattern(regexp = "view|like") String metric,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        return Result.ok(articles.rankings(period, metric, limit));
    }
}
