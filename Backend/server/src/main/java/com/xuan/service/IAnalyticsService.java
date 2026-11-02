package com.xuan.service;

import com.xuan.dto.PageViewCreateDTO;
import com.xuan.vo.TrackingResultVO;

/**
 * 访问采集业务接口，检查来源后，在同一事务中保存访客和浏览记录，不创建登录身份。
 */
public interface IAnalyticsService {

    /** 记录站点访问，原始 IP 只在内存中查询属地，写入数据库前先隐藏部分地址；userId 只取有效会话。 */
    TrackingResultVO track(PageViewCreateDTO request, String origin, String ip, String userAgent);
}
