package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.CategoryCreateDTO;
import com.xuan.dto.CategoryQueryDTO;
import com.xuan.dto.CategoryUpdateDTO;
import com.xuan.dto.PageQueryDTO;
import com.xuan.entity.BlogCategory;
import com.xuan.result.PageResult;
import com.xuan.vo.ArticlePublicVO.Category;
import com.xuan.vo.CategoryProfileVO;
import com.xuan.vo.CategoryPublicDetailVO;

/**
 * 分类是全站字典，SELF 权限也能操作整个字典，不按当前作者筛选。
 */
public interface ICategoryService extends IService<BlogCategory> {

    /** 获取展示中的分类，只返回前台需要的资料。 */
    PageResult<Category> publicCategories(PageQueryDTO query);

    /** 按大小写敏感 slug 查询展示中的分类，计数只含公开文章。 */
    CategoryPublicDetailVO publicDetail(String slug);

    /** 获取后台分类列表，隐藏分类也能查。 */
    PageResult<BlogCategory> listCategories(CategoryQueryDTO query);

    /** 获取分类详情和当前版本，不存在就返回 404。 */
    CategoryProfileVO detail(long id);

    /** 创建分类，不自动修改文章。 */
    BlogCategory create(CategoryCreateDTO request);

    /** 锁住分类并核对版本后，把资料完整替换。 */
    CategoryProfileVO update(long id, CategoryUpdateDTO request, String revision);

    /** 核对版本后删除，文章保留并把分类置空；失败时一起回滚。 */
    void delete(long id, String revision);
}
