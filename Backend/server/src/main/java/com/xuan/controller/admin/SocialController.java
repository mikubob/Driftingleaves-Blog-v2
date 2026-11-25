package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.constant.HttpConstants;
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
 * 管理端社交链接接口
 */
@Slf4j
@RestController("adminSocialController")
@RequestMapping("/admin/home/social-links")
@RequiredArgsConstructor
public class SocialController {

    private final ISocialService socialService;

    /**
     * 分页查询社交链接列表
     *
     * @param query 查询参数
     * @return 社交链接分页结果
     */
    @GetMapping
    @SaCheckPermission("home:social:query")
    public Result<PageResult<HomeSocialLink>> list(@Valid @ModelAttribute DisplayQueryDTO query) {
        log.info("分页查询社交链接列表: {}", query);
        PageResult<HomeSocialLink> pageResult = socialService.listItems(query);
        return Result.ok(pageResult);
    }

    /**
     * 根据 ID 查询社交链接详情
     *
     * @param id 社交链接 ID
     * @return 社交链接详情及 X-Revision 响应头
     */
    @GetMapping("/{id}")
    @SaCheckPermission("home:social:query")
    public ResponseEntity<Result<HomeSocialLink>> detail(@PathVariable String id) {
        log.info("根据 ID 查询社交链接详情: id={}", id);
        ResourceVersion<HomeSocialLink> result = socialService.detail(IdUtils.parse(id));
        return versionResponse(result);
    }

    /**
     * 新增社交链接
     *
     * @param request 社交链接创建参数
     * @return 201 响应、Location 及 X-Revision 响应头
     */
    @PostMapping
    @SaCheckPermission("home:social:create")
    public ResponseEntity<Result<HomeSocialLink>> create(@Valid @RequestBody SocialCreateDTO request) {
        log.info("新增社交链接: {}", request);
        ResourceVersion<HomeSocialLink> result = socialService.create(request);
        URI location = URI.create("/api/v1/admin/home/social-links/" + result.getValue().getSocialId());
        return ResponseEntity.created(location)
                .header(HttpConstants.HEADER_X_REVISION, result.getRevision())
                .body(Result.ok(result.getValue()));
    }

    /**
     * 根据 ID 更新社交链接
     *
     * @param id 社交链接 ID
     * @param request 社交链接更新参数
     * @param revision 请求头携带的 If-Revision 版本
     * @return 更新后的社交链接详情及新 X-Revision 响应头
     */
    @PutMapping("/{id}")
    @SaCheckPermission("home:social:update")
    public ResponseEntity<Result<HomeSocialLink>> update(@PathVariable String id,
            @Valid @RequestBody SocialWriteDTO request,
            @RequestHeader(value = HttpConstants.HEADER_IF_REVISION, required = false) String revision) {
        log.info("更新社交链接: id={}", id);
        ResourceVersion<HomeSocialLink> result = socialService.update(IdUtils.parse(id), request, revision);
        return versionResponse(result);
    }

    /**
     * 批量修改社交链接展示状态
     *
     * @param body 批量状态变更参数
     * @return 更新后的批量版本信息
     */
    @PutMapping("/batch-status")
    @SaCheckPermission("home:social:update")
    public Result<BatchVersionsVO> status(@Valid @RequestBody BatchBinaryStatusDTO body) {
        log.info("批量修改社交链接状态: count={}, status={}", body.getItems().size(), body.getStatus());
        BatchVersionsVO versions = socialService.changeStatusBatch(body.getItems(), body.getStatus());
        return Result.ok(versions);
    }

    /**
     * 批量删除社交链接
     *
     * @param body 批量删除参数
     * @return 成功结果
     */
    @PostMapping("/batch-delete")
    @SaCheckPermission("home:social:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        log.info("批量删除社交链接: count={}", body.getItems().size());
        socialService.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /**
     * 组装带版本头的社交链接详情响应。
     *
     * @param result 社交链接详情与版本
     * @return 200 响应
     */
    private ResponseEntity<Result<HomeSocialLink>> versionResponse(ResourceVersion<HomeSocialLink> result) {
        return ResponseEntity.ok()
                .header(HttpConstants.HEADER_X_REVISION, result.getRevision())
                .body(Result.ok(result.getValue()));
    }
}
