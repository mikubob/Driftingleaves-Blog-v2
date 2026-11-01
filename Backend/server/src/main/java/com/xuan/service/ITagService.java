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
 * 标签是全站字典，文章关联的保存仍走文章接口。
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

    /** 新建标签，不自动给文章添加关联。 */
    BlogTag create(TagWriteDTO request);

    /** 锁住标签并核对版本后，替换名称和 slug。 */
    TagProfileVO update(long id, TagWriteDTO request, String revision);

    /** 核对版本后删除标签和文章标签关联，保留文章；失败一起回滚。 */
    void delete(long id, String revision);
    /** 单个或多个目标共用入口，全部数据库变更共同提交。 */
    void deleteBatch(List<BatchItemDTO> items);
}
