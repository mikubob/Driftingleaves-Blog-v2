package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.TagWriteDTO;
import com.xuan.dto.TagQueryDTO;
import com.xuan.entity.BlogTag;
import com.xuan.exception.BusinessException;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ITagService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.TagProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

/** 后台标签接口，先过后台入口和业务权限检查，再交给业务事务处理。 */
@RestController
@RequestMapping("/admin/tags")
@RequiredArgsConstructor
public class TagController {
    private final ITagService tags;

    /** 获取标签分页，列表不提供每条记录的版本，编辑前要先取详情。 */
    @GetMapping
    @SaCheckPermission("blog:tag:query")
    public Result<PageResult<BlogTag>> list(@Valid @ModelAttribute TagQueryDTO query) {
        return Result.ok(tags.listTags(query));
    }

    /** 获取标签详情，把当前版本放进 X-Revision 响应头。 */
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

    /** 完整替换标签资料，版本检查留到锁事务里，缺版本返回 428。 */
    @PutMapping("/{tagId}")
    @SaCheckPermission("blog:tag:update")
    public ResponseEntity<Result<BlogTag>> update(@PathVariable String tagId,
            @Valid @RequestBody TagWriteDTO body,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(tags.update(IdUtils.parse(tagId), body, revision));
    }

    /** 删除标签前先检查版本，关联处理和成功日志都要一起提交。 */
    @DeleteMapping("/{tagId}")
    @SaCheckPermission("blog:tag:delete")
    public Result<Void> delete(@PathVariable String tagId,
            @RequestHeader(value = "If-Revision", required = false) String revision,
            @RequestBody(required = false) String body) {
        if (body != null) {
            throw new BusinessException(400, "VALIDATION_ERROR", "本接口不接受请求体");
        }
        tags.delete(IdUtils.parse(tagId), revision);
        return Result.ok(null);
    }

    /** 响应体只放标签资料，版本单独放响应头。 */
    private ResponseEntity<Result<BlogTag>> profile(TagProfileVO result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.tag()));
    }
}
