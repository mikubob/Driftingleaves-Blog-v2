package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.entity.HomeProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 站点主人资料数据访问接口
 */
@Mapper
public interface HomeProfileMapper extends BaseMapper<HomeProfile> {

    /**
     * 加行锁查询当前站点主人资料，未初始化时返回 null。
     *
     * @return 锁定的站点主人资料
     */
    HomeProfile lockProfile();

    /**
     * 完整替换站点主人资料，显式写入可空字段以支持清空。
     *
     * @param row 待更新的站点主人资料实体
     * @return 受影响行数
     */
    int replace(@Param("row") HomeProfile row);
}
