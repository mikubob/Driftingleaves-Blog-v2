package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.SubscriptionQueryDTO;
import com.xuan.entity.BlogRssSubscription;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户锁早于订阅锁，所有可空状态时间显式写入。
 */
public interface SubscriptionMapper extends BaseMapper<BlogRssSubscription> {

    /** 本人查询和申请不能通过ALL扩大到其他用户。 */
    BlogRssSubscription byUser(@Param("user") long user);

    /** 用户锁后锁住当前订阅。 */
    BlogRssSubscription lockByUser(@Param("user") long user);

    /** 后台按ID锁目标，再由调用方重查身份和ALL。 */
    BlogRssSubscription lockById(@Param("id") long id);

    /** NULL必须实际清空旧邮箱验证和激活时间。 */
    int replace(@Param("row") BlogRssSubscription row);

    /** 只供ALL后台分页，不开放全量邮箱导出。 */
    IPage<BlogRssSubscription> subscriptionPage(IPage<BlogRssSubscription> page,
            @Param("query") SubscriptionQueryDTO query, @Param("user") Long user);

    /** 通知只取启用用户、激活且已验证的邮箱，每批有界。 */
    List<BlogRssSubscription> recipients(@Param("after") long after);

    /** 提醒指定用户前重查当前已验证激活订阅与账号启用状态。 */
    BlogRssSubscription confirmed(@Param("user") long user);
}
