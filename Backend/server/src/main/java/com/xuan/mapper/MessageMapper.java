package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.entity.BlogMessage;
import com.xuan.model.MessageNode;
import com.xuan.model.MessageRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 留言数据访问接口
 */
@Mapper
public interface MessageMapper extends BaseMapper<BlogMessage> {

    /**
     * 分页查询对当前读者可见的顶层根留言节点，走 idx_message_public 索引。
     *
     * @param page 分页参数
     * @param viewer 当前登录用户 ID，匿名访问为 null
     * @return 可见根留言节点分页结果
     */
    IPage<MessageNode> visibleRootPage(IPage<MessageNode> page, @Param("viewer") Long viewer);

    /**
     * 批量查询指定根留言下的全部回复节点，走 idx_message_root 索引。
     *
     * @param rootIds 根留言 ID 集合
     * @return 回复节点列表
     */
    List<MessageNode> nodesByRoots(@Param("rootIds") Collection<Long> rootIds);

    /**
     * 查询单棵留言会话树的全部节点（根节点及全部回复），用于回复分页、会话详情与删树。
     *
     * @param rootId 会话根留言 ID
     * @return 会话树节点列表
     */
    List<MessageNode> treeNodes(@Param("rootId") long rootId);

    /**
     * 批量查询多棵留言会话树的全部节点（根节点及全部回复），供本人留言列表计算可见性。
     *
     * @param rootIds 会话根留言 ID 集合
     * @return 多棵会话树节点列表
     */
    List<MessageNode> treeNodesByRoots(@Param("rootIds") Collection<Long> rootIds);

    /**
     * 统计指定根留言之前对当前用户可见的根留言数量，用于计算前台跳转页码。
     *
     * @param viewer 当前登录用户 ID
     * @param createTime 目标根留言创建时间
     * @param messageId 目标根留言 ID
     * @return 排在目标之前的可见根留言条数
     */
    long visibleRootRank(@Param("viewer") long viewer,
            @Param("createTime") LocalDateTime createTime,
            @Param("messageId") long messageId);

    /**
     * 根据已确认可见的留言 ID 列表查询留言详情
     *
     * @param ids 留言 ID 列表
     * @return 留言记录列表
     */
    List<MessageRow> visibleRows(@Param("ids") List<Long> ids);

    /**
     * 按归属范围查询单条留言，本人查询传入 user，后台 ALL 查询传 null。
     *
     * @param id 留言 ID
     * @param user 留言作者用户 ID，后台查询时为 null
     * @return 留言记录，不存在或越界时返回 null
     */
    MessageRow scoped(@Param("id") long id, @Param("user") Long user);

    /**
     * 加行锁查询留言记录，调用方按从根到目标顺序加锁。
     *
     * @param id 留言 ID
     * @return 锁定的留言记录，不存在时返回 null
     */
    BlogMessage lockById(@Param("id") long id);

    /**
     * 分页查询本人或后台留言列表
     *
     * @param page 分页参数
     * @param user 留言作者用户 ID，可为空
     * @param status 审核状态，可为空
     * @param secret 是否私密，可为空
     * @param begin 开始时间，可为空
     * @param end 结束时间，可为空
     * @return 留言分页结果
     */
    IPage<MessageRow> messagePage(IPage<MessageRow> page,
            @Param("user") Long user,
            @Param("status") Integer status,
            @Param("secret") Integer secret,
            @Param("begin") LocalDateTime begin,
            @Param("end") LocalDateTime end);

    /**
     * 分页查询后台留言及直接回复标记，parent 为 0 查根节点，为 null 保留平铺。
     *
     * @param page 分页参数
     * @param user 留言作者用户 ID，可为空
     * @param status 审核状态，可为空
     * @param secret 是否私密，可为空
     * @param begin 开始时间，可为空
     * @param end 结束时间，可为空
     * @param parent 父留言 ID，可为空
     * @return 后台留言分页结果
     */
    IPage<MessageRow> adminPage(IPage<MessageRow> page,
            @Param("user") Long user,
            @Param("status") Integer status,
            @Param("secret") Integer secret,
            @Param("begin") LocalDateTime begin,
            @Param("end") LocalDateTime end,
            @Param("parent") Long parent);

    /**
     * 更新留言审核状态，并在 SQL 中限制归属范围。
     *
     * @param row 待更新的留言实体
     * @param user 留言作者用户 ID，可为空
     * @return 受影响行数
     */
    int change(@Param("row") BlogMessage row, @Param("user") Long user);
}
