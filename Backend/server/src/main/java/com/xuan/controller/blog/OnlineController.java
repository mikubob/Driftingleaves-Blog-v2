package com.xuan.controller.blog;

import com.xuan.dto.OnlineTicketCreateDTO;
import com.xuan.properties.AuthProperties;
import com.xuan.result.Result;
import com.xuan.service.IOnlineService;
import com.xuan.utils.IpUtils;
import com.xuan.vo.OnlineTicketVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 公开可选 Bearer 入口，票据用途仅限在线统计。 */
@RestController
@RequestMapping("/analytics/online/tickets")
@RequiredArgsConstructor
public class OnlineController {
    private final IOnlineService online;
    private final AuthProperties auth;

    /** 签票成功返回 201；可选 Bearer 必须有效，响应禁止缓存。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<OnlineTicketVO> issue(@Valid @RequestBody OnlineTicketCreateDTO body, HttpServletRequest request) {
        return Result.ok(online.issue(body, request.getHeader("Origin"),
                IpUtils.clientIp(request, auth.getTrustedProxies())));
    }
}
