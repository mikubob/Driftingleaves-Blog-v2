package com.xuan.model;

import com.xuan.dto.OAuthAuthorizeDTO.Operation;
import com.xuan.vo.OAuthBindingVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 授权结果只存验证过的身份和绑定资料，不存平台 Token 或 code。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthTicket implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 发起授权的站点
    private String site;

    // 浏览器标识摘要
    private String browserHash;

    // 第三方平台来源
    private String source;

    // 操作类型
    private Operation operation;

    // 目标用户ID
    private Long userId;

    // 原会话令牌摘要
    private String tokenHash;

    // 原会话认证时间
    private long authenticatedAt;

    // 第三方绑定信息
    private OAuthBindingVO binding;

    // 第三方平台唯一标识
    private String uuid;
}
