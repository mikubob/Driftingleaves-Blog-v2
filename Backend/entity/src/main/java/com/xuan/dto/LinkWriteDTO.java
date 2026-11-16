package com.xuan.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

/**
 * 友链创建可用默认值，PUT 要完整填写；URL 的公开资源规则由入口业务统一检查。
 */
@Getter
public class LinkWriteDTO extends LinkApplicationDTO {

    /**
     * 完整替换也执行公共字段校验。
     */
    public interface Replace extends Default {

    }

    @NotNull
    private Integer sortOrder = 0;

    @Getter(AccessLevel.NONE)
    private final Set<String> present = new HashSet<>();

    /** 保存头像地址，记录字段是否提交。 */
    @Override
    public void setAvatar(String value) {
        present.add("avatar");
        super.setAvatar(value);
    }

    /** 保存友站简介，记录字段是否提交。 */
    @Override
    public void setDescription(String value) {
        present.add("description");
        super.setDescription(value);
    }

    public void setSortOrder(Integer value) {
        present.add("sortOrder");
        sortOrder = value;
    }

    /** 可空字段也得显式传入，防止漏传时把现有资料覆盖掉。 */
    @AssertTrue(groups = Replace.class, message = "表单内容未完整提交，请重新打开编辑窗口后重试。")
    public boolean isCompleteReplace() {
        return present.size() == 3;
    }

}
