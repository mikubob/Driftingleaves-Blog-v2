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
 * 浏览明细只有分页与清理入口，不允许修改历史归属。
 */
@RestController
@RequestMapping("/admin/page-views")
@RequiredArgsConstructor
public class PageViewController {

    private final IPageViewService views;

    /** ALL后台按声明字段筛选。 */
    @GetMapping
    @SaCheckPermission("blog:view:query")
    public Result<PageResult<BlogPageView>> list(@Valid @ModelAttribute PageViewQueryDTO query) {
        return Result.ok(views.listViews(query));
    }

    /** 单个和多个目标共用入口，权限、版本及失败结果由业务层统一处理。 */
    @PostMapping("/batch-delete")
    @SaCheckPermission("blog:view:delete")
    public Result<Void> delete(@Valid @RequestBody BatchIdsDTO body) {
        views.deleteBatch(body.ids());
        return Result.ok(null);
    }
}
