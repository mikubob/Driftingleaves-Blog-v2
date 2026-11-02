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
 * 留言业务接口，公开、本人和后台操作分别检查可见范围及归属。
 */
public interface IMessageService extends IService<BlogMessage> {

    /** 分页查询留言及直接回复，未登录和已登录用户都只统计自己能看到的留言。 */
    PageResult<MessagePublicVO> publicMessages(MessagePublicQueryDTO query);

    /** 发表留言，作者取当前登录用户，是否私密受全部上级留言限制；只保存发布时的属地、系统和浏览器类型。 */
    MessagePrivateVO create(MessageCreateDTO request, String ip, String userAgent);

    /** 分页查询本人留言，只使用当前登录用户的id。 */
    PageResult<MessagePrivateVO> ownMessages(MessageOwnQueryDTO query);

    /** 根据id查询本人留言及版本，查询他人留言返回 404。 */
    ResourceVersion<MessagePrivateVO> ownDetail(long id);

    /** 修改本人留言，保留私密设置和回复关系，记录编辑时间并重新审核。 */
    ResourceVersion<MessagePrivateVO> edit(long id, MessageEditDTO request, String revision);

    /** 根据id删除本人留言及全部子回复，只能选择自己的留言。 */
    void deleteOwn(long id);

    /** 分页查询后台留言，需要独立的查询权限及 ALL 范围；前台接口不会因此公开私密留言。 */
    PageResult<MessagePrivateVO> adminMessages(MessageAdminQueryDTO query);

    /** 根据id查询后台留言及版本，需要 ALL 范围，详情和版本在同一个只读事务中读取。 */
    ResourceVersion<MessagePrivateVO> adminDetail(long id);

    /** 审核留言，状态实际变为通过且事务提交后，向已确认的订阅发送提醒。 */
    ResourceVersion<MessagePrivateVO> review(long id, int status, String revision);

    /** 在后台回复留言，作者取实际操作者，遵守全部上级留言的私密限制，记录本次请求的属地和设备类型。 */
    MessagePrivateVO replyAdmin(long id, MessageEditDTO request, String ip, String userAgent);
    /** 批量审核留言，单条也走此入口，任一失败则整批回滚。 */
    BatchVersionsVO reviewBatch(List<BatchItemDTO> items, int status);
    /** 批量删除留言及全部子回复，单条也走此入口，任一失败则整批回滚。 */
    void deleteBatch(List<String> ids);
}
