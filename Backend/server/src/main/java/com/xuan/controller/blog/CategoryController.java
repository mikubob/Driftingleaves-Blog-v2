package com.xuan.controller.blog;

import com.xuan.dto.PageQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ICategoryService;
import com.xuan.vo.ArticlePublicVO.Category;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开分类接口，只返回前台需要的字典资料。
 */
@RestController("blogCategoryController")
@RequestMapping("/blog/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final ICategoryService categories;

    /** 分页获取分类，没有数据时 records 返回空数组。 */
    @GetMapping
    public Result<PageResult<Category>> list(@Valid @ModelAttribute PageQueryDTO query) {
        return Result.ok(categories.publicCategories(query));
    }
}
