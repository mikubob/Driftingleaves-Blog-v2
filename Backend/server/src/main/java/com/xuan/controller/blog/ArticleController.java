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
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 博客端文章接口
 */
@Slf4j
@RestController("publicArticleController")
@RequestMapping("/blog")
@RequiredArgsConstructor
public class ArticleController {

    private final IArticleService articleService;

    /**
     * 分页查询公开文章列表
     *
     * @param query 查询参数
     * @return 公开文章分页结果
     */
    @GetMapping("/articles")
    public Result<PageResult<ArticlePublicVO>> list(@Valid @ModelAttribute ArticleQueryDTO query) {
        log.info("分页查询公开文章列表: {}", query);
        PageResult<ArticlePublicVO> pageResult = articleService.publicArticles(query);
        return Result.ok(pageResult);
    }

    /**
     * 根据 slug 查询公开文章详情
     *
     * @param slug 文章 slug
     * @return 公开文章详情
     */
    @GetMapping("/articles/{slug}")
    public Result<ArticlePublicDetailVO> detail(@PathVariable @Size(max = 200)
            @Pattern(regexp = "[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*") String slug) {
        log.info("根据 slug 查询公开文章详情: slug={}", slug);
        ArticlePublicDetailVO detail = articleService.publicDetail(slug);
        return Result.ok(detail);
    }

    /**
     * 分页查询文章归档
     *
     * @param page 页码
     * @param pageSize 每页大小
     * @return 文章归档分页结果
     */
    @GetMapping("/archives")
    public Result<PageResult<ArchiveVO>> archives(@RequestParam(defaultValue = "1") @Min(1) @Max(100000) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        log.info("分页查询文章归档: page={}, pageSize={}", page, pageSize);
        PageResult<ArchiveVO> pageResult = articleService.archives(page, pageSize);
        return Result.ok(pageResult);
    }

    /**
     * 查询热门文章排行
     *
     * @param period 统计周期（site 或 month）
     * @param metric 排行指标（view 或 like）
     * @param limit 返回条数
     * @return 热门文章列表
     */
    @GetMapping("/article-rankings")
    public Result<List<HotArticleVO>> rankings(
            @RequestParam(defaultValue = "site") @Pattern(regexp = "site|month") String period,
            @RequestParam(defaultValue = "view") @Pattern(regexp = "view|like") String metric,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        log.info("查询热门文章排行: period={}, metric={}, limit={}", period, metric, limit);
        List<HotArticleVO> rankings = articleService.rankings(period, metric, limit);
        return Result.ok(rankings);
    }
}
