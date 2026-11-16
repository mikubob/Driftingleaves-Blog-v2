package com.xuan.controller.blog;

import com.xuan.dto.LinkApplicationDTO;
import com.xuan.dto.PageQueryDTO;
import com.xuan.entity.BlogFriendLink;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IFriendLinkService;
import com.xuan.utils.IdUtils;
import com.xuan.vo.LinkPublicVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 查询公开友情链接，登录用户可提交、查看和删除本人的申请。
 */
@RestController("publicFriendLinkController")
@RequestMapping("/blog")
@RequiredArgsConstructor
public class FriendLinkController {

    private final IFriendLinkService links;

    /** 分页查询友情链接，只接受分页参数，不允许用 status 查询非公开记录。 */
    @GetMapping("/links")
    public Result<PageResult<LinkPublicVO>> list(@Valid @ModelAttribute PageQueryDTO query) {
        return Result.ok(links.publicLinks(query));
    }

    /** 查询当前账号的友链申请，没有申请时 data 为 null。 */
    @GetMapping("/me/link-application")
    public Result<BlogFriendLink> application() {
        return Result.ok(links.ownApplication());
    }

    /** 提交本人友链申请，成功返回 201 和申请查询地址。 */
    @PostMapping(path = "/me/link-application", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Result<BlogFriendLink>> apply(@Valid @RequestBody LinkApplicationDTO body) {
        BlogFriendLink application = links.submitApplication(body);
        return ResponseEntity.created(URI.create("/api/v1/blog/me/link-application")).body(Result.ok(application));
    }

    /** 根据id删除本人的友链申请，不要求 If-Revision。 */
    @DeleteMapping("/me/link-application/{linkId}")
    public Result<Void> deleteApplication(@PathVariable String linkId) {
        links.deleteOwnApplication(IdUtils.parse(linkId));
        return Result.ok(null);
    }
}
