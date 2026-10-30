package com.xuan.service;

import com.xuan.dto.PageViewCreateDTO;
import com.xuan.vo.TrackingResultVO;

/** 公开采集不颁发身份，只负责核验来源与同事务写入访客/PV。 */
public interface IAnalyticsService {
    /** 原始IP只在内存用于地域查询，持久化前脱敏；userId只来自有效会话。 */
    TrackingResultVO track(PageViewCreateDTO request, String origin, String ip, String userAgent);
}
