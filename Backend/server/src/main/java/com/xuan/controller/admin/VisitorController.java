package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.constant.HttpConstants;
import com.xuan.dto.BatchBlockDTO;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.VisitorQueryDTO;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IVisitorService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.VisitorVO;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端访客接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/visitors")
@RequiredArgsConstructor
public class VisitorController {

    private final IVisitorService visitorService;

    /**
     * 分页查询访客列表
     *
     * @param query 查询参数
     * @return 访客分页结果
     */
    @GetMapping
    @SaCheckPermission("blog:visitor:query")
    public Result<PageResult<VisitorVO>> list(@Valid @ModelAttribute VisitorQueryDTO query) {
        log.info("分页查询访客列表: {}", query);
        PageResult<VisitorVO> pageResult = visitorService.listVisitors(query);
        return Result.ok(pageResult);
    }

    /**
     * 根据 ID 查询访客详情
     *
     * @param id 访客 ID
     * @return 访客详情及 X-Revision 响应头
     */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:visitor:query")
    public ResponseEntity<Result<VisitorVO>> detail(@PathVariable String id) {
        log.info("根据 ID 查询访客详情: id={}", id);
        ResourceVersion<VisitorVO> result = visitorService.detail(IdUtils.parse(id));
        return versionResponse(result);
    }

    /**
     * 批量封禁访客 IP
     *
     * @param body 批量封禁参数
     * @return 更新后的批量版本信息
     */
    @PutMapping("/batch-block")
    @SaCheckPermission("blog:visitor:block")
    public Result<BatchVersionsVO> block(@Valid @RequestBody BatchBlockDTO body) {
        log.info("批量封禁访客: count={}", body.getItems().size());
        BatchVersionsVO versions = visitorService.blockBatch(body.getItems(), body.getBlockedUntil());
        return Result.ok(versions);
    }

    /**
     * 批量解除访客限制
     *
     * @param body 批量操作参数
     * @return 更新后的批量版本信息
     */
    @PutMapping("/batch-unblock")
    @SaCheckPermission("blog:visitor:unblock")
    public Result<BatchVersionsVO> unblock(@Valid @RequestBody BatchRevisionDTO body) {
        log.info("批量解封访客: count={}", body.getItems().size());
        BatchVersionsVO versions = visitorService.unblockBatch(body.getItems());
        return Result.ok(versions);
    }

    /**
     * 批量删除访客
     *
     * @param body 批量删除参数
     * @return 成功结果
     */
    @PostMapping("/batch-delete")
    @SaCheckPermission("blog:visitor:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        log.info("批量删除访客: count={}", body.getItems().size());
        visitorService.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /**
     * 组装带版本头的访客详情响应。
     *
     * @param result 访客详情与版本
     * @return 200 响应
     */
    private ResponseEntity<Result<VisitorVO>> versionResponse(ResourceVersion<VisitorVO> result) {
        return ResponseEntity.ok()
                .header(HttpConstants.HEADER_X_REVISION, result.getRevision())
                .body(Result.ok(result.getValue()));
    }
}
