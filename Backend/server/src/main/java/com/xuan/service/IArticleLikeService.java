package com.xuan.service;

import com.xuan.vo.LikeStateVO;

/** 复合键点赞只有本人状态和幂等增删，没有独立 ID CRUD。 */
public interface IArticleLikeService {
    /** 只要求本人登录，仍须 PUBLIC，不额外要求 like 权限。 */
    LikeStateVO state(long article);
    /** 文章锁内重查原会话、like 权限和公开状态，重复点赞保持 true。 */
    LikeStateVO like(long article);
    /** 同样只允许 PUBLIC，没点过也成功，不删除历史非公开文章明细。 */
    LikeStateVO unlike(long article);
}
