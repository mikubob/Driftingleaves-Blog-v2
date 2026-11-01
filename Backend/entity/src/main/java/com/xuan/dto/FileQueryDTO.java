package com.xuan.dto;

import cn.hutool.core.util.ObjectUtil;
import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * 文件分页只筛展示名称、上传者和已确认 MIME，不接收存储路径。
 */
public record FileQueryDTO(@Min(1) @Max(100000) Integer page,
                           @Min(1) @Max(100) Integer pageSize,
                           @TextLength(min = 1, max = 100) String keyword,
                           @Pattern(regexp = "[1-9][0-9]{0,18}") String uploaderId,
                           @TextLength(min = 1, max = 100) String mimeType) {

    public FileQueryDTO {
        page = ObjectUtil.defaultIfNull(page, 1);
        pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
        keyword = keyword == null ? null : keyword.strip();
        mimeType = mimeType == null ? null : mimeType.strip();
    }
}
