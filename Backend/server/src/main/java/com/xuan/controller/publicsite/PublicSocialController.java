package com.xuan.controller.publicsite;

import com.xuan.result.Result;
import com.xuan.service.ISocialService;
import com.xuan.vo.SocialPublicVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开站点共用启用的社交链接列表。
 */
@RestController("publicSocialController")
@RequestMapping({"/blog/social-links", "/home/social-links", "/cv/social-links"})
@RequiredArgsConstructor
public class PublicSocialController {

    private final ISocialService items;

    /** 不输出管理字段或原始Markdown。 */
    @GetMapping
    public Result<List<SocialPublicVO>> list() {
        return Result.ok(items.publicItems());
    }
}
