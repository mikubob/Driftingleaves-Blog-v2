package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.constant.HttpConstants;
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
import lombok.extern.slf4j.Slf4j;
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
 * 管理端简历经历接口
 */
@Slf4j
@RestController("adminExperienceController")
@RequestMapping("/admin/cv/experiences")
@RequiredArgsConstructor
public class ExperienceController {

    private final IExperienceService experienceService;

    /**
     * 分页查询经历列表
     *
     * @param query 查询参数
     * @return 经历分页结果
     */
    @GetMapping
    @SaCheckPermission("cv:experience:query")
    public Result<PageResult<CvExperience>> list(@Valid @ModelAttribute ExperienceQueryDTO query) {
        log.info("分页查询经历列表: {}", query);
        PageResult<CvExperience> pageResult = experienceService.listItems(query);
        return Result.ok(pageResult);
    }

    /**
     * 根据 ID 查询经历详情
     *
     * @param id 经历 ID
     * @return 经历详情及 X-Revision 响应头
     */
    @GetMapping("/{id}")
    @SaCheckPermission("cv:experience:query")
    public ResponseEntity<Result<CvExperience>> detail(@PathVariable String id) {
        log.info("根据 ID 查询经历详情: id={}", id);
        ResourceVersion<CvExperience> result = experienceService.detail(IdUtils.parse(id));
        return versionResponse(result);
    }

    /**
     * 新增经历
     *
     * @param request 经历创建参数
     * @return 201 响应、Location 及 X-Revision 响应头
     */
    @PostMapping
    @SaCheckPermission("cv:experience:create")
    public ResponseEntity<Result<CvExperience>> create(@Valid @RequestBody ExperienceCreateDTO request) {
        log.info("新增经历: {}", request);
        ResourceVersion<CvExperience> result = experienceService.create(request);
        URI location = URI.create("/api/v1/admin/cv/experiences/" + result.getValue().getExperienceId());
        return ResponseEntity.created(location)
                .header(HttpConstants.HEADER_X_REVISION, result.getRevision())
                .body(Result.ok(result.getValue()));
    }

    /**
     * 根据 ID 更新经历
     *
     * @param id 经历 ID
     * @param request 经历更新参数
     * @param revision 请求头携带的 If-Revision 版本
     * @return 更新后的经历详情及新 X-Revision 响应头
     */
    @PutMapping("/{id}")
    @SaCheckPermission("cv:experience:update")
    public ResponseEntity<Result<CvExperience>> update(@PathVariable String id,
            @Valid @RequestBody ExperienceWriteDTO request,
            @RequestHeader(value = HttpConstants.HEADER_IF_REVISION, required = false) String revision) {
        log.info("更新经历: id={}", id);
        ResourceVersion<CvExperience> result = experienceService.update(IdUtils.parse(id), request, revision);
        return versionResponse(result);
    }

    /**
     * 批量修改经历展示状态
     *
     * @param body 批量状态变更参数
     * @return 更新后的批量版本信息
     */
    @PutMapping("/batch-status")
    @SaCheckPermission("cv:experience:update")
    public Result<BatchVersionsVO> status(@Valid @RequestBody BatchBinaryStatusDTO body) {
        log.info("批量修改经历状态: count={}, status={}", body.getItems().size(), body.getStatus());
        BatchVersionsVO versions = experienceService.changeStatusBatch(body.getItems(), body.getStatus());
        return Result.ok(versions);
    }

    /**
     * 批量删除经历
     *
     * @param body 批量删除参数
     * @return 成功结果
     */
    @PostMapping("/batch-delete")
    @SaCheckPermission("cv:experience:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        log.info("批量删除经历: count={}", body.getItems().size());
        experienceService.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /**
     * 组装带版本头的经历详情响应。
     *
     * @param result 经历详情与版本
     * @return 200 响应
     */
    private ResponseEntity<Result<CvExperience>> versionResponse(ResourceVersion<CvExperience> result) {
        return ResponseEntity.ok()
                .header(HttpConstants.HEADER_X_REVISION, result.getRevision())
                .body(Result.ok(result.getValue()));
    }
}
