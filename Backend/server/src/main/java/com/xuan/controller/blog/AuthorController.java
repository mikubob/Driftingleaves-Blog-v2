package com.xuan.controller.blog;

import com.xuan.dto.PageQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IArticleService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.AuthorPublicVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 作者公开资料来自当前 PUBLIC 文章，不复用后台用户详情。
 */
@RestController
@RequestMapping("/blog/authors")
@RequiredArgsConstructor
public class  AuthorController {

    private final IArticleService articles;

    /** 按公开文章数和作者 ID 稳定分页，不增加阅读量。 */
    @GetMapping
    public Result<PageResult<AuthorPublicVO>> list(@Valid @ModelAttribute PageQueryDTO query) {
        return Result.ok(articles.publicAuthors(query));
    }

    /** 没有公开文章与不存在的用户统一返回 404。 */
    @GetMapping("/{userId}")
    public Result<AuthorPublicVO> detail(@PathVariable String userId) {
        return Result.ok(articles.publicAuthor(IdUtils.parse(userId)));
    }
}
