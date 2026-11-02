package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.dto.SocialWriteDTO;
import com.xuan.entity.HomeSocialLink;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.vo.SocialPublicVO;

import java.util.List;

/**
 * 社交链接业务接口，公开展示和后台管理使用同一份资料。
 */
public interface ISocialService extends IService<HomeSocialLink> {

    /** 查询公开社交链接列表，只返回展示中的记录和允许公开的字段。 */
    List<SocialPublicVO> publicItems();

    /** 分页查询后台社交链接，查询权限必须有 ALL 范围。 */
    PageResult<HomeSocialLink> listItems(DisplayQueryDTO query);

    /** 根据id查询社交链接详情及版本，资料和版本在同一个只读事务中读取。 */
    ResourceVersion<HomeSocialLink> detail(long id);

    /** 新增社交链接，先锁住操作者，资料和成功日志一起提交。 */
    ResourceVersion<HomeSocialLink> create(SocialWriteDTO request);

    /** 根据id更新社交链接，加锁后重新检查原会话、ALL 范围和版本，再完整替换资料。 */
    ResourceVersion<HomeSocialLink> update(long id, SocialWriteDTO request, String revision);

    /** 根据id删除社交链接，加锁并检查版本后，从数据库删除记录。 */
    void delete(long id, String revision);
    /** 批量删除社交链接，单条也走此入口，所有数据库修改一起提交。 */
    void deleteBatch(List<BatchItemDTO> items);
}
