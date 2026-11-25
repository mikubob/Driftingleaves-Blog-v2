package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.constant.HttpConstants;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.LinkBatchStatusDTO;
import com.xuan.dto.LinkCreateDTO;
import com.xuan.dto.LinkQueryDTO;
import com.xuan.dto.LinkWriteDTO;
import com.xuan.entity.BlogFriendLink;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IFriendLinkService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.AdminLinkVO;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.LinkProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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
 * 管理端友情链接接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/links")
@RequiredArgsConstructor
public class FriendLinkController {

    private final IFriendLinkService friendLinkService;

    /**
     * 分页查询友情链接列表
     *
     * @param query 查询参数
     * @return 友链分页结果
     */
    @GetMapping
    @SaCheckPermission("blog:link:query")
    public Result<PageResult<AdminLinkVO>> list(@Valid @ModelAttribute LinkQueryDTO query) {
        log.info("分页查询友情链接列表: {}", query);
        PageResult<AdminLinkVO> pageResult = friendLinkService.listLinks(query);
        return Result.ok(pageResult);
    }

    /**
     * 根据 ID 查询友情链接详情
     *
     * @param linkId 友链 ID
     * @return 友链详情及 X-Revision 响应头
     */
    @GetMapping("/{linkId}")
    @SaCheckPermission("blog:link:query")
    public ResponseEntity<Result<AdminLinkVO>> detail(@PathVariable String linkId) {
        log.info("根据 ID 查询友情链接详情: linkId={}", linkId);
        LinkProfileVO<AdminLinkVO> profile = friendLinkService.detail(IdUtils.parse(linkId));
        return profileResponse(profile);
    }

    /**
     * 创建友情链接
     *
     * @param body 友链创建参数
     * @return 201 响应及详情 Location
     */
    @PostMapping
    @SaCheckPermission("blog:link:create")
    public ResponseEntity<Result<BlogFriendLink>> create(@Valid @RequestBody LinkCreateDTO body) {
        log.info("创建友情链接: {}", body);
        BlogFriendLink link = friendLinkService.create(body);
        URI location = URI.create("/api/v1/admin/links/" + link.getLinkId());
        return ResponseEntity.created(location).body(Result.ok(link));
    }

    /**
     * 根据 ID 更新友情链接
     *
     * @param linkId 友链 ID
     * @param body 友链更新参数
     * @param revision 请求头携带的 If-Revision 版本
     * @return 更新后的友链详情及新 X-Revision 响应头
     */
    @PutMapping("/{linkId}")
    @SaCheckPermission("blog:link:update")
    public ResponseEntity<Result<BlogFriendLink>> update(@PathVariable String linkId,
            @Validated(LinkWriteDTO.Replace.class) @RequestBody LinkWriteDTO body,
            @RequestHeader(value = HttpConstants.HEADER_IF_REVISION, required = false) String revision) {
        log.info("更新友情链接: linkId={}", linkId);
        LinkProfileVO<BlogFriendLink> profile = friendLinkService.update(IdUtils.parse(linkId), body, revision);
        return profileResponse(profile);
    }

    /**
     * 批量修改友情链接状态
     *
     * @param body 批量状态变更参数
     * @return 更新后的批量版本信息
     */
    @PutMapping("/batch-status")
    @SaCheckPermission("blog:link:update")
    public Result<BatchVersionsVO> status(@Valid @RequestBody LinkBatchStatusDTO body) {
        log.info("批量修改友情链接状态: count={}, status={}", body.getItems().size(), body.getStatus());
        BatchVersionsVO versions = friendLinkService.changeStatusBatch(body.getItems(), body.getStatus());
        return Result.ok(versions);
    }

    /**
     * 批量删除友情链接
     *
     * @param body 批量删除参数
     * @return 成功结果
     */
    @PostMapping("/batch-delete")
    @SaCheckPermission("blog:link:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        log.info("批量删除友情链接: count={}", body.getItems().size());
        friendLinkService.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /**
     * 组装带版本头的友情链接详情响应。
     *
     * @param result 友链详情与版本
     * @return 200 响应
     */
    private <T extends BlogFriendLink> ResponseEntity<Result<T>> profileResponse(LinkProfileVO<T> result) {
        return ResponseEntity.ok()
                .header(HttpConstants.HEADER_X_REVISION, result.revision())
                .body(Result.ok(result.link()));
    }
}
