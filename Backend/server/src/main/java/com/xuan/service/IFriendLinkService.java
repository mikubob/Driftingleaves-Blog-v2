package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.LinkQueryDTO;
import com.xuan.dto.LinkWriteDTO;
import com.xuan.dto.PageQueryDTO;
import com.xuan.entity.BlogFriendLink;
import com.xuan.result.PageResult;
import com.xuan.vo.LinkProfileVO;
import com.xuan.vo.LinkPublicVO;

import java.util.List;

/**
 * 友情链接业务接口，管理结果直接用于公开展示，写入和成功日志一起提交。
 */
public interface IFriendLinkService extends IService<BlogFriendLink> {

    /** 查询公开友情链接列表，只查询展示中的友情链接，不返回后台状态。 */
    PageResult<LinkPublicVO> publicLinks(PageQueryDTO query);

    /** 分页查询后台友情链接，按名称和状态筛选，名称按普通文字匹配，SELF 也能操作全站友情链接。 */
    PageResult<BlogFriendLink> listLinks(LinkQueryDTO query);

    /** 根据id查询友情链接详情及版本，详情和版本在同一个只读事务中读取。 */
    LinkProfileVO detail(long id);

    /** 新增友情链接，默认待审核，不抓取链接内容，名称和 URL 可以重复。 */
    BlogFriendLink create(LinkWriteDTO request);

    /** 根据id更新友情链接，锁住操作者和友情链接后，重新检查原身份、权限及版本，再完整替换资料。 */
    LinkProfileVO update(long id, LinkWriteDTO request, String revision);

    /** 根据id删除友情链接，先检查版本再删除记录；成功日志写入失败时，删除一起回滚。 */
    void delete(long id, String revision);
    /** 批量删除友情链接，单条也走此入口，所有数据库修改一起提交。 */
    void deleteBatch(List<BatchItemDTO> items);
}
