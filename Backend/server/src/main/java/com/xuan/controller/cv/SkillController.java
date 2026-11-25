package com.xuan.controller.cv;

import com.xuan.dto.DisplayQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ISkillService;
import com.xuan.vo.SkillPublicVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 简历端公开技能接口
 */
@Slf4j
@RestController("cvSkillController")
@RequestMapping("/cv/skills")
@RequiredArgsConstructor
public class SkillController {

    private final ISkillService skillService;

    /**
     * 分页查询公开技能列表
     *
     * @param query 查询参数
     * @return 技能分页结果
     */
    @GetMapping
    public Result<PageResult<SkillPublicVO>> list(@Valid @ModelAttribute DisplayQueryDTO query) {
        log.info("简历端分页查询公开技能列表: {}", query);
        PageResult<SkillPublicVO> pageResult = skillService.publicItems(query);
        return Result.ok(pageResult);
    }
}
