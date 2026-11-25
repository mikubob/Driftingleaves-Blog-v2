package com.xuan.controller.blog;

import com.xuan.dto.DisplayQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IMusicService;
import com.xuan.vo.MusicPublicVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 博客端音乐接口
 */
@Slf4j
@RestController("blogMusicController")
@RequestMapping("/blog/music")
@RequiredArgsConstructor
public class MusicController {

    private final IMusicService musicService;

    /**
     * 分页查询公开音乐列表
     *
     * @param query 查询参数
     * @return 公开音乐分页结果
     */
    @GetMapping
    public Result<PageResult<MusicPublicVO>> list(@Valid @ModelAttribute DisplayQueryDTO query) {
        log.info("分页查询公开音乐列表: {}", query);
        PageResult<MusicPublicVO> pageResult = musicService.publicItems(query);
        return Result.ok(pageResult);
    }
}
