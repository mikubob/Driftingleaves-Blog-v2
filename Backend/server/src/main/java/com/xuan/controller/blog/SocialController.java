package com.xuan.controller.blog;

import java.util.List;

import com.xuan.service.ISocialService;
import com.xuan.vo.SocialPublicVO;
import com.xuan.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/** 公开展示只返回启用资料。 */
@RestController("blogSocialController")
@RequestMapping("/home/social-links")
@RequiredArgsConstructor
public class SocialController {
    private final ISocialService items;
    /** 不输出管理字段或原始Markdown。 */
    @GetMapping
    public Result<List<SocialPublicVO>> list() { return Result.ok(items.publicItems()); }
}
