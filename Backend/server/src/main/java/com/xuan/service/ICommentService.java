package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.DiscussionConversationQueryDTO;
import com.xuan.vo.DiscussionConversationVO;
import com.xuan.dto.CommentAdminQueryDTO;
import com.xuan.dto.CommentCreateDTO;
import com.xuan.dto.CommentOwnQueryDTO;
import com.xuan.dto.CommentPublicQueryDTO;
import com.xuan.entity.BlogComment;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.CommentAdminVO;
import com.xuan.vo.CommentOwnVO;
import com.xuan.vo.CommentProfileVO;
import com.xuan.vo.CommentPublicVO;

import java.util.List;

/**
 * 评论业务接口，整组分页查询回复，写入和文章状态变更使用同一把锁，删除时一并删除全部子回复。
 */
public interface ICommentService extends IService<BlogComment> {

    /** 分页查询公开文章的评论，保留本人待审和驳回记录，父评论及全部上级须对当前读者可见。 */
    PageResult<CommentPublicVO> publicComments(long article, CommentPublicQueryDTO query);

    /** 缓存公开评论列表，由 publicComments 检查文章公开状态和全部上级评论后，通过 Spring 代理调用。 */
    PageResult<CommentPublicVO> cachedPublicComments(long article, CommentPublicQueryDTO query);

    /** 发表评论，作者取当前登录用户，加锁后读取最新开关和审核配置；有效管理员免审，有效作者评论自己的文章免审。 */
    CommentOwnVO create(long article, CommentCreateDTO request, String ip, String userAgent);

    /** 分页查询本人评论，只返回自己的评论，文章下架后仍能管理。 */
    PageResult<CommentOwnVO> ownComments(CommentOwnQueryDTO query);

    /** 根据id查询本人评论及版本，文章下架后仍可查询。 */
    ResourceVersion<CommentOwnVO> ownDetail(long id);

    /** 根据id删除本人评论及全部子回复，不要求版本；即使有 ALL 范围，也只能选择自己的评论。 */
    void deleteOwn(long id);

    /** 分页查询后台评论或一层回复，未传parentId保留平铺；SELF按所属文章作者限定。 */
    PageResult<CommentAdminVO> adminComments(CommentAdminQueryDTO query);

    /** 查询主记录及整组回复，按当前查询权限限定范围。 */
    DiscussionConversationVO<CommentAdminVO> conversation(long id, DiscussionConversationQueryDTO query);

    /** 根据id查询评论详情及版本，按评论查询权限限定范围。 */
    CommentProfileVO detail(long id);

    /** 批量审核评论，单条也走此入口，任一失败则整批回滚。 */
    BatchVersionsVO reviewBatch(List<BatchItemDTO> items, int status);
    /** 批量删除评论及全部子回复，单条也走此入口，任一失败则整批回滚。 */
    void moderateBatch(List<String> ids);
}
