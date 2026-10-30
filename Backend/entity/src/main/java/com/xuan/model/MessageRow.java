package com.xuan.model;

import com.xuan.entity.BlogMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 查询时连接当前公开昵称和头像，不复制邮箱、IP或管理员标记。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MessageRow extends BlogMessage {
    private String authorNickname;
    private String authorAvatar;
}
