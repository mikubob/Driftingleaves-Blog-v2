package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文章详情和版本一起传递，版本只写到 HTTP 响应头。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleProfileVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 文章信息
    private ArticleAdminDetailVO article;

    // 资源版本号
    private String revision;
}
