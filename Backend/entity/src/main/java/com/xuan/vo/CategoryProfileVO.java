package com.xuan.vo;

import com.xuan.entity.BlogCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分类资料放响应体，版本单独放到 X-Revision 响应头。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryProfileVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 分类信息
    private BlogCategory category;

    // 资源版本号
    private String revision;
}
