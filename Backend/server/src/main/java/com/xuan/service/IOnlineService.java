package com.xuan.service;

import com.xuan.dto.OnlineTicketCreateDTO;
import com.xuan.vo.OnlineTicketVO;

/** 核验公开站点和可选 HTTP 身份，签发一次性在线入场票据。 */
public interface IOnlineService {
    /** 来源、站点和可选会话有效后签发票据，不改变在线人数。 */
    OnlineTicketVO issue(OnlineTicketCreateDTO request, String origin, String ip);
}
