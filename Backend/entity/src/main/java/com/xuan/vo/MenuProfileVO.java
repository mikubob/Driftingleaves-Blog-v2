package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 菜单资料和版本一起传递，版本只放 HTTP 响应头。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuProfileVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 菜单信息
    private MenuVO menu;

    // 资源版本号
    private String revision;
}
