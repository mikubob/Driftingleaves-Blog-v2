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
 * 作者公开资料接口，只查询有公开文章的作者，不使用后台用户详情。
 */
@RestController
@RequestMapping("/blog/authors")
@RequiredArgsConstructor
public class  AuthorController {

    private final IArticleService articles;

    /** 分页查询文章作者，按公开文章数和作者id排序，不增加阅读量。 */
    @GetMapping
    public Result<PageResult<AuthorPublicVO>> list(@Valid @ModelAttribute PageQueryDTO query) {
        return Result.ok(articles.publicAuthors(query));
    }

    /** 根据id查询作者，没有公开文章或用户不存在时都返回 404。 */
    @GetMapping("/{userId}")
    public Result<AuthorPublicVO> detail(@PathVariable String userId) {
        return Result.ok(articles.publicAuthor(IdUtils.parse(userId)));
    }
}
