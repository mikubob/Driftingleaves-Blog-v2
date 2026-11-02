package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.BatchRevisionDTO;
import com.xuan.dto.LinkQueryDTO;
import com.xuan.dto.LinkWriteDTO;
import com.xuan.entity.BlogFriendLink;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IFriendLinkService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.LinkProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 后台友情链接管理接口，检查后台入口和操作权限，由业务事务处理。
 */
@RestController
@RequestMapping("/admin/links")
@RequiredArgsConstructor
public class FriendLinkController {

    private final IFriendLinkService links;

    /** 分页查询友情链接，列表不带每条记录的版本，编辑前先查询详情。 */
    @GetMapping
    @SaCheckPermission("blog:link:query")
    public Result<PageResult<BlogFriendLink>> list(@Valid @ModelAttribute LinkQueryDTO query) {
        return Result.ok(links.listLinks(query));
    }

    /** 根据id查询友情链接详情，版本放在 X-Revision 响应头。 */
    @GetMapping("/{linkId}")
    @SaCheckPermission("blog:link:query")
    public ResponseEntity<Result<BlogFriendLink>> detail(@PathVariable String linkId) {
        return profile(links.detail(IdUtils.parse(linkId)));
    }

    /** 创建友链，成功返回 201 和新记录的详情地址。 */
    @PostMapping
    @SaCheckPermission("blog:link:create")
    public ResponseEntity<Result<BlogFriendLink>> create(@Valid @RequestBody LinkWriteDTO body) {
        BlogFriendLink value = links.create(body);
        return ResponseEntity.created(URI.create("/api/v1/admin/links/" + value.getLinkId()))
                .body(Result.ok(value));
    }

    /** 根据id更新友情链接，完整替换资料，加锁后检查版本，未传版本时返回 428。 */
    @PutMapping("/{linkId}")
    @SaCheckPermission("blog:link:update")
    public ResponseEntity<Result<BlogFriendLink>> update(@PathVariable String linkId,
            @Validated(LinkWriteDTO.Replace.class) @RequestBody LinkWriteDTO body,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(links.update(IdUtils.parse(linkId), body, revision));
    }

    /** 批量删除友情链接，单条也走此接口，业务层统一检查权限、版本并处理失败。 */
    @PostMapping("/batch-delete")
    @SaCheckPermission("blog:link:delete")
    public Result<Void> delete(@Valid @RequestBody BatchRevisionDTO body) {
        links.deleteBatch(body.getItems());
        return Result.ok(null);
    }

    /** 返回友情链接资料，版本只放在响应头。 */
    private ResponseEntity<Result<BlogFriendLink>> profile(LinkProfileVO result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.link()));
    }
}
