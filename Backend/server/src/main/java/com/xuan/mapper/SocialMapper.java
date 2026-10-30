package com.xuan.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.entity.HomeSocialLink;
import org.apache.ibatis.annotations.Param;
/** 查询与总数使用同样条件，写事务先锁操作者再锁资源。 */
public interface SocialMapper extends BaseMapper<HomeSocialLink> {
    /** 公开查询只取展示状态，关键词按字面包含。 */
    IPage<HomeSocialLink> resourcePage(IPage<HomeSocialLink> page, @Param("query") DisplayQueryDTO query, @Param("publicOnly") boolean publicOnly);
    /** 公开社交链接为有序全量列表。 */
    List<HomeSocialLink> publicItems();
    /** 持有资源锁后，调用方重新检查原会话、ALL范围和版本。 */
    HomeSocialLink lockById(@Param("id") long id);
    /** 显式覆盖可空字段，不能让NULL被默认更新策略跳过。 */
    int replace(@Param("row") HomeSocialLink row);
}
