package com.xuan.controller.home;

import com.xuan.dto.OnlineTicketCreateDTO;
import com.xuan.result.Result;
import com.xuan.service.IOnlineService;
import com.xuan.vo.OnlineTicketVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主页端在线统计票据接口
 */
@Slf4j
@RestController("homeOnlineController")
@RequestMapping("/home/analytics/online/tickets")
@RequiredArgsConstructor
public class OnlineController {

    private final IOnlineService onlineService;

    /**
     * 生成主页端在线统计票据
     *
     * @param body 票据创建参数
     * @param request 当前 HTTP 请求
     * @return 在线统计票据
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<OnlineTicketVO> issue(@Valid @RequestBody OnlineTicketCreateDTO body,
            HttpServletRequest request) {
        log.info("生成主页端在线统计票据: pagePath={}", body.pagePath());
        String origin = request.getHeader("Origin");
        OnlineTicketVO ticket = onlineService.issueForSite("home", body, origin, request);
        return Result.ok(ticket);
    }
}
