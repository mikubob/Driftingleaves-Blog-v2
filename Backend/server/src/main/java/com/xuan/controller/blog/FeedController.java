package com.xuan.controller.blog;

import com.xuan.service.IFeedService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RSS 和站点地图接口，公开读取 XML，不要求邮件订阅、票据或登录。
 */
@RestController
@RequestMapping("/blog")
@RequiredArgsConstructor
public class FeedController {

    private final IFeedService feeds;

    /** 获取 RSS 2.0 订阅，直接返回 XML，失败时返回统一 JSON 错误。 */
    @GetMapping(value = "/rss.xml", produces = "application/rss+xml;charset=UTF-8")
    public String rss() {
        return feeds.rss();
    }

    /** 获取站点地图，page 用于选择子地图，未知页返回 404。 */
    @GetMapping(value = "/sitemap.xml", produces = "application/xml;charset=UTF-8")
    public String sitemap(@RequestParam(required = false) @Min(1) Integer page) {
        return feeds.sitemap(page);
    }
}
