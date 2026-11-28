package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.FileQueryDTO;
import com.xuan.entity.BlogFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 文件数据访问接口
 */
@Mapper
public interface FileMapper extends BaseMapper<BlogFile> {

    /**
     * 分页查询文件列表，并在 SQL 中限制上传者权限范围。
     *
     * @param page 分页参数
     * @param query 查询条件
     * @param uploader SELF 范围上传者 ID，ALL 范围为 null
     * @param filterUploader 筛选条件中的上传者 ID，可为空
     * @return 文件分页结果
     */
    IPage<BlogFile> filePage(IPage<BlogFile> page,
            @Param("query") FileQueryDTO query,
            @Param("uploader") Long uploader,
            @Param("filterUploader") Long filterUploader);

    /**
     * 按上传者权限范围删除文件记录，调用方须已持有文件行锁。
     *
     * @param id 文件 ID
     * @param uploader SELF 范围上传者 ID，ALL 范围为 null
     * @return 受影响行数
     */
    int deleteScoped(@Param("id") long id, @Param("uploader") Long uploader);

    /**
     * 检查文件是否仍被任意文章引用，走 blog_article_file 索引查询。
     *
     * @param fileId 文件 ID
     * @return 是否存在文章引用
     */
    boolean hasArticleReference(@Param("fileId") long fileId);

    /**
     * 检查指定文章是否引用了指定文件，走 blog_article_file 主键查询。
     *
     * @param articleId 文章 ID
     * @param fileId 文件 ID
     * @return 是否存在引用关系
     */
    boolean hasArticleFileReference(@Param("articleId") long articleId, @Param("fileId") long fileId);
}
