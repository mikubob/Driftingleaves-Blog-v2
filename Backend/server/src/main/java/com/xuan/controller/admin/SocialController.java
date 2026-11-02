package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.BatchBinaryStatusDTO;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.dto.SocialCreateDTO;
import com.xuan.dto.SocialWriteDTO;
import com.xuan.entity.HomeSocialLink;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ISocialService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.BatchVersionsVO;
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
 * 后台社交链接管理接口，业务层在同一事务中检查权限和版本、保存资料。
 */
@RestController("adminSocialController")
@RequestMapping("/admin/home/social-links")
@RequiredArgsConstructor
public class SocialController {

    private final ISocialService items;

    /** 分页查询社交链接，列表不带每条记录的版本，编辑前先查询详情。 */
    @GetMapping
    @SaCheckPermission("home:social:query")
    public Result<PageResult<HomeSocialLink>> list(@Valid @ModelAttribute DisplayQueryDTO query) {
        return Result.ok(items.listItems(query));
    }

    /** 根据id查询社交链接详情，版本放在 X-Revision 响应头。 */
    @GetMapping("/{id}")
    @SaCheckPermission("home:social:query")
    public ResponseEntity<Result<HomeSocialLink>> detail(@PathVariable String id) {
        return profile(items.detail(IdUtils.parse(id)));
    }

    /** 新增社交链接，成功后返回 201 和新版本。 */
    @PostMapping
    @SaCheckPermission("home:social:create")
    public ResponseEntity<Result<HomeSocialLink>> create(@Valid @RequestBody SocialCreateDTO request) {
        ResourceVersion<HomeSocialLink> result = items.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/home/social-links/" + result.value().getSocialId()))
                .header("X-Revision", result.revision()).body(Result.ok(result.value()));
    }

    /** 根据id更新社交链接，PUT 要传完整资料，加锁后检查版本。 */
    @PutMapping("/{id}")
    @SaCheckPermission("home:social:update")
    public ResponseEntity<Result<HomeSocialLink>> update(@PathVariable String id,
            @Valid @RequestBody SocialWriteDTO request,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(items.update(IdUtils.parse(id), request, revision));
    }

    /** 批量修改状态，单条也提交一项，任一失败则整批回滚。 */
    @PutMapping("/batch-status")
    @SaCheckPermission("home:social:update")
    public Result<BatchVersionsVO> status(@Valid @RequestBody BatchBinaryStatusDTO body) {
        return Result.ok(items.changeStatusBatch(body.getItems(), body.getStatus()));
    }

    /** 批量删除社交链接，单条也走此接口，业务层统一检查权限、版本并处理失败。 */
    @PostMapping("/batch-delete")
    @SaCheckPermission("home:social:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        items.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /** 返回社交链接资料，版本放在 X-Revision 响应头。 */
    private ResponseEntity<Result<HomeSocialLink>> profile(ResourceVersion<HomeSocialLink> result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.value()));
    }
}
