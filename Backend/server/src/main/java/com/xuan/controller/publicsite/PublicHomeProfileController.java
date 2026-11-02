package com.xuan.controller.publicsite;

import com.xuan.result.Result;
import com.xuan.service.IHomeProfileService;
import com.xuan.vo.HomeProfilePublicVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开站点共用站点主人资料，不返回账号 ID 和登录邮箱。
 */
@RestController("publicHomeProfileController")
@RequestMapping({"/blog/profile", "/home/profile", "/cv/profile"})
@RequiredArgsConstructor
public class PublicHomeProfileController {

    private final IHomeProfileService profiles;

    /** 不返回账号ID和登录邮箱。 */
    @GetMapping
    public Result<HomeProfilePublicVO> detail() {
        return Result.ok(profiles.publicProfile());
    }
}
