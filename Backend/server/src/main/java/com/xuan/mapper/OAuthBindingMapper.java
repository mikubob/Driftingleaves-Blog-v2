package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.entity.SysUserOauth;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 第三方账号绑定数据访问接口
 */
@Mapper
public interface OAuthBindingMapper extends BaseMapper<SysUserOauth> {

    /**
     * 根据平台标识与平台用户唯一标识查询绑定记录，均区分大小写。
     *
     * @param source 平台标识
     * @param uuid 平台用户唯一标识
     * @return 绑定记录，不存在时返回 null
     */
    SysUserOauth findIdentity(@Param("source") String source, @Param("uuid") String uuid);

    /**
     * 查询指定用户的全部第三方绑定记录，按平台标识升序排列。
     *
     * @param userId 用户 ID
     * @return 绑定记录列表
     */
    List<SysUserOauth> findByUser(@Param("userId") long userId);

    /**
     * 按用户 ID 与平台标识删除第三方绑定记录
     *
     * @param userId 用户 ID
     * @param source 平台标识
     * @return 受影响行数
     */
    int deleteBinding(@Param("userId") long userId, @Param("source") String source);
}
