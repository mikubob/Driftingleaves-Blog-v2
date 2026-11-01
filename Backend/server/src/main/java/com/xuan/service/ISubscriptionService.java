package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.SubscriptionQueryDTO;
import com.xuan.dto.SubscriptionWriteDTO;
import com.xuan.entity.BlogRssSubscription;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.SubscriptionVO;

import java.util.List;

/**
 * 邮件订阅必须先验证当前邮箱，XML Feed独立于本业务。
 */
public interface ISubscriptionService extends IService<BlogRssSubscription> {

    /** 仅取当前用户，未申请返回null。 */
    SubscriptionVO own();

    /** 提交待验证状态后发送真实确认信，传输失败503，允许重发恢复。 */
    SubscriptionVO request(SubscriptionWriteDTO request);

    /** 同一用户锁内原子消费当前轮次票据，再提交激活状态。 */
    SubscriptionVO confirm(String ticket);

    /** 本人退订，不存在或已退订幂等。 */
    void cancelOwn();

    /** 公开票据只允许退订绑定的同一轮次，允许重复退订。 */
    void cancelTicket(String ticket);

    /** ALL后台分页，不返回票据。 */
    PageResult<BlogRssSubscription> listSubscriptions(SubscriptionQueryDTO query);

    /** 同一快照读取资料与版本。 */
    ResourceVersion<BlogRssSubscription> detail(long id);

    /** 当前邮箱已验证且用户启用才可重新激活。 */
    ResourceVersion<BlogRssSubscription> changeStatus(long id, int status, String revision);

    /** 物理删除记录，所有旧票据因记录消失而无效。 */
    void delete(long id, String revision);
    /** 单条和多条共用的事务入口，失败不保留部分数据库变更。 */
    void deleteBatch(List<BatchItemDTO> items);
    /** 单条和多条共用的事务入口，失败不保留部分数据库变更。 */
    BatchVersionsVO changeStatusBatch(List<BatchItemDTO> items, int status);
}
