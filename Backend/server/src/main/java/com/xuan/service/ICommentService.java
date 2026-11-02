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
 * 评论业务接口，逐级查询回复，写入和文章状态变更使用同一把锁，删除时一并删除全部子回复。
 */
public interface ICommentService extends IService<BlogComment> {

    /** 分页查询公开文章的评论，父评论及它的全部上级评论都必须可见。 */
    PageResult<CommentPublicVO> publicComments(long article, CommentPublicQueryDTO query);

    /** 缓存公开评论列表，由 publicComments 检查文章公开状态和全部上级评论后，通过 Spring 代理调用。 */
    PageResult<CommentPublicVO> cachedPublicComments(long article, CommentPublicQueryDTO query);

    /** 发表评论，作者取当前登录用户，加锁后读取最新的评论开关和审核配置，管理员也要审核。 */
    CommentOwnVO create(long article, CommentCreateDTO request, String ip, String userAgent);

    /** 分页查询本人评论，只返回自己的评论，文章下架后仍能管理。 */
    PageResult<CommentOwnVO> ownComments(CommentOwnQueryDTO query);

    /** 根据id删除本人评论及全部子回复，不要求版本；即使有 ALL 范围，也只能选择自己的评论。 */
    void deleteOwn(long id);

    /** 分页查询后台评论，SELF 只允许查询本人文章下的评论，不是本人发表的所有评论。 */
    PageResult<CommentAdminVO> adminComments(CommentAdminQueryDTO query);

    /** 根据id查询评论详情及版本，按评论查询权限限定范围。 */
    CommentProfileVO detail(long id);

    /** 根据id审核评论，先检查审核权限范围和版本，再设置 0/1/2 状态；已删除的占位评论不能再改。 */
    CommentProfileVO review(long id, int status, String revision);

    /** 根据id删除评论及全部子回复，单独检查管理删除权限范围；非公开文章的评论也能处理，不要求版本。 */
    void moderate(long id);
    /** 批量审核评论，单条也走此入口，任一失败则整批回滚。 */
    BatchVersionsVO reviewBatch(List<BatchItemDTO> items, int status);
    /** 批量删除评论及全部子回复，单条也走此入口，任一失败则整批回滚。 */
    void moderateBatch(List<String> ids);
}
