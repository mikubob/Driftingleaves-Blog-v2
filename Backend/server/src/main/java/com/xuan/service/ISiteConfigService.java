package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.ConfigWriteDTO;
import com.xuan.entity.BlogSiteConfig;
import com.xuan.vo.ConfigProfileVO;
import com.xuan.vo.ConfigVO;

import java.util.List;

/**
 * 配置只维护既定白名单，评论策略保存后由原有评论流程直接读取。
 */
public interface ISiteConfigService extends IService<BlogSiteConfig> {

    /** 仅返回允许公开且 isPublic=1 的 site。 */
    List<ConfigVO> publicConfigs();

    /** 仅返回三种固定键，不返回未知配置。 */
    List<ConfigVO> listConfigs();

    /** 未知键或不存在返回404，资料和版本来自同一快照。 */
    ConfigProfileVO detail(String key);

    /** 先锁操作者和配置，再复查身份、权限和版本，值与审计一起提交。 */
    ConfigProfileVO update(String key, ConfigWriteDTO request, String revision);
}
