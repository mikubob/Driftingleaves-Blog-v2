package com.xuan.controller.blog;

import com.xuan.result.Result;
import com.xuan.service.ISiteOwnerProfileService;
import com.xuan.vo.SiteOwnerProfilePublicVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 博客端站长公开资料接口
 */
@Slf4j
@RestController("blogPublicSiteOwnerProfileController")
@RequestMapping("/blog/profile")
@RequiredArgsConstructor
public class PublicSiteOwnerProfileController {

    private final ISiteOwnerProfileService siteOwnerProfileService;

    /**
     * 查询站长公开资料
     *
     * @return 站长公开资料
     */
    @GetMapping
    public Result<SiteOwnerProfilePublicVO> detail() {
        log.info("博客端查询站长公开资料");
        SiteOwnerProfilePublicVO profile = siteOwnerProfileService.publicProfile();
        return Result.ok(profile);
    }
}
