package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;

/**
 * 邮箱必须与当前账号邮箱一致，规范化后仍须收信确认。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionWriteDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 电子邮箱
    @NotNull
    @Email
    @TextLength(min = 1, max = 254)
    private String email;

    public void setEmail(String email) {
        this.email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
