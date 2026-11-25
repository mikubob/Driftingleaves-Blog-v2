package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.constant.HttpConstants;
import com.xuan.dto.BatchBinaryStatusDTO;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.dto.SkillCreateDTO;
import com.xuan.dto.SkillWriteDTO;
import com.xuan.entity.CvSkill;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ISkillService;
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
 * 管理端简历技能接口
 */
@Slf4j
@RestController("adminSkillController")
@RequestMapping("/admin/cv/skills")
@RequiredArgsConstructor
public class SkillController {

    private final ISkillService skillService;

    /**
     * 分页查询技能列表
     *
     * @param query 查询参数
     * @return 技能分页结果
     */
    @GetMapping
    @SaCheckPermission("cv:skill:query")
    public Result<PageResult<CvSkill>> list(@Valid @ModelAttribute DisplayQueryDTO query) {
        log.info("分页查询技能列表: {}", query);
        PageResult<CvSkill> pageResult = skillService.listItems(query);
        return Result.ok(pageResult);
    }

    /**
     * 根据 ID 查询技能详情
     *
     * @param id 技能 ID
     * @return 技能详情及 X-Revision 响应头
     */
    @GetMapping("/{id}")
    @SaCheckPermission("cv:skill:query")
    public ResponseEntity<Result<CvSkill>> detail(@PathVariable String id) {
        log.info("根据 ID 查询技能详情: id={}", id);
        ResourceVersion<CvSkill> result = skillService.detail(IdUtils.parse(id));
        return versionResponse(result);
    }

    /**
     * 新增技能
     *
     * @param request 技能创建参数
     * @return 201 响应、Location 及 X-Revision 响应头
     */
    @PostMapping
    @SaCheckPermission("cv:skill:create")
    public ResponseEntity<Result<CvSkill>> create(@Valid @RequestBody SkillCreateDTO request) {
        log.info("新增技能: {}", request);
        ResourceVersion<CvSkill> result = skillService.create(request);
        URI location = URI.create("/api/v1/admin/cv/skills/" + result.getValue().getSkillId());
        return ResponseEntity.created(location)
                .header(HttpConstants.HEADER_X_REVISION, result.getRevision())
                .body(Result.ok(result.getValue()));
    }

    /**
     * 根据 ID 更新技能
     *
     * @param id 技能 ID
     * @param request 技能更新参数
     * @param revision 请求头携带的 If-Revision 版本
     * @return 更新后的技能详情及新 X-Revision 响应头
     */
    @PutMapping("/{id}")
    @SaCheckPermission("cv:skill:update")
    public ResponseEntity<Result<CvSkill>> update(@PathVariable String id,
            @Valid @RequestBody SkillWriteDTO request,
            @RequestHeader(value = HttpConstants.HEADER_IF_REVISION, required = false) String revision) {
        log.info("更新技能: id={}", id);
        ResourceVersion<CvSkill> result = skillService.update(IdUtils.parse(id), request, revision);
        return versionResponse(result);
    }

    /**
     * 批量修改技能展示状态
     *
     * @param body 批量状态变更参数
     * @return 更新后的批量版本信息
     */
    @PutMapping("/batch-status")
    @SaCheckPermission("cv:skill:update")
    public Result<BatchVersionsVO> status(@Valid @RequestBody BatchBinaryStatusDTO body) {
        log.info("批量修改技能状态: count={}, status={}", body.getItems().size(), body.getStatus());
        BatchVersionsVO versions = skillService.changeStatusBatch(body.getItems(), body.getStatus());
        return Result.ok(versions);
    }

    /**
     * 批量删除技能
     *
     * @param body 批量删除参数
     * @return 成功结果
     */
    @PostMapping("/batch-delete")
    @SaCheckPermission("cv:skill:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        log.info("批量删除技能: count={}", body.getItems().size());
        skillService.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /**
     * 组装带版本头的技能详情响应。
     *
     * @param result 技能详情与版本
     * @return 200 响应
     */
    private ResponseEntity<Result<CvSkill>> versionResponse(ResourceVersion<CvSkill> result) {
        return ResponseEntity.ok()
                .header(HttpConstants.HEADER_X_REVISION, result.getRevision())
                .body(Result.ok(result.getValue()));
    }
}
