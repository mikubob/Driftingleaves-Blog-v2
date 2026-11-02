package com.xuan.controller.blog;

import com.xuan.dto.PageQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IFriendLinkService;
import com.xuan.vo.LinkPublicVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开友情链接接口，只展示审核通过的链接，下架或删除后下次读取生效。
 */
@RestController("publicFriendLinkController")
@RequestMapping("/blog/links")
@RequiredArgsConstructor
public class FriendLinkController {

    private final IFriendLinkService links;

    /** 分页查询友情链接，只接受分页参数，不允许用 status 查询非公开记录。 */
    @GetMapping
    public Result<PageResult<LinkPublicVO>> list(@Valid @ModelAttribute PageQueryDTO query) {
        return Result.ok(links.publicLinks(query));
    }
}
