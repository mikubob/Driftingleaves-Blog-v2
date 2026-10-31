package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.entity.BlogMessage;
import com.xuan.model.MessageNode;
import com.xuan.model.MessageRow;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 留言写入按操作者、message配置、根到目标加锁，归属在SQL中限定。
 */
public interface MessageMapper extends BaseMapper<BlogMessage> {
    /** 只查父链字段，不取隐藏原文。 */
    List<MessageNode> nodes();
    /** 可见性和分页已确认，最多取当前页50条原文。 */
    List<MessageRow> visibleRows(@Param("ids") List<Long> ids);
    /** 本人user固定来自会话，后台传null并先检查ALL。 */
    MessageRow scoped(@Param("id") long id, @Param("user") Long user);
    /** 每条祖先锁后调用方都重新检查原Token和独立权限。 */
    BlogMessage lockById(@Param("id") long id);
    /** 公开查询不走此分页，本人或后台保留各自SQL归属条件。 */
    IPage<MessageRow> messagePage(IPage<MessageRow> page, @Param("user") Long user,
            @Param("status") Integer status, @Param("secret") Integer secret,
            @Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);
    /** 只更新正文、通知、审核和编辑时间，不改作者、父链或私密范围。 */
    int change(@Param("row") BlogMessage row, @Param("user") Long user);
}
