package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.DisplayQueryDTO;
import com.xuan.dto.MusicWriteDTO;
import com.xuan.entity.BlogMusic;
import com.xuan.model.ResourceVersion;
import com.xuan.result.PageResult;
import com.xuan.vo.MusicPublicVO;

import java.util.List;

/**
 * 全站展示与后台维护共用同一份资料。
 */
public interface IMusicService extends IService<BlogMusic> {

    /** 只返回展示状态与公开字段。 */
    PageResult<MusicPublicVO> publicItems(DisplayQueryDTO query);

    /** 检查查询权限的ALL范围后分页读取。 */
    PageResult<BlogMusic> listItems(DisplayQueryDTO query);

    /** 一致快照读取资料和业务版本。 */
    ResourceVersion<BlogMusic> detail(long id);

    /** 锁操作者后创建，成功日志与资料一起提交。 */
    ResourceVersion<BlogMusic> create(MusicWriteDTO request);

    /** 资源锁后重查原会话、ALL和版本，完整替换。 */
    ResourceVersion<BlogMusic> update(long id, MusicWriteDTO request, String revision);

    /** 资源锁后核对版本，再物理删除元数据。 */
    void delete(long id, String revision);
    /** 单个或多个目标共用入口，全部数据库变更共同提交。 */
    void deleteBatch(List<BatchItemDTO> items);
}
