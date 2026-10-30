package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.HomeProfileWriteDTO;
import com.xuan.entity.HomeProfile;
import com.xuan.model.ResourceVersion;
import com.xuan.vo.HomeProfilePublicVO;

/**
 * 主页、博客与简历共用固定站点主人资料。
 */
public interface IHomeProfileService extends IService<HomeProfile> {

    /** 未设置时返回null。 */
    HomeProfilePublicVO publicProfile();

    /** 空资源也返回版本供首次设置使用。 */
    ResourceVersion<HomeProfile> detail();

    /** 锁定站点和所选启用账号后完整替换。 */
    ResourceVersion<HomeProfile> replace(HomeProfileWriteDTO request, String revision);
}
