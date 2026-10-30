package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.LinkQueryDTO;
import com.xuan.entity.BlogFriendLink;
import org.apache.ibatis.annotations.Param;

/**
 * 友链是全站资源，SELF 也不虚构按作者筛选。
 */
public interface FriendLinkMapper extends BaseMapper<BlogFriendLink> {

    /** 列表与总数共用条件，公开查询只取展示状态。 */
    IPage<BlogFriendLink> linkPage(IPage<BlogFriendLink> page, @Param("query") LinkQueryDTO query,
            @Param("publicOnly") boolean publicOnly);

    /** 调用方先锁操作者，再锁友链并重查权限和版本。 */
    BlogFriendLink lockById(@Param("id") long id);

    /** 显式写可空字段，清空头像或说明不能被默认更新策略跳过。 */
    int updateLink(@Param("link") BlogFriendLink link);
}
