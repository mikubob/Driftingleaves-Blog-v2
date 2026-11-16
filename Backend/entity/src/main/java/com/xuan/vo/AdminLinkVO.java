package com.xuan.vo;

import com.xuan.entity.BlogFriendLink;
import lombok.Getter;
import lombok.Setter;

/** 后台友链列表和详情附带来源及当前申请人。 */
@Getter
@Setter
public class AdminLinkVO extends BlogFriendLink {

    private String source;
    private Applicant applicant;

    /** 申请人摘要只包含账号 ID 和昵称。 */
    @Getter
    @Setter
    public static class Applicant {

        private Long userId;
        private String nickname;
    }
}
