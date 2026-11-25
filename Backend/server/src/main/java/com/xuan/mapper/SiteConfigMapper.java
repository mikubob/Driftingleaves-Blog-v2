package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.entity.BlogSiteConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 站点配置数据访问接口
 */
@Mapper
public interface SiteConfigMapper extends BaseMapper<BlogSiteConfig> {

    /**
     * 查询白名单站点配置列表，公开查询仅返回公开的 site 配置，按配置键升序排列。
     *
     * @param publicOnly 是否仅查公开配置
     * @return 站点配置列表
     */
    List<BlogSiteConfig> configs(@Param("publicOnly") boolean publicOnly);

    /**
     * 根据配置键加行锁查询站点配置，调用方须处于写事务内。
     *
     * @param key 配置键
     * @return 锁定的配置记录，不存在时返回 null
     */
    BlogSiteConfig lockByKey(@Param("key") String key);

    /**
     * 完整更新站点配置值、公开标记与说明，显式写入可空字段。
     *
     * @param config 待更新的配置实体
     * @return 受影响行数
     */
    int updateConfig(@Param("config") BlogSiteConfig config);
}
