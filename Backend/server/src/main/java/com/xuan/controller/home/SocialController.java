package com.xuan.controller.home;

import com.xuan.result.Result;
import com.xuan.service.ISocialService;
import com.xuan.vo.SocialPublicVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开展示只返回启用资料。
 */
@RestController("homeSocialController")
@RequestMapping("/home/social-links")
@RequiredArgsConstructor
public class SocialController {

    private final ISocialService items;

    /** 不输出管理字段或原始Markdown。 */
    @GetMapping
    public Result<List<SocialPublicVO>> list() {
        return Result.ok(items.publicItems());
    }
}
