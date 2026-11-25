package com.xuan.controller.blog;

import com.xuan.dto.PageQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IArticleService;
import com.xuan.vo.LikedArticleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本人点赞文章接口
 */
@Slf4j
@RestController("ownArticleController")
@RequestMapping("/blog/me/liked-articles")
@RequiredArgsConstructor
public class MyArticleController {

    private final IArticleService articleService;

    /**
     * 分页查询本人点赞的公开文章
     *
     * @param query 分页参数
     * @return 点赞文章分页结果
     */
    @GetMapping
    public Result<PageResult<LikedArticleVO>> list(@Valid @ModelAttribute PageQueryDTO query) {
        log.info("分页查询本人点赞文章列表: {}", query);
        PageResult<LikedArticleVO> pageResult = articleService.likedArticles(query);
        return Result.ok(pageResult);
    }
}
