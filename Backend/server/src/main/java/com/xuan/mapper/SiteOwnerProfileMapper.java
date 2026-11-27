package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.entity.SiteOwnerProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 站长资料数据访问接口
 */
@Mapper
public interface SiteOwnerProfileMapper extends BaseMapper<SiteOwnerProfile> {

    /**
     * 加行锁查询当前站长资料，未初始化时返回 null。
     *
     * @return 锁定的站长资料
     */
    SiteOwnerProfile lockProfile();

    /**
     * 完整替换站长资料，显式写入可空字段以支持清空。
     *
     * @param row 待更新的站长资料实体
     * @return 受影响行数
     */
    int replace(@Param("row") SiteOwnerProfile row);
}
