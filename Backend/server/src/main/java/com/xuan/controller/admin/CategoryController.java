package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.CategoryCreateDTO;
import com.xuan.dto.CategoryQueryDTO;
import com.xuan.dto.CategoryUpdateDTO;
import com.xuan.entity.BlogCategory;
import com.xuan.exception.BusinessException;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ICategoryService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.CategoryProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 后台分类接口，先过后台入口和业务权限检查，再交给业务事务处理。
 */
@RestController
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final ICategoryService categories;

    /** 获取分类分页，列表不提供每条记录的版本，编辑前要先取详情。 */
    @GetMapping
    @SaCheckPermission("blog:category:query")
    public Result<PageResult<BlogCategory>> list(@Valid @ModelAttribute CategoryQueryDTO query) {
        return Result.ok(categories.listCategories(query));
    }

    /** 获取分类详情，把当前版本放进 X-Revision 响应头。 */
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

    /** 完整替换分类资料，版本检查留到锁事务里，缺版本返回 428。 */
    @PutMapping("/{categoryId}")
    @SaCheckPermission("blog:category:update")
    public ResponseEntity<Result<BlogCategory>> update(@PathVariable String categoryId,
            @Valid @RequestBody CategoryUpdateDTO body,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(categories.update(IdUtils.parse(categoryId), body, revision));
    }

    /** 删除分类前先检查版本，关联处理和成功日志都要一起提交。 */
    @DeleteMapping("/{categoryId}")
    @SaCheckPermission("blog:category:delete")
    public Result<Void> delete(@PathVariable String categoryId,
            @RequestHeader(value = "If-Revision", required = false) String revision,
            @RequestBody(required = false) String body) {
        if (body != null) {
            throw new BusinessException(400, "VALIDATION_ERROR", "本接口不接受请求体");
        }
        categories.delete(IdUtils.parse(categoryId), revision);
        return Result.ok(null);
    }

    /** 响应体只放分类资料，版本单独放响应头。 */
    private ResponseEntity<Result<BlogCategory>> profile(CategoryProfileVO result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.category()));
    }
}
