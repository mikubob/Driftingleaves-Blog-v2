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
 * 邮件订阅业务接口，必须先验证当前邮箱，与 RSS 订阅独立。
 */
public interface ISubscriptionService extends IService<BlogRssSubscription> {

    /** 查询本人订阅，未申请订阅时返回 null。 */
    SubscriptionVO own();

    /** 申请订阅，只能使用当前账号邮箱；保存待验证状态后发送确认邮件，发送失败返回 503，可以重新发送。 */
    SubscriptionVO request(SubscriptionWriteDTO request);

    /** 确认订阅，在用户锁内检查并一次性使用本次申请的票据，然后保存激活状态。 */
    SubscriptionVO confirm(String ticket);

    /** 取消本人订阅，没有订阅或已经退订时也返回成功。 */
    void cancelOwn();

    /** 根据邮件票据取消订阅，只能取消票据对应轮次的订阅，重复退订也返回成功。 */
    void cancelTicket(String ticket);

    /** 分页查询订阅，需要 ALL 范围，不返回票据。 */
    PageResult<BlogRssSubscription> listSubscriptions(SubscriptionQueryDTO query);

    /** 根据id查询订阅详情及版本，资料和版本在同一个只读事务中读取。 */
    ResourceVersion<BlogRssSubscription> detail(long id);

    /** 根据id修改订阅状态，重新激活时要求当前邮箱已验证、用户已启用。 */
    ResourceVersion<BlogRssSubscription> changeStatus(long id, int status, String revision);

    /** 根据id删除订阅记录，删除后原有票据全部失效。 */
    void delete(long id, String revision);
    /** 批量删除订阅，单条也走此入口，任一失败则整批回滚。 */
    void deleteBatch(List<BatchItemDTO> items);
    /** 批量修改订阅状态，单条也走此入口，任一失败则整批回滚。 */
    BatchVersionsVO changeStatusBatch(List<BatchItemDTO> items, int status);
}
