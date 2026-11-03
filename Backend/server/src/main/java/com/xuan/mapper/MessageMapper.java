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
 * 留言数据接口，SQL 限制归属，写入时先锁操作者和 message 配置，再从根到目标锁住留言。
 */
public interface MessageMapper extends BaseMapper<BlogMessage> {
    /** 查询上级留言关系，不读取隐藏的原文。 */
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

    /** 分页查询后台留言及直接回复标记；parent为0查根节点，null保留平铺，调用前须检查ALL。 */
    IPage<MessageRow> adminPage(IPage<MessageRow> page, @Param("user") Long user,
            @Param("status") Integer status, @Param("secret") Integer secret,
            @Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end,
            @Param("parent") Long parent);
    /** 更新留言正文、回复通知、审核状态和编辑时间，不修改作者、回复关系或私密范围。 */
    int change(@Param("row") BlogMessage row, @Param("user") Long user);
}
