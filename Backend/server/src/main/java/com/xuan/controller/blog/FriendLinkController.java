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
import lombok.extern.slf4j.Slf4j;
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
 * 博客端友情链接与申请接口
 */
@Slf4j
@RestController("publicFriendLinkController")
@RequestMapping("/blog")
@RequiredArgsConstructor
public class FriendLinkController {

    private final IFriendLinkService friendLinkService;

    /**
     * 分页查询公开友情链接列表
     *
     * @param query 分页参数
     * @return 友链分页结果
     */
    @GetMapping("/links")
    public Result<PageResult<LinkPublicVO>> list(@Valid @ModelAttribute PageQueryDTO query) {
        log.info("分页查询公开友情链接列表: {}", query);
        PageResult<LinkPublicVO> pageResult = friendLinkService.publicLinks(query);
        return Result.ok(pageResult);
    }

    /**
     * 查询当前用户的友链申请
     *
     * @return 友链申请记录，无申请时返回 null
     */
    @GetMapping("/me/link-application")
    public Result<BlogFriendLink> application() {
        log.info("查询本人友链申请");
        BlogFriendLink application = friendLinkService.ownApplication();
        return Result.ok(application);
    }

    /**
     * 提交本人友链申请
     *
     * @param body 友链申请参数
     * @return 201 响应及申请记录
     */
    @PostMapping(path = "/me/link-application", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Result<BlogFriendLink>> apply(@Valid @RequestBody LinkApplicationDTO body) {
        log.info("提交本人友链申请: {}", body);
        BlogFriendLink application = friendLinkService.submitApplication(body);
        URI location = URI.create("/api/v1/blog/me/link-application");
        return ResponseEntity.created(location).body(Result.ok(application));
    }

    /**
     * 删除本人的友链申请
     *
     * @param linkId 友链申请 ID
     * @return 成功结果
     */
    @DeleteMapping("/me/link-application/{linkId}")
    public Result<Void> deleteApplication(@PathVariable String linkId) {
        log.info("删除本人友链申请: linkId={}", linkId);
        friendLinkService.deleteOwnApplication(IdUtils.parse(linkId));
        return Result.ok(null);
    }
}
