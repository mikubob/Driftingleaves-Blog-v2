package com.xuan.controller.cv;

import com.xuan.result.Result;
import com.xuan.service.IHomeProfileService;
import com.xuan.vo.HomeProfilePublicVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 简历端站点主人公开资料接口
 */
@Slf4j
@RestController("cvPublicHomeProfileController")
@RequestMapping("/cv/profile")
@RequiredArgsConstructor
public class PublicHomeProfileController {

    private final IHomeProfileService homeProfileService;

    /**
     * 查询站点主人公开资料
     *
     * @return 站点主人公开资料
     */
    @GetMapping
    public Result<HomeProfilePublicVO> detail() {
        log.info("简历端查询站点主人公开资料");
        HomeProfilePublicVO profile = homeProfileService.publicProfile();
        return Result.ok(profile);
    }
}
