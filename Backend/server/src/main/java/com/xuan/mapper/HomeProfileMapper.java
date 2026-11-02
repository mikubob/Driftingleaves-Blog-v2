package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.entity.HomeProfile;
import org.apache.ibatis.annotations.Param;

/**
 * 站点主人资料接口，写入前先拿管理保护锁，保证首次创建时也能依次检查版本。
 */
public interface HomeProfileMapper extends BaseMapper<HomeProfile> {

    /** 锁住当前资料，缺失表示还未设置。 */
    HomeProfile lockProfile();

    /** 完整替换，NULL也清空。 */
    int replace(@Param("row") HomeProfile row);
}
