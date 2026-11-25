package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.FileQueryDTO;
import com.xuan.entity.BlogFile;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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
     * 按文章主键游标分批扫描全部文章封面与正文，用于检查文件引用。
     *
     * @param after 上一批最大文章 ID
     * @return 文章引用信息列表
     */
    List<ArticleReference> referencesAfter(@Param("after") long after);

    /**
     * 文章文件引用检查模型
     */
    @Data
    class ArticleReference {

        private Long articleId;
        private String coverUrl;
        private String markdownContent;
    }
}
