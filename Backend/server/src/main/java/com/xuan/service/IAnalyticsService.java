package com.xuan.service;

import com.xuan.dto.PageViewCreateDTO;
import com.xuan.vo.TrackingResultVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 访问采集业务接口，检查来源后，在同一事务中保存访客和浏览记录，不创建登录身份。
 */
public interface IAnalyticsService {

    /**
     * 校验站点归属并记录站点访问，从请求中解析可信客户端 IP 与 User-Agent。
     *
     * @param expectedSiteCode 接口所属端站点编码
     * @param request 访问记录参数
     * @param origin 请求来源
     * @param httpRequest 当前 HTTP 请求
     * @return 采集结果
     */
    TrackingResultVO trackSite(String expectedSiteCode, PageViewCreateDTO request, String origin,
            HttpServletRequest httpRequest);

    /**
     * 记录站点访问，原始 IP 只在内存中查询属地，写入数据库前先隐藏部分地址；userId 只取有效会话。
     */
    TrackingResultVO track(PageViewCreateDTO request, String origin, String ip, String userAgent);
}
