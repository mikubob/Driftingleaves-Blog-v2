package com.xuan.controller.blog;

import com.xuan.dto.PageQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ICategoryService;
import com.xuan.vo.ArticlePublicVO.Category;
import com.xuan.vo.CategoryPublicDetailVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
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

    /** slug 只接受 ASCII 分词，隐藏或不存在的分类统一返回 404。 */
    @GetMapping("/{slug}")
    public Result<CategoryPublicDetailVO> detail(@PathVariable @Size(max = 100)
            @Pattern(regexp = "[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*") String slug) {
        return Result.ok(categories.publicDetail(slug));
    }
}
