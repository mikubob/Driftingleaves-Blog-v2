package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.constant.HttpConstants;
import com.xuan.dto.HomeProfileWriteDTO;
import com.xuan.entity.HomeProfile;
import com.xuan.model.ResourceVersion;
import com.xuan.result.Result;
import com.xuan.service.IHomeProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端站点主人资料接口
 */
@Slf4j
@RestController("adminHomeProfileController")
@RequestMapping("/admin/home/profile")
@RequiredArgsConstructor
public class HomeProfileController {

    private final IHomeProfileService homeProfileService;

    /**
     * 查询站点主人资料及版本
     *
     * @return 主人资料及 X-Revision 响应头
     */
    @GetMapping
    @SaCheckPermission("home:profile:query")
    public ResponseEntity<Result<HomeProfile>> detail() {
        log.info("查询站点主人资料");
        ResourceVersion<HomeProfile> result = homeProfileService.detail();
        return versionResponse(result);
    }

    /**
     * 设置或更新站点主人资料
     *
     * @param request 主人资料写入参数
     * @param revision 请求头携带的 If-Revision 版本
     * @return 更新后的主人资料及新 X-Revision 响应头
     */
    @PutMapping
    @SaCheckPermission("home:profile:update")
    public ResponseEntity<Result<HomeProfile>> replace(@Valid @RequestBody HomeProfileWriteDTO request,
            @RequestHeader(value = HttpConstants.HEADER_IF_REVISION, required = false) String revision) {
        log.info("更新站点主人资料");
        ResourceVersion<HomeProfile> result = homeProfileService.replace(request, revision);
        return versionResponse(result);
    }

    /**
     * 组装带版本头的站点主人资料响应。
     *
     * @param result 资料与版本
     * @return 200 响应
     */
    private ResponseEntity<Result<HomeProfile>> versionResponse(ResourceVersion<HomeProfile> result) {
        return ResponseEntity.ok()
                .header(HttpConstants.HEADER_X_REVISION, result.getRevision())
                .body(Result.ok(result.getValue()));
    }
}
