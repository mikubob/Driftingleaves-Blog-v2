package com.xuan.service;

import com.xuan.vo.LikeStateVO;

/**
 * 文章点赞业务接口，查询本人状态、点赞和取消点赞，重复操作保持相同结果，不单独生成点赞id。
 */
public interface IArticleLikeService {

    /** 查询本人点赞状态，要求用户登录、文章公开，不额外要求点赞权限。 */
    LikeStateVO state(long article);

    /** 为文章点赞，锁住文章后重新检查原会话、点赞权限和公开状态，重复点赞仍返回 true。 */
    LikeStateVO like(long article);

    /** 取消文章点赞，只能操作公开文章，没点过赞也返回成功；非公开文章的历史点赞记录保留。 */
    LikeStateVO unlike(long article);
}
