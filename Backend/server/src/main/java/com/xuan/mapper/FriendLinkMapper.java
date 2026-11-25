package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.LinkQueryDTO;
import com.xuan.entity.BlogFriendLink;
import com.xuan.model.LinkApplicationRow;
import com.xuan.vo.AdminLinkVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 友情链接数据访问接口
 */
@Mapper
public interface FriendLinkMapper extends BaseMapper<BlogFriendLink> {

    /**
     * 分页查询公开展示中的友情链接
     *
     * @param page 分页参数
     * @return 公开友链分页结果
     */
    IPage<BlogFriendLink> publicLinkPage(IPage<BlogFriendLink> page);

    /**
     * 分页查询后台友情链接及申请人信息
     *
     * @param page 分页参数
     * @param query 查询条件
     * @param applicantId 申请人用户 ID，可为空
     * @return 后台友链分页结果
     */
    IPage<AdminLinkVO> adminLinkPage(IPage<AdminLinkVO> page,
            @Param("query") LinkQueryDTO query,
            @Param("applicantId") Long applicantId);

    /**
     * 根据友链 ID 查询后台友链及申请人详情
     *
     * @param id 友链 ID
     * @return 友链详情，不存在时返回 null
     */
    AdminLinkVO adminLinkDetail(@Param("id") long id);

    /**
     * 加行锁查询友链记录，调用方须处于写事务内。
     *
     * @param id 友链 ID
     * @return 锁定的友链记录，不存在时返回 null
     */
    LinkApplicationRow lockById(@Param("id") long id);

    /**
     * 查询指定用户当前的友链申请记录（含所有审核状态）
     *
     * @param userId 申请用户 ID
     * @return 友链申请记录，无申请时返回 null
     */
    BlogFriendLink findApplication(@Param("userId") long userId);

    /**
     * 新增友情链接并回填自增主键
     *
     * @param link 友链实体
     * @param applicantId 申请人用户 ID，后台直接创建时为 null
     * @param source 来源标识
     * @return 受影响行数
     */
    int insertLink(@Param("link") BlogFriendLink link,
            @Param("applicantId") Long applicantId,
            @Param("source") String source);

    /**
     * 按友链 ID 与申请用户 ID 删除本人友链申请
     *
     * @param id 友链 ID
     * @param userId 申请用户 ID
     * @return 受影响行数
     */
    int deleteApplication(@Param("id") long id, @Param("userId") long userId);

    /**
     * 查询用户在指定自然日已提交友链申请的次数
     *
     * @param userId 用户 ID
     * @param date 统计日期
     * @return 当日提交次数，无记录时返回 null
     */
    Integer applicationCount(@Param("userId") long userId, @Param("date") LocalDate date);

    /**
     * 保存或更新用户当日友链申请次数
     *
     * @param userId 用户 ID
     * @param date 统计日期
     * @param count 累计次数
     * @return 受影响行数
     */
    int saveApplicationCount(@Param("userId") long userId,
            @Param("date") LocalDate date,
            @Param("count") int count);

    /**
     * 完整更新友情链接资料，显式写入可空字段以支持清空。
     *
     * @param link 待更新的友链实体
     * @param applicantId 申请人用户 ID，可为空
     * @return 受影响行数
     */
    int updateLink(@Param("link") BlogFriendLink link, @Param("applicantId") Long applicantId);

    /**
     * 更新友情链接状态
     *
     * @param id 友链 ID
     * @param status 目标状态
     * @return 受影响行数
     */
    int updateStatus(@Param("id") long id, @Param("status") int status);
}
