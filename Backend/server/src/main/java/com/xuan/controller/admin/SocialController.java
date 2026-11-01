package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.dto.SocialWriteDTO;
import com.xuan.entity.HomeSocialLink;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ISocialService;
import com.xuan.utils.IdUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 后台全站资料维护，权限、版本和写入由同一业务事务落实。
 */
@RestController("adminSocialController")
@RequestMapping("/admin/home/social-links")
@RequiredArgsConstructor
public class SocialController {

    private final ISocialService items;

    /** 分页不返回逐条版本，编辑前读取详情。 */
    @GetMapping
    @SaCheckPermission("home:social:query")
    public Result<PageResult<HomeSocialLink>> list(@Valid @ModelAttribute DisplayQueryDTO query) {
        return Result.ok(items.listItems(query));
    }

    /** 资料版本放响应头。 */
    @GetMapping("/{id}")
    @SaCheckPermission("home:social:query")
    public ResponseEntity<Result<HomeSocialLink>> detail(@PathVariable String id) {
        return profile(items.detail(IdUtils.parse(id)));
    }

    /** 创建成功返回201和新资料版本。 */
    @PostMapping
    @SaCheckPermission("home:social:create")
    public ResponseEntity<Result<HomeSocialLink>> create(@Valid @RequestBody SocialWriteDTO request) {
        ResourceVersion<HomeSocialLink> result = items.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/home/social-links/" + result.value().getSocialId()))
                .header("X-Revision", result.revision()).body(Result.ok(result.value()));
    }

    /** PUT完整替换并在锁内核对版本。 */
    @PutMapping("/{id}")
    @SaCheckPermission("home:social:update")
    public ResponseEntity<Result<HomeSocialLink>> update(@PathVariable String id,
            @Valid @RequestBody SocialWriteDTO request,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(items.update(IdUtils.parse(id), request, revision));
    }

    /** 单个和多个目标共用入口，权限、版本及失败结果由业务层统一处理。 */
    @PostMapping("/batch-delete")
    @SaCheckPermission("home:social:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        items.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /** 响应体只放资料。 */
    private ResponseEntity<Result<HomeSocialLink>> profile(ResourceVersion<HomeSocialLink> result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.value()));
    }
}
