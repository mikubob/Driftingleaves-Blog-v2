package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 绑定列表只给平台和创建时间。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthBindingVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 来源
    private String source;

    // 创建时间
    private LocalDateTime createTime;
}
