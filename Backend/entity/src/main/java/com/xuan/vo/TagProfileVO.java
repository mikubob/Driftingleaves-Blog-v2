package com.xuan.vo;

import com.xuan.entity.BlogTag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 标签资料放响应体，版本单独放到 X-Revision 响应头。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagProfileVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 标签信息
    private BlogTag tag;

    // 资源版本号
    private String revision;
}
