package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.constant.HttpConstants;
import com.xuan.dto.SiteOwnerProfileWriteDTO;
import com.xuan.entity.SiteOwnerProfile;
import com.xuan.model.ResourceVersion;
import com.xuan.result.Result;
import com.xuan.service.ISiteOwnerProfileService;
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
 * 管理端站长资料接口
 */
@Slf4j
@RestController("adminSiteOwnerProfileController")
@RequestMapping("/admin/home/profile")
@RequiredArgsConstructor
public class SiteOwnerProfileController {

    private final ISiteOwnerProfileService siteOwnerProfileService;

    /**
     * 查询站长资料及版本
     *
     * @return 主人资料及 X-Revision 响应头
     */
    @GetMapping
    @SaCheckPermission("site:ownerProfile:query")
    public ResponseEntity<Result<SiteOwnerProfile>> detail() {
        log.info("查询站长资料");
        ResourceVersion<SiteOwnerProfile> result = siteOwnerProfileService.detail();
        return versionResponse(result);
    }

    /**
     * 设置或更新站长资料
     *
     * @param request 主人资料写入参数
     * @param revision 请求头携带的 If-Revision 版本
     * @return 更新后的主人资料及新 X-Revision 响应头
     */
    @PutMapping
    @SaCheckPermission("site:ownerProfile:update")
    public ResponseEntity<Result<SiteOwnerProfile>> replace(@Valid @RequestBody SiteOwnerProfileWriteDTO request,
            @RequestHeader(value = HttpConstants.HEADER_IF_REVISION, required = false) String revision) {
        log.info("更新站长资料");
        ResourceVersion<SiteOwnerProfile> result = siteOwnerProfileService.replace(request, revision);
        return versionResponse(result);
    }

    /**
     * 组装带版本头的站长资料响应。
     *
     * @param result 资料与版本
     * @return 200 响应
     */
    private ResponseEntity<Result<SiteOwnerProfile>> versionResponse(ResourceVersion<SiteOwnerProfile> result) {
        return ResponseEntity.ok()
                .header(HttpConstants.HEADER_X_REVISION, result.getRevision())
                .body(Result.ok(result.getValue()));
    }
}
