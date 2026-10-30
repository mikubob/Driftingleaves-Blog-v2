package com.xuan.controller.blog;

import com.xuan.dto.DisplayQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ISkillService;
import com.xuan.vo.SkillPublicVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开展示只返回启用资料。
 */
@RestController("blogSkillController")
@RequestMapping("/cv/skills")
@RequiredArgsConstructor
public class SkillController {

    private final ISkillService items;

    /** 不输出管理字段或原始Markdown。 */
    @GetMapping
    public Result<PageResult<SkillPublicVO>> list(@Valid @ModelAttribute DisplayQueryDTO query) {
        return Result.ok(items.publicItems(query));
    }
}
