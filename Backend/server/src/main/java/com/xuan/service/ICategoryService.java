package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.BatchItemDTO;
import com.xuan.dto.CategoryCreateDTO;
import com.xuan.dto.CategoryQueryDTO;
import com.xuan.dto.CategoryUpdateDTO;
import com.xuan.dto.PageQueryDTO;
import com.xuan.entity.BlogCategory;
import com.xuan.result.PageResult;
import com.xuan.vo.BatchVersionsVO;
import com.xuan.vo.CategoryProfileVO;
import com.xuan.vo.CategoryPublicDetailVO;

import java.util.List;

/**
 * 分类业务接口，分类全站共用，SELF 也能操作全部分类，不按作者筛选。
 */
public interface ICategoryService extends IService<BlogCategory> {

    /** 获取展示中的分类，只返回前台需要的资料。 */
    PageResult<CategoryPublicDetailVO> publicCategories(PageQueryDTO query);

    /** 按大小写敏感 slug 查询展示中的分类，计数只含公开文章。 */
    CategoryPublicDetailVO publicDetail(String slug);

    /** 获取后台分类列表，隐藏分类也能查。 */
    PageResult<BlogCategory> listCategories(CategoryQueryDTO query);

    /** 获取分类详情和当前版本，不存在就返回 404。 */
    CategoryProfileVO detail(long id);

    /** 创建分类，不自动修改文章。 */
    BlogCategory create(CategoryCreateDTO request);

    /** 根据id更新分类，先锁住分类并检查版本，再完整替换资料，保留展示状态。 */
    CategoryProfileVO update(long id, CategoryUpdateDTO request, String revision);

    /** 批量展示或隐藏分类，全部目标检查通过后写入，版本按原选择顺序返回。 */
    BatchVersionsVO changeStatusBatch(List<BatchItemDTO> items, int status);

    /** 批量删除分类，单条也走此入口，所有数据库修改一起提交。 */
    void deleteBatch(List<BatchItemDTO> items);
}
