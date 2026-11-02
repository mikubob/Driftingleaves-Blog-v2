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
 * 后台站点配置接口，分别检查查询和更新权限，不提供配置键的新增或删除。
 */
@RestController
@RequestMapping("/admin/configs")
@RequiredArgsConstructor
public class SiteConfigController {
    private final ISiteConfigService configs;

    /** 查询站点配置列表，只返回三种固定配置，不分页。 */
    @GetMapping
    @SaCheckPermission("blog:config:query")
    public Result<List<ConfigVO>> list() {
        return Result.ok(configs.listConfigs());
    }

    /** 根据配置键查询站点配置，未知键返回 404，已有配置的版本放在 X-Revision。 */
    @GetMapping("/{configKey}")
    @SaCheckPermission("blog:config:query")
    public ResponseEntity<Result<ConfigVO>> detail(@PathVariable String configKey) {
        return profile(configs.detail(configKey));
    }

    /** 根据配置键更新站点配置，完整替换值和公开标记，在写事务中加锁并检查权限和版本。 */
    @PutMapping("/{configKey}")
    @SaCheckPermission("blog:config:update")
    public ResponseEntity<Result<ConfigVO>> update(@PathVariable String configKey,
            @Valid @RequestBody ConfigWriteDTO body,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(configs.update(configKey, body, revision));
    }

    /** 返回站点配置资料，版本放在 X-Revision 响应头。 */
    private ResponseEntity<Result<ConfigVO>> profile(ConfigProfileVO result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.config()));
    }
}
