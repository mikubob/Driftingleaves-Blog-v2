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
 * 文件业务接口，检查上传者范围、文章和音乐引用，处理存储失败后的清理，不返回存储直链。
 */
public interface IFileService extends IService<BlogFile> {

    /** 分页查询文件，按文件查询权限限定范围，文件资料和总数在同一个只读事务中查询。 */
    PageResult<FileVO> listFiles(FileQueryDTO query);

    /** 根据id查询文件资料，按文件查询权限限定范围；文件资料不能编辑，不生成版本。 */
    FileVO detail(long id);

    /** 上传文件，先临时保存并检查图片、音频或歌词，存储成功后保存文件资料；事务失败时删除新上传的文件。 */
    FileVO upload(MultipartFile file);

    /** 根据id读取原文件，按文件查询权限限定范围，与文章是否公开无关。 */
    FileContent original(long id);

    /** 根据id删除文件，加锁后检查全部文章和音乐引用；无人引用才删除文件及数据库记录，文件删除失败时保留记录。 */
    void delete(long id);

    /** 读取文章附件，先锁文件再锁文章；公开接口只允许公开文章，后台分别检查文章和文件的查询权限范围。 */
    FileContent attachment(long articleId, long fileId, boolean admin);

    /** 读取音乐附件，未登录时只能读取当前展示曲目实际引用的文件。 */
    FileContent musicAttachment(long fileId);
    /** 批量删除文件，每个文件独立提交，按输入顺序返回结果，部分失败时不能把整批标为成功。 */
    BatchFileResultsVO deleteBatch(List<String> ids);
}
