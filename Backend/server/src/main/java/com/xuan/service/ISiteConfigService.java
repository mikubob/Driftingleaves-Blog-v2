package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.ConfigWriteDTO;
import com.xuan.entity.BlogSiteConfig;
import com.xuan.vo.ConfigProfileVO;
import com.xuan.vo.ConfigVO;

import java.util.List;

/**
 * 站点配置业务接口，只维护允许的配置，保存后评论直接读取新设置。
 */
public interface ISiteConfigService extends IService<BlogSiteConfig> {

    /** 查询公开站点配置，只返回允许公开且 isPublic=1 的 site 配置。 */
    List<ConfigVO> publicConfigs();

    /** 查询后台站点配置，只返回三种固定配置键，不返回未知配置。 */
    List<ConfigVO> listConfigs();

    /** 根据配置键查询站点配置及版本，未知或不存在的键返回 404，资料和版本在同一个只读事务中读取。 */
    ConfigProfileVO detail(String key);

    /** 根据配置键更新站点配置，先锁住操作者和配置，再重新检查身份、权限和版本；配置值和成功日志一起提交。 */
    ConfigProfileVO update(String key, ConfigWriteDTO request, String revision);
}
