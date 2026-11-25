package com.xuan.controller.blog;

import com.xuan.dto.PageQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ITagService;
import com.xuan.vo.TagPublicDetailVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 博客端文章标签接口
 */
@Slf4j
@RestController("blogTagController")
@RequestMapping("/blog/tags")
@RequiredArgsConstructor
public class TagController {

    private final ITagService tagService;

    /**
     * 分页查询公开标签列表
     *
     * @param query 分页参数
     * @return 标签分页结果
     */
    @GetMapping
    public Result<PageResult<TagPublicDetailVO>> list(@Valid @ModelAttribute PageQueryDTO query) {
        log.info("分页查询公开标签列表: {}", query);
        PageResult<TagPublicDetailVO> pageResult = tagService.publicTags(query);
        return Result.ok(pageResult);
    }

    /**
     * 根据 slug 查询公开标签详情
     *
     * @param slug 标签 slug
     * @return 标签详情
     */
    @GetMapping("/{slug}")
    public Result<TagPublicDetailVO> detail(@PathVariable @Size(max = 100)
            @Pattern(regexp = "[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*") String slug) {
        log.info("根据 slug 查询公开标签详情: slug={}", slug);
        TagPublicDetailVO detail = tagService.publicDetail(slug);
        return Result.ok(detail);
    }
}
