package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.constant.HttpConstants;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.TagQueryDTO;
import com.xuan.dto.TagWriteDTO;
import com.xuan.entity.BlogTag;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ITagService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.TagProfileVO;
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
 * 管理端文章标签接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/tags")
@RequiredArgsConstructor
public class TagController {

    private final ITagService tagService;

    /**
     * 分页查询标签列表
     *
     * @param query 查询参数
     * @return 标签分页结果
     */
    @GetMapping
    @SaCheckPermission("blog:tag:query")
    public Result<PageResult<BlogTag>> list(@Valid @ModelAttribute TagQueryDTO query) {
        log.info("分页查询标签列表: {}", query);
        PageResult<BlogTag> pageResult = tagService.listTags(query);
        return Result.ok(pageResult);
    }

    /**
     * 根据 ID 查询标签详情
     *
     * @param tagId 标签 ID
     * @return 标签详情及 X-Revision 响应头
     */
    @GetMapping("/{tagId}")
    @SaCheckPermission("blog:tag:query")
    public ResponseEntity<Result<BlogTag>> detail(@PathVariable String tagId) {
        log.info("根据 ID 查询标签详情: tagId={}", tagId);
        TagProfileVO profile = tagService.detail(IdUtils.parse(tagId));
        return profileResponse(profile);
    }

    /**
     * 创建标签
     *
     * @param body 标签创建参数
     * @return 201 响应及详情 Location
     */
    @PostMapping
    @SaCheckPermission("blog:tag:create")
    public ResponseEntity<Result<BlogTag>> create(@Valid @RequestBody TagWriteDTO body) {
        log.info("创建标签: {}", body);
        BlogTag tag = tagService.create(body);
        URI location = URI.create("/api/v1/admin/tags/" + tag.getTagId());
        return ResponseEntity.created(location).body(Result.ok(tag));
    }

    /**
     * 根据 ID 更新标签
     *
     * @param tagId 标签 ID
     * @param body 标签更新参数
     * @param revision 请求头携带的 If-Revision 版本
     * @return 更新后的标签详情及新 X-Revision 响应头
     */
    @PutMapping("/{tagId}")
    @SaCheckPermission("blog:tag:update")
    public ResponseEntity<Result<BlogTag>> update(@PathVariable String tagId,
            @Valid @RequestBody TagWriteDTO body,
            @RequestHeader(value = HttpConstants.HEADER_IF_REVISION, required = false) String revision) {
        log.info("更新标签: tagId={}", tagId);
        TagProfileVO profile = tagService.update(IdUtils.parse(tagId), body, revision);
        return profileResponse(profile);
    }

    /**
     * 批量删除标签
     *
     * @param body 批量删除参数
     * @return 成功结果
     */
    @PostMapping("/batch-delete")
    @SaCheckPermission("blog:tag:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        log.info("批量删除标签: count={}", body.getItems().size());
        tagService.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /**
     * 组装带版本头的标签详情响应。
     *
     * @param result 标签详情与版本
     * @return 200 响应
     */
    private ResponseEntity<Result<BlogTag>> profileResponse(TagProfileVO result) {
        return ResponseEntity.ok()
                .header(HttpConstants.HEADER_X_REVISION, result.getRevision())
                .body(Result.ok(result.getTag()));
    }
}
