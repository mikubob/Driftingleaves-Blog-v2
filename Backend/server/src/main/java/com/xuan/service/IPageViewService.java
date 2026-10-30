package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.entity.BlogPageView;
import com.xuan.dto.PageViewQueryDTO;
import com.xuan.result.PageResult;

/** 浏览明细只能查询和显式清理，没有编辑能力。 */
public interface IPageViewService extends IService<BlogPageView> {
    /** ALL后台按明确筛选字段分页。 */
    PageResult<BlogPageView> listViews(PageViewQueryDTO query);
    /** 清理只改变保留明细报表，不反向扣文章累计阅读。 */
    void delete(long id);
}
