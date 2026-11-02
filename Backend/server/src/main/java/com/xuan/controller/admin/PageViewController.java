package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.BatchIdsDTO;
import com.xuan.dto.PageViewQueryDTO;
import com.xuan.entity.BlogPageView;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IPageViewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台浏览记录接口，只提供分页查询和清理，不修改历史归属。
 */
@RestController
@RequestMapping("/admin/page-views")
@RequiredArgsConstructor
public class PageViewController {

    private final IPageViewService views;

    /** 分页查询浏览记录，需要 ALL 范围，只接受已声明的筛选字段。 */
    @GetMapping
    @SaCheckPermission("blog:view:query")
    public Result<PageResult<BlogPageView>> list(@Valid @ModelAttribute PageViewQueryDTO query) {
        return Result.ok(views.listViews(query));
    }

    /** 批量删除浏览记录，单条也走此接口，业务层统一检查权限、版本并处理失败。 */
    @PostMapping("/batch-delete")
    @SaCheckPermission("blog:view:delete")
    public Result<Void> delete(@Valid @RequestBody BatchIdsDTO body) {
        views.deleteBatch(body.ids());
        return Result.ok(null);
    }
}
