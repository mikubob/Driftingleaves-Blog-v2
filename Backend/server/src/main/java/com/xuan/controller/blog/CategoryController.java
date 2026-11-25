package com.xuan.controller.blog;

import com.xuan.dto.PageQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ICategoryService;
import com.xuan.vo.CategoryPublicDetailVO;
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
 * 博客端文章分类接口
 */
@Slf4j
@RestController("blogCategoryController")
@RequestMapping("/blog/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final ICategoryService categoryService;

    /**
     * 分页查询公开分类列表
     *
     * @param query 分页参数
     * @return 分类分页结果
     */
    @GetMapping
    public Result<PageResult<CategoryPublicDetailVO>> list(@Valid @ModelAttribute PageQueryDTO query) {
        log.info("分页查询公开分类列表: {}", query);
        PageResult<CategoryPublicDetailVO> pageResult = categoryService.publicCategories(query);
        return Result.ok(pageResult);
    }

    /**
     * 根据 slug 查询公开分类详情
     *
     * @param slug 分类 slug
     * @return 分类详情
     */
    @GetMapping("/{slug}")
    public Result<CategoryPublicDetailVO> detail(@PathVariable @Size(max = 100)
            @Pattern(regexp = "[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*") String slug) {
        log.info("根据 slug 查询公开分类详情: slug={}", slug);
        CategoryPublicDetailVO detail = categoryService.publicDetail(slug);
        return Result.ok(detail);
    }
}
