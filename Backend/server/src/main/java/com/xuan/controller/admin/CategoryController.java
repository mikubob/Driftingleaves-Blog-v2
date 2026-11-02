package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.CategoryCreateDTO;
import com.xuan.dto.CategoryQueryDTO;
import com.xuan.dto.CategoryUpdateDTO;
import com.xuan.entity.BlogCategory;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ICategoryService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.CategoryProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 后台分类管理接口，检查后台入口和操作权限，由业务事务处理。
 */
@RestController
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final ICategoryService categories;

    /** 分页查询分类，列表不带每条记录的版本，编辑前先查询详情。 */
    @GetMapping
    @SaCheckPermission("blog:category:query")
    public Result<PageResult<BlogCategory>> list(@Valid @ModelAttribute CategoryQueryDTO query) {
        return Result.ok(categories.listCategories(query));
    }

    /** 根据id查询分类详情，版本放在 X-Revision 响应头。 */
    @GetMapping("/{categoryId}")
    @SaCheckPermission("blog:category:query")
    public ResponseEntity<Result<BlogCategory>> detail(@PathVariable String categoryId) {
        return profile(categories.detail(IdUtils.parse(categoryId)));
    }

    /** 创建分类，成功返回 201 和新记录的详情地址。 */
    @PostMapping
    @SaCheckPermission("blog:category:create")
    public ResponseEntity<Result<BlogCategory>> create(@Valid @RequestBody CategoryCreateDTO body) {
        BlogCategory value = categories.create(body);
        return ResponseEntity.created(URI.create("/api/v1/admin/categories/" + value.getCategoryId()))
                .body(Result.ok(value));
    }

    /** 根据id更新分类，完整替换资料，加锁后检查版本，未传版本时返回 428。 */
    @PutMapping("/{categoryId}")
    @SaCheckPermission("blog:category:update")
    public ResponseEntity<Result<BlogCategory>> update(@PathVariable String categoryId,
            @Valid @RequestBody CategoryUpdateDTO body,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(categories.update(IdUtils.parse(categoryId), body, revision));
    }

    /** 批量删除分类，单条也走此接口，业务层统一检查权限、版本并处理失败。 */
    @PostMapping("/batch-delete")
    @SaCheckPermission("blog:category:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        categories.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /** 返回分类资料，版本只放在响应头。 */
    private ResponseEntity<Result<BlogCategory>> profile(CategoryProfileVO result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.category()));
    }
}
