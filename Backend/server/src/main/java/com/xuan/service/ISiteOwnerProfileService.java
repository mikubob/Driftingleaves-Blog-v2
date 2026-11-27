package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.SiteOwnerProfileWriteDTO;
import com.xuan.entity.SiteOwnerProfile;
import com.xuan.model.ResourceVersion;
import com.xuan.vo.SiteOwnerProfilePublicVO;

/**
 * 站长资料业务接口，主页、博客和简历共用。
 */
public interface ISiteOwnerProfileService extends IService<SiteOwnerProfile> {

    /**
     * 查询站长公开资料，未设置时返回 null。
     */
    SiteOwnerProfilePublicVO publicProfile();

    /**
     * 查询站长资料及版本，尚未设置时也返回版本，供首次保存使用。
     */
    ResourceVersion<SiteOwnerProfile> detail();

    /**
     * 设置或更新站长资料，锁定资料后检查版本并完整保存。
     */
    ResourceVersion<SiteOwnerProfile> replace(SiteOwnerProfileWriteDTO request, String revision);
}
