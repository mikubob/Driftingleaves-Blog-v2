package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.entity.BlogSiteConfig;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 配置只查 site/comment/message，不提供新增或删除配置键的业务入口。 */
public interface SiteConfigMapper extends BaseMapper<BlogSiteConfig> {
    /** 公开只取 site 且 is_public=1，后台也只取白名单，按键升序。 */
    List<BlogSiteConfig> configs(@Param("publicOnly") boolean publicOnly);
    /** 和评论创建锁同一配置行，让开关修改与新增评论有明确提交顺序。 */
    BlogSiteConfig lockByKey(@Param("key") String key);
    /** 完整替换配置资料，可空说明也写入，时间由数据库维护。 */
    int updateConfig(@Param("config") BlogSiteConfig config);
}
