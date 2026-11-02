package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.HomeProfileWriteDTO;
import com.xuan.entity.HomeProfile;
import com.xuan.model.ResourceVersion;
import com.xuan.vo.HomeProfilePublicVO;

/**
 * 站点主人资料业务接口，主页、博客和简历共用。
 */
public interface IHomeProfileService extends IService<HomeProfile> {

    /** 查询站点主人公开资料，未设置时返回 null。 */
    HomeProfilePublicVO publicProfile();

    /** 查询站点主人资料及版本，尚未设置时也返回版本，供首次保存使用。 */
    ResourceVersion<HomeProfile> detail();

    /** 设置或更新站点主人资料，先锁住站点和所选的启用账号，再完整替换资料。 */
    ResourceVersion<HomeProfile> replace(HomeProfileWriteDTO request, String revision);
}
