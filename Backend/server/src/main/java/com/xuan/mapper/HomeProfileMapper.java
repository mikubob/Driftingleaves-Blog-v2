package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.entity.HomeProfile;
import org.apache.ibatis.annotations.Param;

/** 单例资料写入先持有既有管理保护锁，首次创建也能串行比较空资源版本。 */
public interface HomeProfileMapper extends BaseMapper<HomeProfile> {
    /** 锁住当前资料，缺失表示还未设置。 */
    HomeProfile lockProfile();
    /** 完整替换，NULL也清空。 */
    int replace(@Param("row") HomeProfile row);
}
