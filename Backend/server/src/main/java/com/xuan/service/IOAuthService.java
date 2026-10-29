package com.xuan.service;

import com.xuan.dto.OAuthAuthorizeDTO.Operation;
import com.xuan.vo.OAuthAuthorizeVO;

/** OAuth 协调契约：浏览器、操作、平台、目标用户及原 Token 均绑定一次性流程。 */
public interface IOAuthService {
    OAuthAuthorizeVO authorize(String source, Operation operation, String browser);
    /** 可信且已消费流程返回固定回跳地址；不可信关系直接抛出 HTTP 错误。 */
    String callback(String source, String state, String code, String error, String browser);
    /** LOGIN 签发 Session；BIND 必须携带原 Token，只返回绑定结果。 */
    Object exchange(String ticket, String browser);
}
