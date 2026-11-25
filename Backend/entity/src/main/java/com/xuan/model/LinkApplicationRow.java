package com.xuan.model;

import com.xuan.entity.BlogFriendLink;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;

/**
 * 加锁时读取申请人 ID，用于更新和检查版本。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LinkApplicationRow extends BlogFriendLink {

    @Serial
    private static final long serialVersionUID = 1L;

    // 申请人用户ID
    private Long applicantId;
}
