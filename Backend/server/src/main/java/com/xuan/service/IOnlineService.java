package com.xuan.service;

import com.xuan.dto.OnlineTicketCreateDTO;
import com.xuan.vo.OnlineTicketVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 在线统计票据业务接口，检查站点和可选的登录身份后，生成一次性票据。
 */
public interface IOnlineService {

    /**
     * 校验站点归属并生成一次性在线统计票据，从请求中解析可信客户端 IP。
     *
     * @param expectedSiteCode 接口所属端站点编码
     * @param request 票据创建参数
     * @param origin 请求来源
     * @param httpRequest 当前 HTTP 请求
     * @return 在线统计票据
     */
    OnlineTicketVO issueForSite(String expectedSiteCode, OnlineTicketCreateDTO request, String origin,
            HttpServletRequest httpRequest);

    /**
     * 生成一次性在线统计票据，先检查来源、站点和可选的登录会话，此时不增加在线人数。
     */
    OnlineTicketVO issue(OnlineTicketCreateDTO request, String origin, String ip);
}
