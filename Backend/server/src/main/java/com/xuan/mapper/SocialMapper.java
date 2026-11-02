package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.entity.HomeSocialLink;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 社交链接数据接口，列表和总数使用相同条件，写入时先锁操作者再锁记录。
 */
public interface SocialMapper extends BaseMapper<HomeSocialLink> {

    /** 分页查询社交链接，公开查询只取展示中的记录，关键词按普通文字匹配。 */
    IPage<HomeSocialLink> resourcePage(IPage<HomeSocialLink> page, @Param("query") DisplayQueryDTO query,
            @Param("publicOnly") boolean publicOnly);

    /** 公开社交链接为有序全量列表。 */
    List<HomeSocialLink> publicItems();

    /** 根据id锁住记录，拿到锁后由调用方重新检查原会话、ALL 范围和版本。 */
    HomeSocialLink lockById(@Param("id") long id);

    /** 更新记录，可空字段传入 NULL 时也要写入，避免清空操作被默认更新策略忽略。 */
    int replace(@Param("row") HomeSocialLink row);
}
