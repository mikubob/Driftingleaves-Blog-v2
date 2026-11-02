package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.MessageAdminQueryDTO;
import com.xuan.dto.MessageCreateDTO;
import com.xuan.dto.MessageEditDTO;
import com.xuan.dto.MessageOwnQueryDTO;
import com.xuan.dto.MessagePublicQueryDTO;
import com.xuan.entity.BlogMessage;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.MessagePrivateVO;
import com.xuan.vo.MessagePublicVO;

import java.util.List;

/**
 * 留言公开、本人和后台入口各自执行可见性/归属检查。
 */
public interface IMessageService extends IService<BlogMessage> {

    /** 逐层分页，匿名和有效会话的total分别只计可见节点。 */
    PageResult<MessagePublicVO> publicMessages(MessagePublicQueryDTO query);

    /** 作者固定当前用户，私密性继承完整父链；仅保存发布时的属地、系统与浏览器类型。 */
    MessagePrivateVO create(MessageCreateDTO request, String ip, String userAgent);

    /** 本人列表只按当前user_id取数。 */
    PageResult<MessagePrivateVO> ownMessages(MessageOwnQueryDTO query);

    /** 本人详情返回编辑所需版本，他人404。 */
    ResourceVersion<MessagePrivateVO> ownDetail(long id);

    /** 编辑保留私密性和父关系，记录编辑时间并重新审核。 */
    ResourceVersion<MessagePrivateVO> edit(long id, MessageEditDTO request, String revision);

    /** 只能选择本人留言，连同全部子回复删除。 */
    void deleteOwn(long id);

    /** ALL后台独立查询权限，私密内容不会由前台路径返回。 */
    PageResult<MessagePrivateVO> adminMessages(MessageAdminQueryDTO query);

    /** ALL后台详情与版本在同一快照。 */
    ResourceVersion<MessagePrivateVO> adminDetail(long id);

    /** 实际转为通过后提交，再使用已确认订阅发送提醒。 */
    ResourceVersion<MessagePrivateVO> review(long id, int status, String revision);

    /** 用实际后台操作者身份回复，继承完整私密范围，记录本次请求的属地与设备类型。 */
    MessagePrivateVO replyAdmin(long id, MessageEditDTO request, String ip, String userAgent);
    /** 单条和多条共用的事务入口，失败不保留部分数据库变更。 */
    BatchVersionsVO reviewBatch(List<BatchItemDTO> items, int status);
    /** 单条和多条共用的事务入口，失败不保留部分数据库变更。 */
    void deleteBatch(List<String> ids);
}
