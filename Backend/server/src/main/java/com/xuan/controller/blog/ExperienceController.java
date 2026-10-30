package com.xuan.controller.blog;

import com.xuan.service.IExperienceService;
import com.xuan.vo.ExperiencePublicVO;
import com.xuan.result.Result;
import com.xuan.result.PageResult;
import com.xuan.dto.ExperienceQueryDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.ModelAttribute;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/** 公开展示只返回启用资料。 */
@RestController("blogExperienceController")
@RequestMapping("/cv/experiences")
@RequiredArgsConstructor
public class ExperienceController {
    private final IExperienceService items;
    /** 不输出管理字段或原始Markdown。 */
    @GetMapping
    public Result<PageResult<ExperiencePublicVO>> list(@Valid @ModelAttribute ExperienceQueryDTO query) { return Result.ok(items.publicItems(query)); }
}
