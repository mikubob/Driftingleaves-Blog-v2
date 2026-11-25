package com.xuan.controller.cv;

import com.xuan.dto.ExperienceQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IExperienceService;
import com.xuan.vo.ExperiencePublicVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 简历端公开经历接口
 */
@Slf4j
@RestController("cvExperienceController")
@RequestMapping("/cv/experiences")
@RequiredArgsConstructor
public class ExperienceController {

    private final IExperienceService experienceService;

    /**
     * 分页查询公开经历列表
     *
     * @param query 查询参数
     * @return 经历分页结果
     */
    @GetMapping
    public Result<PageResult<ExperiencePublicVO>> list(@Valid @ModelAttribute ExperienceQueryDTO query) {
        log.info("简历端分页查询公开经历列表: {}", query);
        PageResult<ExperiencePublicVO> pageResult = experienceService.publicItems(query);
        return Result.ok(pageResult);
    }
}
