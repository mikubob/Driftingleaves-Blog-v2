package com.xuan.vo;

import com.xuan.entity.BlogFriendLink;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;

/**
 * 后台友链列表和详情附带来源及当前申请人。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AdminLinkVO extends BlogFriendLink {

    @Serial
    private static final long serialVersionUID = 1L;

    // 友链来源
    private String source;

    // 申请人信息
    private Applicant applicant;

    /**
     * 申请人摘要只包含账号 ID 和昵称。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Applicant implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        // 用户ID
        private Long userId;

        // 昵称
        private String nickname;
    }
}
