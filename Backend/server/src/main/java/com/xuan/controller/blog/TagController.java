package com.xuan.controller.blog;

import com.xuan.dto.PageQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ITagService;
import com.xuan.vo.ArticlePublicVO.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开标签接口，只返回前台需要的字典资料。
 */
@RestController("blogTagController")
@RequestMapping("/blog/tags")
@RequiredArgsConstructor
public class TagController {

    private final ITagService tags;

    /** 分页获取标签，没有数据时 records 返回空数组。 */
    @GetMapping
    public Result<PageResult<Tag>> list(@Valid @ModelAttribute PageQueryDTO query) {
        return Result.ok(tags.publicTags(query));
    }
}
