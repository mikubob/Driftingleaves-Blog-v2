package com.xuan.vo;

import com.xuan.entity.BlogFriendLink;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 友链版本交给 Controller 写响应头。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkProfileVO<T extends BlogFriendLink> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 友链信息
    private T link;

    // 资源版本号
    private String revision;
}
