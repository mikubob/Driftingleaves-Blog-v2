package com.xuan.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xuan.dto.LinkQueryDTO;
import com.xuan.dto.LinkWriteDTO;
import com.xuan.entity.BlogFriendLink;
import com.xuan.exception.BusinessException;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IFriendLinkService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.LinkProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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

/**
 * 后台友链接口，先过后台入口和业务权限检查，再交给业务事务处理。
 */
@RestController
@RequestMapping("/admin/links")
@RequiredArgsConstructor
public class FriendLinkController {

    private final IFriendLinkService links;

    /** 获取友链分页，列表不提供每条记录的版本，编辑前要先取详情。 */
    @GetMapping
    @SaCheckPermission("blog:link:query")
    public Result<PageResult<BlogFriendLink>> list(@Valid @ModelAttribute LinkQueryDTO query) {
        return Result.ok(links.listLinks(query));
    }

    /** 获取友链详情，把当前版本放进 X-Revision 响应头。 */
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

    /** 完整替换友链资料，版本检查留到锁事务里，缺版本返回 428。 */
    @PutMapping("/{linkId}")
    @SaCheckPermission("blog:link:update")
    public ResponseEntity<Result<BlogFriendLink>> update(@PathVariable String linkId,
            @Validated(LinkWriteDTO.Replace.class) @RequestBody LinkWriteDTO body,
            @RequestHeader(value = "If-Revision", required = false) String revision) {
        return profile(links.update(IdUtils.parse(linkId), body, revision));
    }

    /** 删除友链前先检查版本，删除和成功日志都要一起提交。 */
    @DeleteMapping("/{linkId}")
    @SaCheckPermission("blog:link:delete")
    public Result<Void> delete(@PathVariable String linkId,
            @RequestHeader(value = "If-Revision", required = false) String revision,
            @RequestBody(required = false) String body) {
        if (body != null) {
            throw new BusinessException(400, "VALIDATION_ERROR", "本接口不接受请求体");
        }
        links.delete(IdUtils.parse(linkId), revision);
        return Result.ok(null);
    }

    /** 响应体只放友链资料，版本单独放响应头。 */
    private ResponseEntity<Result<BlogFriendLink>> profile(LinkProfileVO result) {
        return ResponseEntity.ok().header("X-Revision", result.revision()).body(Result.ok(result.link()));
    }
}
