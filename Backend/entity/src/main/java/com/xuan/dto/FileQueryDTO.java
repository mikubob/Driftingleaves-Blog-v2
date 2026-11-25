package com.xuan.dto;

import cn.hutool.core.util.ObjectUtil;
import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 按原始文件名或存储文件名、上传者和 MIME 筛选文件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 页码
    @Builder.Default
    @Min(1)
    @Max(100000)
    private Integer page = 1;

    // 每页条数
    @Builder.Default
    @Min(1)
    @Max(100)
    private Integer pageSize = 10;

    // 搜索关键词
    @TextLength(min = 1, max = 255)
    private String keyword;

    // 上传者ID
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "所选记录无效，请刷新页面后重新选择")
    private String uploaderId;

    // MIME类型
    @TextLength(min = 1, max = 100)
    private String mimeType;

    public void setPage(Integer page) {
        this.page = ObjectUtil.defaultIfNull(page, 1);
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = ObjectUtil.defaultIfNull(pageSize, 10);
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword == null ? null : keyword.strip();
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType == null ? null : mimeType.strip();
    }
}
