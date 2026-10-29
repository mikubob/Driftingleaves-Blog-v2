package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.entity.SysUserOauth;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 第三方身份唯一键由数据库保证，删除同时限定用户和平台。 */
public interface OAuthBindingMapper extends BaseMapper<SysUserOauth> {
    /**
     * 按平台和已验证的 uuid 查询唯一第三方绑定。
     * <p>两者均按数据库区分大小写的身份字段匹配，不通过昵称或邮箱识别账号；用户状态由调用方检查。</p>
     *
     * @param source 服务端白名单中的平台标识
     * @param uuid 平台验证成功后原样保留的身份标识
     * @return 唯一绑定；不存在时为 null
     */
    SysUserOauth findIdentity(@Param("source") String source, @Param("uuid") String uuid);
    /**
     * 查询指定用户的全部平台绑定，按 source 升序返回。
     * <p>包含当前配置未启用的平台；可用登录方式判断和响应脱敏由 Service 完成。</p>
     *
     * @param userId 服务端确认的本站用户 ID
     * @return 内部绑定实体列表，无绑定时为空列表
     */
    List<SysUserOauth> findByUser(@Param("userId") long userId);
    /**
     * 按用户 ID 和平台共同限定删除一条绑定。
     * <p>调用方须持有用户行锁，并已检查最近认证及删除后剩余登录方式。</p>
     *
     * @param userId 已确认归属并锁定的用户 ID
     * @param source 待解绑的平台标识
     * @return 删除影响行数，无匹配绑定时为 0
     */
    int deleteBinding(@Param("userId") long userId, @Param("source") String source);
}
