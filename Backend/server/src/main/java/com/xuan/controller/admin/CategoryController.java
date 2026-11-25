package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.constant.HttpConstants;
import com.xuan.dto.BatchBinaryStatusDTO;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.CategoryCreateDTO;
import com.xuan.dto.CategoryQueryDTO;
import com.xuan.dto.CategoryUpdateDTO;
import com.xuan.entity.BlogCategory;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ICategoryService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.CategoryProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 管理端文章分类接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final ICategoryService categoryService;

    /**
     * 分页查询分类列表
     *
     * @param query 查询参数
     * @return 分类分页结果
     */
    @GetMapping
    @SaCheckPermission("blog:category:query")
    public Result<PageResult<BlogCategory>> list(@Valid @ModelAttribute CategoryQueryDTO query) {
        log.info("分页查询分类列表: {}", query);
        PageResult<BlogCategory> pageResult = categoryService.listCategories(query);
        return Result.ok(pageResult);
    }

    /**
     * 根据 ID 查询分类详情
     *
     * @param categoryId 分类 ID
     * @return 分类详情及 X-Revision 响应头
     */
    @GetMapping("/{categoryId}")
    @SaCheckPermission("blog:category:query")
    public ResponseEntity<Result<BlogCategory>> detail(@PathVariable String categoryId) {
        log.info("根据 ID 查询分类详情: categoryId={}", categoryId);
        CategoryProfileVO profile = categoryService.detail(IdUtils.parse(categoryId));
        return profileResponse(profile);
    }

    /**
     * 创建分类
     *
     * @param body 分类创建参数
     * @return 201 响应及详情 Location
     */
    @PostMapping
    @SaCheckPermission("blog:category:create")
    public ResponseEntity<Result<BlogCategory>> create(@Valid @RequestBody CategoryCreateDTO body) {
        log.info("创建分类: {}", body);
        BlogCategory category = categoryService.create(body);
        URI location = URI.create("/api/v1/admin/categories/" + category.getCategoryId());
        return ResponseEntity.created(location).body(Result.ok(category));
    }

    /**
     * 根据 ID 更新分类
     *
     * @param categoryId 分类 ID
     * @param body 分类更新参数
     * @param revision 请求头携带的 If-Revision 版本
     * @return 更新后的分类详情及新 X-Revision 响应头
     */
    @PutMapping("/{categoryId}")
    @SaCheckPermission("blog:category:update")
    public ResponseEntity<Result<BlogCategory>> update(@PathVariable String categoryId,
            @Valid @RequestBody CategoryUpdateDTO body,
            @RequestHeader(value = HttpConstants.HEADER_IF_REVISION, required = false) String revision) {
        log.info("更新分类: categoryId={}", categoryId);
        CategoryProfileVO profile = categoryService.update(IdUtils.parse(categoryId), body, revision);
        return profileResponse(profile);
    }

    /**
     * 批量修改分类展示状态
     *
     * @param body 批量状态变更参数
     * @return 更新后的批量版本信息
     */
    @PutMapping("/batch-status")
    @SaCheckPermission("blog:category:update")
    public Result<BatchVersionsVO> status(@Valid @RequestBody BatchBinaryStatusDTO body) {
        log.info("批量修改分类状态: count={}, status={}", body.getItems().size(), body.getStatus());
        BatchVersionsVO versions = categoryService.changeStatusBatch(body.getItems(), body.getStatus());
        return Result.ok(versions);
    }

    /**
     * 批量删除分类
     *
     * @param body 批量删除参数
     * @return 成功结果
     */
    @PostMapping("/batch-delete")
    @SaCheckPermission("blog:category:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        log.info("批量删除分类: count={}", body.getItems().size());
        categoryService.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /**
     * 组装带版本头的分类详情响应。
     *
     * @param result 分类详情与版本
     * @return 200 响应
     */
    private ResponseEntity<Result<BlogCategory>> profileResponse(CategoryProfileVO result) {
        return ResponseEntity.ok()
                .header(HttpConstants.HEADER_X_REVISION, result.getRevision())
                .body(Result.ok(result.getCategory()));
    }
}
