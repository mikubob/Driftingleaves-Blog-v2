package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.dto.MusicWriteDTO;
import com.xuan.entity.BlogMusic;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IMusicService;
import com.xuan.utils.IdUtils;
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
 * 后台音乐管理接口，业务层在同一事务中检查权限和版本、保存资料。
 */
@RestController("adminMusicController")
@RequestMapping("/admin/music")
@RequiredArgsConstructor
public class MusicController {

    private final IMusicService items;

    /** 分页查询音乐，列表不带每条记录的版本，编辑前先查询详情。 */
    @GetMapping
    @SaCheckPermission("blog:music:query")
    public Result<PageResult<BlogMusic>> list(@Valid @ModelAttribute DisplayQueryDTO query) {
        return Result.ok(items.listItems(query));
    }

    /** 根据id查询音乐详情，版本放在 X-Revision 响应头。 */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:music:query")
    public ResponseEntity<Result<BlogMusic>> detail(@PathVariable String id) {
        return profile(items.detail(IdUtils.parse(id)));
    }

    /** 新增音乐，成功后返回 201 和新版本。 */
    @PostMapping
    @SaCheckPermission("blog:music:create")
    public ResponseEntity<Result<BlogMusic>> create(@Valid @RequestBody MusicWriteDTO request) {
        ResourceVersion<BlogMusic> result = items.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/music/" + result.value().getMusicId()))
                .header("X-Revision", result.revision()).body(Result.ok(result.value()));
    }

    /** 根据id更新音乐，PUT 要传完整资料，加锁后检查版本。 */
    @PutMapping("/{id}")
    @SaCheckPermission("blog:music:update")
    public ResponseEntity<Result<BlogMusic>> update(@PathVariable String id, @Valid @RequestBody MusicWriteDTO request,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(items.update(IdUtils.parse(id), request, revision));
    }

    /** 批量删除音乐，单条也走此接口，业务层统一检查权限、版本并处理失败。 */
    @PostMapping("/batch-delete")
    @SaCheckPermission("blog:music:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        items.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /** 返回音乐资料，版本放在 X-Revision 响应头。 */
    private ResponseEntity<Result<BlogMusic>> profile(ResourceVersion<BlogMusic> result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.value()));
    }
}
