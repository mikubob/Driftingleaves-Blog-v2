package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.LinkQueryDTO;
import com.xuan.dto.LinkWriteDTO;
import com.xuan.dto.PageQueryDTO;
import com.xuan.entity.BlogFriendLink;
import com.xuan.result.PageResult;
import com.xuan.vo.LinkProfileVO;
import com.xuan.vo.LinkPublicVO;

/**
 * 友链维护与公开展示直接连通，写入和成功审计共同提交。
 */
public interface IFriendLinkService extends IService<BlogFriendLink> {

    /** 只分页展示中的友链，不返回后台状态。 */
    PageResult<LinkPublicVO> publicLinks(PageQueryDTO query);

    /** 后台按名称字面包含和状态过滤，SELF 对全局资源也有效。 */
    PageResult<BlogFriendLink> listLinks(LinkQueryDTO query);

    /** 同一快照返回详情和版本。 */
    LinkProfileVO detail(long id);

    /** 新建默认待审，不抓取 URL，也不限制名称和 URL 重复。 */
    BlogFriendLink create(LinkWriteDTO request);

    /** 操作者和友链锁内复查原身份、权限及版本后完整替换。 */
    LinkProfileVO update(long id, LinkWriteDTO request, String revision);

    /** 校验版本后物理删除，成功日志失败时删除一起回滚。 */
    void delete(long id, String revision);
}
