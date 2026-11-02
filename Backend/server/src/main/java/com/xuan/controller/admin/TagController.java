package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
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
 * 后台标签管理接口，检查后台入口和操作权限，由业务事务处理。
 */
@RestController
@RequestMapping("/admin/tags")
@RequiredArgsConstructor
public class TagController {

    private final ITagService tags;

    /** 分页查询标签，列表不带每条记录的版本，编辑前先查询详情。 */
    @GetMapping
    @SaCheckPermission("blog:tag:query")
    public Result<PageResult<BlogTag>> list(@Valid @ModelAttribute TagQueryDTO query) {
        return Result.ok(tags.listTags(query));
    }

    /** 根据id查询标签详情，版本放在 X-Revision 响应头。 */
    @GetMapping("/{tagId}")
    @SaCheckPermission("blog:tag:query")
    public ResponseEntity<Result<BlogTag>> detail(@PathVariable String tagId) {
        return profile(tags.detail(IdUtils.parse(tagId)));
    }

    /** 创建标签，成功返回 201 和新记录的详情地址。 */
    @PostMapping
    @SaCheckPermission("blog:tag:create")
    public ResponseEntity<Result<BlogTag>> create(@Valid @RequestBody TagWriteDTO body) {
        BlogTag value = tags.create(body);
        return ResponseEntity.created(URI.create("/api/v1/admin/tags/" + value.getTagId()))
                .body(Result.ok(value));
    }

    /** 根据id更新标签，完整替换资料，加锁后检查版本，未传版本时返回 428。 */
    @PutMapping("/{tagId}")
    @SaCheckPermission("blog:tag:update")
    public ResponseEntity<Result<BlogTag>> update(@PathVariable String tagId,
            @Valid @RequestBody TagWriteDTO body,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(tags.update(IdUtils.parse(tagId), body, revision));
    }

    /** 批量删除标签，单条也走此接口，业务层统一检查权限、版本并处理失败。 */
    @PostMapping("/batch-delete")
    @SaCheckPermission("blog:tag:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        tags.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /** 返回标签资料，版本只放在响应头。 */
    private ResponseEntity<Result<BlogTag>> profile(TagProfileVO result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.tag()));
    }
}
