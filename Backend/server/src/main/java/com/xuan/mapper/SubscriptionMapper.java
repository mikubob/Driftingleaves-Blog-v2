package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.SubscriptionQueryDTO;
import com.xuan.entity.BlogRssSubscription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 邮件订阅数据访问接口
 */
@Mapper
public interface SubscriptionMapper extends BaseMapper<BlogRssSubscription> {

    /**
     * 根据用户 ID 查询本人订阅记录
     *
     * @param user 用户 ID
     * @return 订阅记录，未订阅时返回 null
     */
    BlogRssSubscription byUser(@Param("user") long user);

    /**
     * 根据用户 ID 加行锁查询订阅记录，调用方须先持有用户行锁。
     *
     * @param user 用户 ID
     * @return 锁定的订阅记录，不存在时返回 null
     */
    BlogRssSubscription lockByUser(@Param("user") long user);

    /**
     * 根据订阅 ID 加行锁查询订阅记录，调用方须处于写事务内。
     *
     * @param id 订阅 ID
     * @return 锁定的订阅记录，不存在时返回 null
     */
    BlogRssSubscription lockById(@Param("id") long id);

    /**
     * 完整更新订阅资料，显式写入可空字段以支持清空验证和激活时间。
     *
     * @param row 待更新的订阅实体
     * @return 受影响行数
     */
    int replace(@Param("row") BlogRssSubscription row);

    /** 停用与当前账号邮箱不一致的订阅，调用方须先锁住用户。 */
    int retireForEmailChange(@Param("user") long user, @Param("email") String email);

    /**
     * 分页查询后台订阅列表
     *
     * @param page 分页参数
     * @param query 查询条件
     * @param user 用户 ID，可为空
     * @return 订阅分页结果
     */
    IPage<BlogRssSubscription> subscriptionPage(IPage<BlogRssSubscription> page,
            @Param("query") SubscriptionQueryDTO query,
            @Param("user") Long user);

    /**
     * 按主键游标分批查询可接收通知的有效订阅记录（启用用户、已激活且已验证邮箱）
     *
     * @param after 上一批最大订阅 ID
     * @return 有效订阅列表
     */
    List<BlogRssSubscription> recipients(@Param("after") long after);

    /**
     * 查询指定用户当前有效且已验证的订阅记录
     *
     * @param user 用户 ID
     * @return 有效订阅记录，不满足条件时返回 null
     */
    BlogRssSubscription confirmed(@Param("user") long user);
}
