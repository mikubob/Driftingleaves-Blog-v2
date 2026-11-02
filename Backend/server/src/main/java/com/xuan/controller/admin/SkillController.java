package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.dto.SkillWriteDTO;
import com.xuan.entity.CvSkill;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ISkillService;
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
 * 后台技能管理接口，业务层在同一事务中检查权限和版本、保存资料。
 */
@RestController("adminSkillController")
@RequestMapping("/admin/cv/skills")
@RequiredArgsConstructor
public class SkillController {
    private final ISkillService items;

    /** 分页查询技能，列表不带每条记录的版本，编辑前先查询详情。 */
    @GetMapping
    @SaCheckPermission("cv:skill:query")
    public Result<PageResult<CvSkill>> list(@Valid @ModelAttribute DisplayQueryDTO query) {
        return Result.ok(items.listItems(query));
    }

    /** 根据id查询技能详情，版本放在 X-Revision 响应头。 */
    @GetMapping("/{id}")
    @SaCheckPermission("cv:skill:query")
    public ResponseEntity<Result<CvSkill>> detail(@PathVariable String id) {
        return profile(items.detail(IdUtils.parse(id)));
    }

    /** 新增技能，成功后返回 201 和新版本。 */
    @PostMapping
    @SaCheckPermission("cv:skill:create")
    public ResponseEntity<Result<CvSkill>> create(@Valid @RequestBody SkillWriteDTO request) {
        ResourceVersion<CvSkill> result = items.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/cv/skills/" + result.value().getSkillId()))
                .header("X-Revision", result.revision()).body(Result.ok(result.value()));
    }

    /** 根据id更新技能，PUT 要传完整资料，加锁后检查版本。 */
    @PutMapping("/{id}")
    @SaCheckPermission("cv:skill:update")
    public ResponseEntity<Result<CvSkill>> update(@PathVariable String id, @Valid @RequestBody SkillWriteDTO request,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(items.update(IdUtils.parse(id), request, revision));
    }

    /** 批量删除技能，单条也走此接口，业务层统一检查权限、版本并处理失败。 */
    @PostMapping("/batch-delete")
    @SaCheckPermission("cv:skill:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        items.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /** 返回技能资料，版本放在 X-Revision 响应头。 */
    private ResponseEntity<Result<CvSkill>> profile(ResourceVersion<CvSkill> result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.value()));
    }
}
