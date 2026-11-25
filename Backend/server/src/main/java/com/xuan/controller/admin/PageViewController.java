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
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端浏览记录接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/page-views")
@RequiredArgsConstructor
public class PageViewController {

    private final IPageViewService pageViewService;

    /**
     * 分页查询浏览记录列表
     *
     * @param query 查询参数
     * @return 浏览记录分页结果
     */
    @GetMapping
    @SaCheckPermission("blog:view:query")
    public Result<PageResult<BlogPageView>> list(@Valid @ModelAttribute PageViewQueryDTO query) {
        log.info("分页查询浏览记录列表: {}", query);
        PageResult<BlogPageView> pageResult = pageViewService.listViews(query);
        return Result.ok(pageResult);
    }

    /**
     * 批量删除浏览记录
     *
     * @param body 批量删除参数
     * @return 成功结果
     */
    @PostMapping("/batch-delete")
    @SaCheckPermission("blog:view:delete")
    public Result<Void> delete(@Valid @RequestBody BatchIdsDTO body) {
        log.info("批量删除浏览记录: count={}", body.ids().size());
        pageViewService.deleteBatch(body.ids());
        return Result.ok(null);
    }
}
