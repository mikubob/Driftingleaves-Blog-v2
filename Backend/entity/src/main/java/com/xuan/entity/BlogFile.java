package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigInteger;
import java.time.LocalDateTime;
import lombok.Data;

/** 这里只存文件资料，私有文件必须先检查权限，再由服务端读取返回。 */
@Data
@TableName("blog_file")
public class BlogFile {
    /** 文件ID。 */
    @TableId(value = "file_id", type = IdType.AUTO)
    private Long fileId;

    /** 上传者用户ID，关联sys_user.user_id。 */
    @TableField("uploader_id")
    private Long uploaderId;

    /** 上传时的原始文件名，仅用于展示。 */
    @TableField("original_name")
    private String originalName;

    /** 存储类型，如local本地存储、oss对象存储。 */
    @TableField("storage_type")
    private String storageType;

    /** 对象存储桶名称，本地存储时为空字符串。 */
    @TableField("bucket")
    private String bucket;

    /** 服务端生成的存储键，不使用用户文件名作为路径。 */
    @TableField("object_key")
    private String objectKey;

    /** 文件MIME类型，如image/png。 */
    @TableField("mime_type")
    private String mimeType;

    /** 文件大小，单位字节。 */
    @TableField("size_bytes")
    private BigInteger sizeBytes;

    /** 上传记录的创建时间由数据库填写，应用不用赋值。 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
