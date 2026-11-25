package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.constant.HttpConstants;
import com.xuan.dto.BatchBinaryStatusDTO;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.dto.MusicCreateDTO;
import com.xuan.dto.MusicWriteDTO;
import com.xuan.entity.BlogMusic;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IMusicService;
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
 * 管理端音乐接口
 */
@Slf4j
@RestController("adminMusicController")
@RequestMapping("/admin/music")
@RequiredArgsConstructor
public class MusicController {

    private final IMusicService musicService;

    /**
     * 分页查询音乐列表
     *
     * @param query 查询参数
     * @return 音乐分页结果
     */
    @GetMapping
    @SaCheckPermission("blog:music:query")
    public Result<PageResult<BlogMusic>> list(@Valid @ModelAttribute DisplayQueryDTO query) {
        log.info("分页查询音乐列表: {}", query);
        PageResult<BlogMusic> pageResult = musicService.listItems(query);
        return Result.ok(pageResult);
    }

    /**
     * 根据 ID 查询音乐详情
     *
     * @param id 音乐 ID
     * @return 音乐详情及 X-Revision 响应头
     */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:music:query")
    public ResponseEntity<Result<BlogMusic>> detail(@PathVariable String id) {
        log.info("根据 ID 查询音乐详情: id={}", id);
        ResourceVersion<BlogMusic> result = musicService.detail(IdUtils.parse(id));
        return versionResponse(result);
    }

    /**
     * 新增音乐
     *
     * @param request 音乐创建参数
     * @return 201 响应、Location 及 X-Revision 响应头
     */
    @PostMapping
    @SaCheckPermission("blog:music:create")
    public ResponseEntity<Result<BlogMusic>> create(@Valid @RequestBody MusicCreateDTO request) {
        log.info("新增音乐: {}", request);
        ResourceVersion<BlogMusic> result = musicService.create(request);
        URI location = URI.create("/api/v1/admin/music/" + result.value().getMusicId());
        return ResponseEntity.created(location)
                .header(HttpConstants.HEADER_X_REVISION, result.revision())
                .body(Result.ok(result.value()));
    }

    /**
     * 根据 ID 更新音乐
     *
     * @param id 音乐 ID
     * @param request 音乐更新参数
     * @param revision 请求头携带的 If-Revision 版本
     * @return 更新后的音乐详情及新 X-Revision 响应头
     */
    @PutMapping("/{id}")
    @SaCheckPermission("blog:music:update")
    public ResponseEntity<Result<BlogMusic>> update(@PathVariable String id,
            @Valid @RequestBody MusicWriteDTO request,
            @RequestHeader(value = HttpConstants.HEADER_IF_REVISION, required = false) String revision) {
        log.info("更新音乐: id={}", id);
        ResourceVersion<BlogMusic> result = musicService.update(IdUtils.parse(id), request, revision);
        return versionResponse(result);
    }

    /**
     * 批量修改音乐展示状态
     *
     * @param body 批量状态变更参数
     * @return 更新后的批量版本信息
     */
    @PutMapping("/batch-status")
    @SaCheckPermission("blog:music:update")
    public Result<BatchVersionsVO> status(@Valid @RequestBody BatchBinaryStatusDTO body) {
        log.info("批量修改音乐状态: count={}, status={}", body.getItems().size(), body.getStatus());
        BatchVersionsVO versions = musicService.changeStatusBatch(body.getItems(), body.getStatus());
        return Result.ok(versions);
    }

    /**
     * 批量删除音乐
     *
     * @param body 批量删除参数
     * @return 成功结果
     */
    @PostMapping("/batch-delete")
    @SaCheckPermission("blog:music:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        log.info("批量删除音乐: count={}", body.getItems().size());
        musicService.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /**
     * 组装带版本头的音乐详情响应。
     *
     * @param result 音乐详情与版本
     * @return 200 响应
     */
    private ResponseEntity<Result<BlogMusic>> versionResponse(ResourceVersion<BlogMusic> result) {
        return ResponseEntity.ok()
                .header(HttpConstants.HEADER_X_REVISION, result.revision())
                .body(Result.ok(result.value()));
    }
}
