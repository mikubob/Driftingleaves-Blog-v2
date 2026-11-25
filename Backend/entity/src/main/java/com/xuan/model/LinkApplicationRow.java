package com.xuan.model;

import com.xuan.entity.BlogFriendLink;
import lombok.Getter;
import lombok.Setter;

/**
 * 加锁时读取申请人 ID，用于更新和检查版本。
 */
@Getter
@Setter
public class LinkApplicationRow extends BlogFriendLink {

    private Long applicantId;
}
