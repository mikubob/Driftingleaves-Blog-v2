package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.ConfigWriteDTO;
import com.xuan.result.Result;
import com.xuan.service.ISiteConfigService;
import com.xuan.vo.ConfigProfileVO;
import com.xuan.vo.ConfigVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 配置列表、详情、完整替换都走后台入口及自己的业务权限，不开放建键和删键。
 */
@RestController
@RequestMapping("/admin/configs")
@RequiredArgsConstructor
public class SiteConfigController {
    private final ISiteConfigService configs;

    /** 配置固定三种键，不做没有需要的分页。 */
    @GetMapping
    @SaCheckPermission("blog:config:query")
    public Result<List<ConfigVO>> list() {
        return Result.ok(configs.listConfigs());
    }

    /** 查询未知键返回404，已有键附带 X-Revision。 */
    @GetMapping("/{configKey}")
    @SaCheckPermission("blog:config:query")
    public ResponseEntity<Result<ConfigVO>> detail(@PathVariable String configKey) {
        return profile(configs.detail(configKey));
    }

    /** 完整替换值与公开标记，版本与锁后权限由写事务核对。 */
    @PutMapping("/{configKey}")
    @SaCheckPermission("blog:config:update")
    public ResponseEntity<Result<ConfigVO>> update(@PathVariable String configKey,
            @Valid @RequestBody ConfigWriteDTO body,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(configs.update(configKey, body, revision));
    }

    /** 业务版本只放响应头，不混进配置对象。 */
    private ResponseEntity<Result<ConfigVO>> profile(ConfigProfileVO result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.config()));
    }
}
