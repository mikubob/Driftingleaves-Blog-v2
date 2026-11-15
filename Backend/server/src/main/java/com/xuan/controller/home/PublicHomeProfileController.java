package com.xuan.controller.home;

import com.xuan.result.Result;
import com.xuan.service.IHomeProfileService;
import com.xuan.vo.HomeProfilePublicVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站点主人公开资料接口，不返回账号id和登录邮箱。
 */
@RestController("homePublicHomeProfileController")
@RequestMapping("/home/profile")
@RequiredArgsConstructor
public class PublicHomeProfileController {

    private final IHomeProfileService profiles;

    /** 查询站点主人公开资料，不返回账号id和登录邮箱。 */
    @GetMapping
    public Result<HomeProfilePublicVO> detail() {
        return Result.ok(profiles.publicProfile());
    }
}
