package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.PageViewQueryDTO;
import com.xuan.entity.BlogPageView;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IPageViewService;
import com.xuan.utils.IdUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
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

    /** 不要求版本，删除后报表基于剩余明细重新计算。 */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:view:delete")
    public Result<Void> delete(@PathVariable String id) {
        views.delete(IdUtils.parse(id));
        return Result.ok(null);
    }
}
