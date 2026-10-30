package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.CommentAdminQueryDTO;
import com.xuan.dto.CommentCreateDTO;
import com.xuan.dto.CommentOwnQueryDTO;
import com.xuan.dto.CommentPublicQueryDTO;
import com.xuan.entity.BlogComment;
import com.xuan.result.PageResult;
import com.xuan.vo.CommentAdminVO;
import com.xuan.vo.CommentOwnVO;
import com.xuan.vo.CommentProfileVO;
import com.xuan.vo.CommentPublicVO;

/**
 * 评论逐层展示，写入与文章状态互斥，删除统一保留占位和回复链。
 */
public interface ICommentService extends IService<BlogComment> {

    /** 公开只看 PUBLIC 文章，父节点必须有完整可见祖先链。 */
    PageResult<CommentPublicVO> publicComments(long article, CommentPublicQueryDTO query);

    /** 作者取会话，开关和审核策略取锁内最新配置，管理员也不免审。 */
    CommentOwnVO create(long article, CommentCreateDTO request);

    /** 本人只看自己的记录，下架文章也可以管理。 */
    PageResult<CommentOwnVO> ownComments(CommentOwnQueryDTO query);

    /** 即使有 ALL 也只删本人，不要求版本，重复删除成功。 */
    void deleteOwn(long id);

    /** SELF 是本人文章下的评论，不能按评论者冒充文章归属。 */
    PageResult<CommentAdminVO> adminComments(CommentAdminQueryDTO query);

    /** 详情按 query 独立范围返回，附带业务版本。 */
    CommentProfileVO detail(long id);

    /** 先检查 review 范围和版本，再审核 0/1/2；占位为终态。 */
    CommentProfileVO review(long id, int status, String revision);

    /** moderate 独立范围，可以处理非公开文章；占位删除不要求版本。 */
    void moderate(long id);
}
