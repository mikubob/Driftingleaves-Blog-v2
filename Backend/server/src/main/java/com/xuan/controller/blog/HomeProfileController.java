package com.xuan.controller.blog;

import com.xuan.result.Result;
import com.xuan.service.IHomeProfileService;
import com.xuan.vo.HomeProfilePublicVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 博客、主页和CV都读取这份公开资料。
 */
@RestController("blogHomeProfileController")
@RequestMapping("/home/profile")
@RequiredArgsConstructor
public class HomeProfileController {

    private final IHomeProfileService profiles;

    /** 不返回账号ID和登录邮箱。 */
    @GetMapping
    public Result<HomeProfilePublicVO> detail() {
        return Result.ok(profiles.publicProfile());
    }
}
