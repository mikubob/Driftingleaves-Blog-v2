package com.xuan.controller.publicsite;

import com.xuan.result.Result;
import com.xuan.service.ISiteConfigService;
import com.xuan.vo.ConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开站点配置接口，各站点共用，只返回允许公开的 site 配置，不返回审核设置。
 */
@RestController("publicSiteConfigController")
@RequestMapping({"/blog/configs", "/home/configs", "/cv/configs"})
@RequiredArgsConstructor
public class PublicSiteConfigController {

    private final ISiteConfigService configs;

    /** 查询公开站点配置，配置值返回对象，site 不公开或无公开配置时返回空数组。 */
    @GetMapping
    public Result<List<ConfigVO>> list() {
        return Result.ok(configs.publicConfigs());
    }
}
