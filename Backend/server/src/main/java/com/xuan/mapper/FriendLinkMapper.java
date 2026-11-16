package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.LinkQueryDTO;
import com.xuan.entity.BlogFriendLink;
import com.xuan.vo.AdminLinkVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 友情链接数据接口，查询全站资源，SELF 也不按作者筛选。
 */
public interface FriendLinkMapper extends BaseMapper<BlogFriendLink> {

    /** 分页查询展示中的友链。 */
    IPage<BlogFriendLink> publicLinkPage(IPage<BlogFriendLink> page);

    /** 分页查询后台友链和申请人，列表与总数共用筛选条件。 */
    IPage<AdminLinkVO> adminLinkPage(IPage<AdminLinkVO> page, @Param("query") LinkQueryDTO query,
            @Param("applicantId") Long applicantId);

    /** 根据id查询后台友链和申请人。 */
    AdminLinkVO adminLinkDetail(@Param("id") long id);

    /** 调用方先锁操作者，再锁友链并重查权限和版本。 */
    BlogFriendLink lockById(@Param("id") long id);

    /** 查询账号尚未删除的申请，包含所有审核状态。 */
    BlogFriendLink findApplication(@Param("userId") long userId);

    /** 保存友链及服务端确定的来源，回填生成的友链 ID。 */
    int insertLink(@Param("link") BlogFriendLink link, @Param("applicantId") Long applicantId,
            @Param("source") String source);

    /** 根据id和申请账号删除友链。 */
    int deleteApplication(@Param("id") long id, @Param("userId") long userId);

    /** 查询账号在指定自然日已成功提交的次数。 */
    Integer applicationCount(@Param("userId") long userId, @Param("date") LocalDate date);

    /** 持有账号锁，在保存友链申请的事务内记录次数。 */
    int saveApplicationCount(@Param("userId") long userId, @Param("date") LocalDate date,
            @Param("count") int count);

    /** 显式写可空字段，清空头像或说明不能被默认更新策略跳过。 */
    int updateLink(@Param("link") BlogFriendLink link);
    /** 全部目标加锁并通过版本检查后，只更新状态。 */
    int updateStatus(@Param("id") long id, @Param("status") int status);

}
