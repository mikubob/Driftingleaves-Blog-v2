package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.HomeProfileWriteDTO;
import com.xuan.entity.HomeProfile;
import com.xuan.model.ResourceVersion;
import com.xuan.result.Result;
import com.xuan.service.IHomeProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;


/** 全站主人资料维护，空资源GET也提供首次设置版本。 */
@RestController("adminHomeProfileController")
@RequestMapping("/admin/home/profile")
@RequiredArgsConstructor
public class HomeProfileController {
    private final IHomeProfileService profiles;
    /** 返回当前资料及版本。 */
    @GetMapping
    @SaCheckPermission("home:profile:query")
    public ResponseEntity<Result<HomeProfile>> detail() { return response(profiles.detail()); }
    /** 首次设置与完整替换都返回200。 */
    @PutMapping
    @SaCheckPermission("home:profile:update")
    public ResponseEntity<Result<HomeProfile>> replace(@Valid @RequestBody HomeProfileWriteDTO request,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return response(profiles.replace(request, revision));
    }
    /** 版本不写进业务JSON。 */
    private ResponseEntity<Result<HomeProfile>> response(ResourceVersion<HomeProfile> result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.value()));
    }
}
