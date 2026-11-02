package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.VisitorQueryDTO;
import com.xuan.entity.BlogVisitor;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.VisitorVO;

import java.util.List;

/**
 * 访客业务接口，只限制统计标识，不修改用户账号状态。
 */
public interface IVisitorService extends IService<BlogVisitor> {

    /** 分页查询访客资料及当前保留的浏览次数，需要 ALL 范围。 */
    PageResult<VisitorVO> listVisitors(VisitorQueryDTO query);

    /** 根据id查询访客详情，版本只包含访问限制字段，最近访问变化不会导致版本过期。 */
    ResourceVersion<VisitorVO> detail(long id);

    /** 批量删除访客，单条也走此入口，所有数据库修改一起提交。 */
    void deleteBatch(List<BatchItemDTO> items);
    /** 批量限制访客访问，单条也走此入口，任一失败则整批回滚。 */
    BatchVersionsVO blockBatch(List<BatchItemDTO> items, String until);
    /** 批量解除访客限制，单条也走此入口，任一失败则整批回滚。 */
    BatchVersionsVO unblockBatch(List<BatchItemDTO> items);
}
