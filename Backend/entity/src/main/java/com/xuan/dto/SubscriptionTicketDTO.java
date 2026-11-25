package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 只接受JSON票据，不接受邮箱、用户ID或URL查询替代凭据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionTicketDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 一次性票据
    @NotNull
    @TextLength(min = 1, max = 512)
    private String ticket;
}
