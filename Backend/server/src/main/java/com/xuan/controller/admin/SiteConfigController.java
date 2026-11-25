package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.constant.HttpConstants;
import com.xuan.dto.ConfigWriteDTO;
import com.xuan.result.Result;
import com.xuan.service.ISiteConfigService;
import com.xuan.vo.ConfigProfileVO;
import com.xuan.vo.ConfigVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端站点配置接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/configs")
@RequiredArgsConstructor
public class SiteConfigController {

    private final ISiteConfigService siteConfigService;

    /**
     * 查询站点配置列表
     *
     * @return 站点配置列表
     */
    @GetMapping
    @SaCheckPermission("blog:config:query")
    public Result<List<ConfigVO>> list() {
        log.info("查询站点配置列表");
        List<ConfigVO> configList = siteConfigService.listConfigs();
        return Result.ok(configList);
    }

    /**
     * 根据配置键查询站点配置详情
     *
     * @param configKey 配置键
     * @return 配置详情及 X-Revision 响应头
     */
    @GetMapping("/{configKey}")
    @SaCheckPermission("blog:config:query")
    public ResponseEntity<Result<ConfigVO>> detail(@PathVariable String configKey) {
        log.info("根据配置键查询站点配置详情: configKey={}", configKey);
        ConfigProfileVO profile = siteConfigService.detail(configKey);
        return profileResponse(profile);
    }

    /**
     * 根据配置键更新站点配置
     *
     * @param configKey 配置键
     * @param body 配置写入参数
     * @param revision 请求头携带的 If-Revision 版本
     * @return 更新后的配置详情及新 X-Revision 响应头
     */
    @PutMapping("/{configKey}")
    @SaCheckPermission("blog:config:update")
    public ResponseEntity<Result<ConfigVO>> update(@PathVariable String configKey,
            @Valid @RequestBody ConfigWriteDTO body,
            @RequestHeader(value = HttpConstants.HEADER_IF_REVISION, required = false) String revision) {
        log.info("更新站点配置: configKey={}", configKey);
        ConfigProfileVO profile = siteConfigService.update(configKey, body, revision);
        return profileResponse(profile);
    }

    /**
     * 组装带版本头的站点配置响应。
     *
     * @param result 配置详情与版本
     * @return 200 响应
     */
    private ResponseEntity<Result<ConfigVO>> profileResponse(ConfigProfileVO result) {
        return ResponseEntity.ok()
                .header(HttpConstants.HEADER_X_REVISION, result.getRevision())
                .body(Result.ok(result.getConfig()));
    }
}
