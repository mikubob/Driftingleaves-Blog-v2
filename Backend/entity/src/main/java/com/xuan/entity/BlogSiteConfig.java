package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 站点展示与业务开关；不保存数据库/邮件/对象存储密码。 */
@Data
@TableName("blog_site_config")
public class BlogSiteConfig {
    /** 唯一配置键，如site、comment。 */
    @TableId(value = "config_key", type = IdType.INPUT)
    private String configKey;

    /** JSON格式的配置内容；保留原始 JSON 文本，解析属于应用层。 */
    @TableField("config_value")
    private String configValue;

    /** 配置项用途说明。 */
    @TableField("description")
    private String description;

    /** 仅显式标记1的配置可返回前台。 */
    @TableField("is_public")
    private Integer isPublic;

    /** 最后更新时间，记录修改时自动更新；由数据库默认值维护，应用不填充。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
