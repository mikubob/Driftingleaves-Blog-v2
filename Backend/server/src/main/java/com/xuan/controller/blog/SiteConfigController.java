package com.xuan.controller.blog;

import com.xuan.result.Result;
import com.xuan.service.ISiteConfigService;
import com.xuan.vo.ConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台只读允许公开的 site，审核策略即使误标公开也不会返回。
 */
@RestController("publicSiteConfigController")
@RequestMapping("/blog/configs")
@RequiredArgsConstructor
public class SiteConfigController {

    private final ISiteConfigService configs;

    /** 配置值返回对象，私有 site 或没有公开配置时返回空数组。 */
    @GetMapping
    public Result<List<ConfigVO>> list() {
        return Result.ok(configs.publicConfigs());
    }
}
