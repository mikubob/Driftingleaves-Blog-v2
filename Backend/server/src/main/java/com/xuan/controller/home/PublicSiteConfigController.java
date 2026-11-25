package com.xuan.controller.home;

import com.xuan.result.Result;
import com.xuan.service.ISiteConfigService;
import com.xuan.vo.ConfigVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 主页端公开站点配置接口
 */
@Slf4j
@RestController("homePublicSiteConfigController")
@RequestMapping("/home/configs")
@RequiredArgsConstructor
public class PublicSiteConfigController {

    private final ISiteConfigService siteConfigService;

    /**
     * 查询公开站点配置列表
     *
     * @return 公开配置列表
     */
    @GetMapping
    public Result<List<ConfigVO>> list() {
        log.info("主页端查询公开站点配置");
        List<ConfigVO> configList = siteConfigService.publicConfigs();
        return Result.ok(configList);
    }
}
