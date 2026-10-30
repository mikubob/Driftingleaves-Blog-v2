package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.entity.SysUserOauth;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 第三方身份由数据库唯一键防重复，删除时要同时限制用户和平台。
 */
public interface OAuthBindingMapper extends BaseMapper<SysUserOauth> {
    /**
     * 按平台和已验证的 uuid 查唯一绑定，两个字段都区分大小写；不能按昵称或邮箱认账号，用户状态由调用方检查。
     * @param source 服务端允许的平台标识
     * @param uuid 平台验证成功后原样保留的身份标识
     * @return 唯一绑定；不存在时为 null
     */
    SysUserOauth findIdentity(@Param("source") String source, @Param("uuid") String uuid);
    /**
     * 按 source 升序取用户全部绑定，关闭的平台也保留；哪些仍能登录、哪些能对外返回由业务层判断。
     * @param userId 服务端确认的本站用户 ID
     * @return 内部绑定实体列表，无绑定时为空列表
     */
    List<SysUserOauth> findByUser(@Param("userId") long userId);
    /**
     * 按用户 ID 和平台一起删除绑定；调用前要锁住用户，并查过最近认证和剩余登录方式。
     * @param userId 已确认归属并锁定的用户 ID
     * @param source 待解绑的平台标识
     * @return 删除影响行数，无匹配绑定时为 0
     */
    int deleteBinding(@Param("userId") long userId, @Param("source") String source);
}
