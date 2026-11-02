package com.xuan.service;

import com.xuan.dto.OnlineTicketCreateDTO;
import com.xuan.vo.OnlineTicketVO;

/** 在线统计票据业务接口，检查站点和可选的登录身份后，生成一次性票据。 */
public interface IOnlineService {
    /** 生成一次性在线统计票据，先检查来源、站点和可选的登录会话，此时不增加在线人数。 */
    OnlineTicketVO issue(OnlineTicketCreateDTO request, String origin, String ip);
}
