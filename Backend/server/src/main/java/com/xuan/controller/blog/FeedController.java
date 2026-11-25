package com.xuan.controller.blog;

import com.xuan.service.IFeedService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 博客端 RSS 与站点地图接口
 */
@Slf4j
@RestController
@RequestMapping("/blog")
@RequiredArgsConstructor
public class FeedController {

    private final IFeedService feedService;

    /**
     * 获取 RSS 2.0 订阅内容
     *
     * @return RSS XML 字符串
     */
    @GetMapping(value = "/rss.xml", produces = "application/rss+xml;charset=UTF-8")
    public String rss() {
        log.info("获取 RSS 订阅内容");
        return feedService.rss();
    }

    /**
     * 获取站点地图内容
     *
     * @param page 子地图页码，可为空
     * @return Sitemap XML 字符串
     */
    @GetMapping(value = "/sitemap.xml", produces = "application/xml;charset=UTF-8")
    public String sitemap(@RequestParam(required = false) @Min(1) Integer page) {
        log.info("获取站点地图: page={}", page);
        return feedService.sitemap(page);
    }
}
