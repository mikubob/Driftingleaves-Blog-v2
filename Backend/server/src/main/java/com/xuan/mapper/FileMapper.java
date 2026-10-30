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
 * 文件查询和删除都在 SQL 里限制上传者；文章引用只按批读取，不另建引用表。
 */
@Mapper
public interface FileMapper extends BaseMapper<BlogFile> {

    /** 列表与总数共用操作范围，keyword 按文件展示名称字面包含。 */
    IPage<BlogFile> filePage(IPage<BlogFile> page, @Param("query") FileQueryDTO query,
            @Param("uploader") Long uploader, @Param("filterUploader") Long filterUploader);

    /** 调用方已经持有文件行锁，并在锁后重新计算 delete 范围。 */
    int deleteScoped(@Param("id") long id, @Param("uploader") Long uploader);

    /** 持文件锁后按主键向前扫描全部状态的文章，READ COMMITTED 下读取最新正文与封面。 */
    List<ArticleReference> referencesAfter(@Param("after") long after);

    /**
     * 仅取删除引用检查用的源资料，缺正文时不能当作没有引用。
     */
    @Data
    class ArticleReference {

        private Long articleId;
        private String coverUrl;
        private String markdownContent;
    }
}
