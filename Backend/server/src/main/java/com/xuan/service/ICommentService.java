package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.CommentAdminQueryDTO;
import com.xuan.dto.CommentCreateDTO;
import com.xuan.dto.CommentOwnQueryDTO;
import com.xuan.dto.CommentPublicQueryDTO;
import com.xuan.entity.BlogComment;
import com.xuan.result.PageResult;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.CommentAdminVO;
import com.xuan.vo.CommentOwnVO;
import com.xuan.vo.CommentProfileVO;
import com.xuan.vo.CommentPublicVO;

import java.util.List;

/**
 * 评论逐层展示，写入与文章状态互斥，删除同时移除目标和全部子回复。
 */
public interface ICommentService extends IService<BlogComment> {

    /** 公开只看 PUBLIC 文章，父节点必须有完整可见祖先链。 */
    PageResult<CommentPublicVO> publicComments(long article, CommentPublicQueryDTO query);

    /** 供已完成当前 PUBLIC 与祖先链检查的 publicComments 经代理调用。 */
    PageResult<CommentPublicVO> cachedPublicComments(long article, CommentPublicQueryDTO query);

    /** 作者取会话，开关和审核策略取锁内最新配置，管理员也不免审。 */
    CommentOwnVO create(long article, CommentCreateDTO request);

    /** 本人只看自己的记录，下架文章也可以管理。 */
    PageResult<CommentOwnVO> ownComments(CommentOwnQueryDTO query);

    /** 即使有 ALL 也只能选择本人评论，连同全部子回复删除，不要求版本。 */
    void deleteOwn(long id);

    /** SELF 是本人文章下的评论，不能按评论者冒充文章归属。 */
    PageResult<CommentAdminVO> adminComments(CommentAdminQueryDTO query);

    /** 详情按 query 独立范围返回，附带业务版本。 */
    CommentProfileVO detail(long id);

    /** 先检查 review 范围和版本，再审核 0/1/2；占位为终态。 */
    CommentProfileVO review(long id, int status, String revision);

    /** moderate 独立范围，可以处理非公开文章；连同全部子回复删除，不要求版本。 */
    void moderate(long id);
    /** 单条和多条共用的事务入口，失败不保留部分数据库变更。 */
    BatchVersionsVO reviewBatch(List<BatchItemDTO> items, int status);
    /** 单条和多条共用的事务入口，失败不保留部分数据库变更。 */
    void moderateBatch(List<String> ids);
}
