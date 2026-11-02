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
 * 公开站点共用允许公开的 site 配置，不返回审核策略。
 */
@RestController("publicSiteConfigController")
@RequestMapping({"/blog/configs", "/home/configs", "/cv/configs"})
@RequiredArgsConstructor
public class PublicSiteConfigController {

    private final ISiteConfigService configs;

    /** 配置值返回对象，私有 site 或没有公开配置时返回空数组。 */
    @GetMapping
    public Result<List<ConfigVO>> list() {
        return Result.ok(configs.publicConfigs());
    }
}
