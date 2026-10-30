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
 * 公开分页只展示已通过友链，后台下架或删除后下次读取立即生效。
 */
@RestController("publicFriendLinkController")
@RequestMapping("/blog/links")
@RequiredArgsConstructor
public class FriendLinkController {

    private final IFriendLinkService links;

    /** 只接受标准分页，不允许客户端传 status 放宽公开条件。 */
    @GetMapping
    public Result<PageResult<LinkPublicVO>> list(@Valid @ModelAttribute PageQueryDTO query) {
        return Result.ok(links.publicLinks(query));
    }
}
