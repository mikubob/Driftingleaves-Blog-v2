package com.xuan.controller.blog;

import com.xuan.dto.DisplayQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IMusicService;
import com.xuan.vo.MusicPublicVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开音乐查询接口，只返回启用的资料。
 */
@RestController("blogMusicController")
@RequestMapping("/blog/music")
@RequiredArgsConstructor
public class MusicController {

    private final IMusicService items;

    /** 查询公开音乐列表，不返回管理字段或原始 Markdown。 */
    @GetMapping
    public Result<PageResult<MusicPublicVO>> list(@Valid @ModelAttribute DisplayQueryDTO query) {
        return Result.ok(items.publicItems(query));
    }
}
