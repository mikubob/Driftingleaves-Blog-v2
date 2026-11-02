package com.xuan.controller.blog;

import com.xuan.dto.PageQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IArticleService;
import com.xuan.vo.LikedArticleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本人点赞文章接口，用户取当前会话，不从参数获取身份。
 */
@RestController("ownArticleController")
@RequestMapping("/blog/me/liked-articles")
@RequiredArgsConstructor
public class MyArticleController {

    private final IArticleService articles;

    /** 分页查询本人点赞的文章，只返回当前公开的文章，不改变点赞或阅读量。 */
    @GetMapping
    public Result<PageResult<LikedArticleVO>> list(@Valid @ModelAttribute PageQueryDTO query) {
        return Result.ok(articles.likedArticles(query));
    }
}
