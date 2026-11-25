package com.xuan.controller.blog;

import com.xuan.dto.PageQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IArticleService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.AuthorPublicVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 博客端作者公开资料接口
 */
@Slf4j
@RestController
@RequestMapping("/blog/authors")
@RequiredArgsConstructor
public class AuthorController {

    private final IArticleService articleService;

    /**
     * 分页查询公开文章作者列表
     *
     * @param query 分页参数
     * @return 作者分页结果
     */
    @GetMapping
    public Result<PageResult<AuthorPublicVO>> list(@Valid @ModelAttribute PageQueryDTO query) {
        log.info("分页查询公开文章作者列表: {}", query);
        PageResult<AuthorPublicVO> pageResult = articleService.publicAuthors(query);
        return Result.ok(pageResult);
    }

    /**
     * 根据 ID 查询公开作者资料
     *
     * @param userId 作者用户 ID
     * @return 作者公开资料
     */
    @GetMapping("/{userId}")
    public Result<AuthorPublicVO> detail(@PathVariable String userId) {
        log.info("根据 ID 查询公开作者资料: userId={}", userId);
        AuthorPublicVO author = articleService.publicAuthor(IdUtils.parse(userId));
        return Result.ok(author);
    }
}
