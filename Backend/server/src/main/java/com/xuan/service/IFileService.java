package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.FileQueryDTO;
import com.xuan.entity.BlogFile;
import com.xuan.model.FileContent;
import com.xuan.result.PageResult;
import com.xuan.vo.BatchFileResultsVO;
import com.xuan.vo.FileVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件业务统一处理上传者范围、文章真实引用和存储补偿，不返回公开存储直链。
 */
public interface IFileService extends IService<BlogFile> {

    /** 同一快照按 query 范围查元数据与总数。 */
    PageResult<FileVO> listFiles(FileQueryDTO query);

    /** 按文件 query 范围取不可变元数据，不生成业务版本。 */
    FileVO detail(long id);

    /** 临时写入并验证图片，存储成功后写元数据；事务失败清理新对象。 */
    FileVO upload(MultipartFile file);

    /** 文件 query 范围内读取原文件，与文章公开状态无关。 */
    FileContent original(long id);

    /** 文件锁内扫描全部文章引用，无引用才删存储和元数据；存储失败保留记录。 */
    void delete(long id);

    /** 文件锁后再锁文章，公开入口固定 PUBLIC，后台分别检查文章和文件 query 范围。 */
    FileContent attachment(long articleId, long fileId, boolean admin);
    /** 每个文件独立提交，顺序与输入一致，不把部分失败伪装成整批成功。 */
    BatchFileResultsVO deleteBatch(List<String> ids);
}
