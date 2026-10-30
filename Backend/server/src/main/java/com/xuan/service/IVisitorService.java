package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.entity.BlogVisitor;
import com.xuan.dto.VisitorQueryDTO;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;

/** 访客管理仅限制统计标识，不修改本站用户状态。 */
public interface IVisitorService extends IService<BlogVisitor> {
    /** ALL后台分页读取脱敏地址及统计资料。 */
    PageResult<BlogVisitor> listVisitors(VisitorQueryDTO query);
    /** 版本只包含限制字段，最近访问不会使版本过期。 */
    ResourceVersion<BlogVisitor> detail(long id);
    /** 限制到指定UTC时间或永久，持锁后检查版本。 */
    ResourceVersion<BlogVisitor> block(long id, String until, String revision);
    /** 解除限制且清空结束时间，相同目标仍检查版本。 */
    ResourceVersion<BlogVisitor> unblock(long id, String revision);
    /** 外键将历史PV的visitor_id置NULL，PV仍然保留。 */
    void delete(long id, String revision);
}
