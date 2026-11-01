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
 * 个人中心只读取当前用户的点赞，身份不从参数取得。
 */
@RestController("ownArticleController")
@RequestMapping("/blog/me/liked-articles")
@RequiredArgsConstructor
public class MyArticleController {

    private final IArticleService articles;

    /** 只返回当前仍公开的完整文章资料，读取不改变点赞或阅读。 */
    @GetMapping
    public Result<PageResult<LikedArticleVO>> list(@Valid @ModelAttribute PageQueryDTO query) {
        return Result.ok(articles.likedArticles(query));
    }
}
