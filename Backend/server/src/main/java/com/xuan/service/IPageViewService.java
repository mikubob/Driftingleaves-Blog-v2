package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.PageViewQueryDTO;
import com.xuan.entity.BlogPageView;
import com.xuan.result.PageResult;

import java.util.List;

/**
 * 浏览记录业务接口，只提供查询和清理，不允许编辑。
 */
public interface IPageViewService extends IService<BlogPageView> {

    /** 分页查询后台浏览记录，需要 ALL 范围，按指定字段筛选。 */
    PageResult<BlogPageView> listViews(PageViewQueryDTO query);

    /** 根据id删除浏览记录，只影响按保留明细生成的报表，不扣减文章累计阅读量。 */
    void delete(long id);
    /** 批量删除浏览记录，单条也走此入口，所有数据库修改一起提交。 */
    void deleteBatch(List<String> ids);
}
