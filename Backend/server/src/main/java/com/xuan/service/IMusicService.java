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
 * 音乐业务接口，公开展示和后台管理使用同一份资料。
 */
public interface IMusicService extends IService<BlogMusic> {

    /** 查询公开音乐列表，只返回展示中的记录和允许公开的字段。 */
    PageResult<MusicPublicVO> publicItems(DisplayQueryDTO query);

    /** 分页查询后台音乐，查询权限必须有 ALL 范围。 */
    PageResult<BlogMusic> listItems(DisplayQueryDTO query);

    /** 根据id查询音乐详情及版本，资料和版本在同一个只读事务中读取。 */
    ResourceVersion<BlogMusic> detail(long id);

    /** 新增音乐，先锁住操作者，资料和成功日志一起提交。 */
    ResourceVersion<BlogMusic> create(MusicWriteDTO request);

    /** 根据id更新音乐，加锁后重新检查原会话、ALL 范围和版本，再完整替换资料。 */
    ResourceVersion<BlogMusic> update(long id, MusicWriteDTO request, String revision);

    /** 根据id删除音乐，加锁并检查版本后，从数据库删除记录。 */
    void delete(long id, String revision);
    /** 批量删除音乐，单条也走此入口，所有数据库修改一起提交。 */
    void deleteBatch(List<BatchItemDTO> items);
}
