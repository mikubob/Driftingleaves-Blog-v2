package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.BatchBinaryStatusDTO;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.ExperienceCreateDTO;
import com.xuan.dto.ExperienceQueryDTO;
import com.xuan.dto.ExperienceWriteDTO;
import com.xuan.entity.CvExperience;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IExperienceService;
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
 * 后台经历管理接口，业务层在同一事务中检查权限和版本、保存资料。
 */
@RestController("adminExperienceController")
@RequestMapping("/admin/cv/experiences")
@RequiredArgsConstructor
public class ExperienceController {

    private final IExperienceService items;

    /** 分页查询经历，列表不带每条记录的版本，编辑前先查询详情。 */
    @GetMapping
    @SaCheckPermission("cv:experience:query")
    public Result<PageResult<CvExperience>> list(@Valid @ModelAttribute ExperienceQueryDTO query) {
        return Result.ok(items.listItems(query));
    }

    /** 根据id查询经历详情，版本放在 X-Revision 响应头。 */
    @GetMapping("/{id}")
    @SaCheckPermission("cv:experience:query")
    public ResponseEntity<Result<CvExperience>> detail(@PathVariable String id) {
        return profile(items.detail(IdUtils.parse(id)));
    }

    /** 新增经历，成功后返回 201 和新版本。 */
    @PostMapping
    @SaCheckPermission("cv:experience:create")
    public ResponseEntity<Result<CvExperience>> create(@Valid @RequestBody ExperienceCreateDTO request) {
        ResourceVersion<CvExperience> result = items.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/cv/experiences/" + result.value().getExperienceId()))
                .header("X-Revision", result.revision()).body(Result.ok(result.value()));
    }

    /** 根据id更新经历，PUT 要传完整资料，加锁后检查版本。 */
    @PutMapping("/{id}")
    @SaCheckPermission("cv:experience:update")
    public ResponseEntity<Result<CvExperience>> update(@PathVariable String id,
            @Valid @RequestBody ExperienceWriteDTO request,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(items.update(IdUtils.parse(id), request, revision));
    }

    /** 批量修改状态，单条也提交一项，任一失败则整批回滚。 */
    @PutMapping("/batch-status")
    @SaCheckPermission("cv:experience:update")
    public Result<BatchVersionsVO> status(@Valid @RequestBody BatchBinaryStatusDTO body) {
        return Result.ok(items.changeStatusBatch(body.getItems(), body.getStatus()));
    }

    /** 批量删除经历，单条也走此接口，业务层统一检查权限、版本并处理失败。 */
    @PostMapping("/batch-delete")
    @SaCheckPermission("cv:experience:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        items.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /** 返回经历资料，版本放在 X-Revision 响应头。 */
    private ResponseEntity<Result<CvExperience>> profile(ResourceVersion<CvExperience> result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.value()));
    }
}
