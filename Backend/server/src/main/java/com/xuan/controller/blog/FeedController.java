package com.xuan.controller.blog;

import com.xuan.service.IFeedService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * XML派生资源公开读取，不要求邮箱订阅、票据或本站会话。
 */
@RestController
@RequestMapping("/blog")
@RequiredArgsConstructor
public class FeedController {

    private final IFeedService feeds;

    /** RSS2.0直接返回XML，错误仍由统一异常处理器返回JSON。 */
    @GetMapping(value = "/rss.xml", produces = "application/rss+xml;charset=UTF-8")
    public String rss() {
        return feeds.rss();
    }

    /** page只用于同一路径的子图，未知页404。 */
    @GetMapping(value = "/sitemap.xml", produces = "application/xml;charset=UTF-8")
    public String sitemap(@RequestParam(required = false) @Min(1) Integer page) {
        return feeds.sitemap(page);
    }
}
