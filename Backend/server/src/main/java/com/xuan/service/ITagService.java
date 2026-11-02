package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.PageQueryDTO;
import com.xuan.dto.TagQueryDTO;
import com.xuan.dto.TagWriteDTO;
import com.xuan.entity.BlogTag;
import com.xuan.result.PageResult;
import com.xuan.vo.ArticlePublicVO.Tag;
import com.xuan.vo.TagProfileVO;
import com.xuan.vo.TagPublicDetailVO;

import java.util.List;

/**
 * 标签业务接口，标签全站共用，文章标签关联仍由文章接口保存。
 */
public interface ITagService extends IService<BlogTag> {

    /** 获取前台标签列表，没有关联文章的标签也会返回。 */
    PageResult<Tag> publicTags(PageQueryDTO query);

    /** 按大小写敏感 slug 查询标签，空标签的公开文章数为零。 */
    TagPublicDetailVO publicDetail(String slug);

    /** 获取后台标签列表，按名称字面筛选。 */
    PageResult<BlogTag> listTags(TagQueryDTO query);

    /** 获取标签详情和当前版本，不存在就返回 404。 */
    TagProfileVO detail(long id);

    /** 新增标签，不自动关联文章。 */
    BlogTag create(TagWriteDTO request);

    /** 根据id更新标签，先锁住标签并检查版本，再替换名称和 slug。 */
    TagProfileVO update(long id, TagWriteDTO request, String revision);

    /** 根据id删除标签，先检查版本，再删除标签和文章标签关联，保留文章；失败时一起回滚。 */
    void delete(long id, String revision);
    /** 批量删除标签，单条也走此入口，所有数据库修改一起提交。 */
    void deleteBatch(List<BatchItemDTO> items);
}
