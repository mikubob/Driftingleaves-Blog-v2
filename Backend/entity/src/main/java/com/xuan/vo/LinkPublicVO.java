package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 公开友链只含展示字段，状态和内部维护时间不返回。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkPublicVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 友链ID
    private Long linkId;

    // 友链名称
    private String linkName;

    // 链接地址
    private String url;

    // 头像URL
    private String avatar;

    // 描述
    private String description;

    // 排序权重
    private Integer sortOrder;
}
