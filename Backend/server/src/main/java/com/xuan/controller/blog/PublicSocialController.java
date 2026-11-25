package com.xuan.controller.blog;

import com.xuan.result.Result;
import com.xuan.service.ISocialService;
import com.xuan.vo.SocialPublicVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 博客端公开社交链接接口
 */
@Slf4j
@RestController("blogPublicSocialController")
@RequestMapping("/blog/social-links")
@RequiredArgsConstructor
public class PublicSocialController {

    private final ISocialService socialService;

    /**
     * 查询公开社交链接列表
     *
     * @return 社交链接列表
     */
    @GetMapping
    public Result<List<SocialPublicVO>> list() {
        log.info("博客端查询公开社交链接列表");
        List<SocialPublicVO> socialList = socialService.publicItems();
        return Result.ok(socialList);
    }
}
