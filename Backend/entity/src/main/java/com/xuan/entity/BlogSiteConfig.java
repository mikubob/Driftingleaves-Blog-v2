package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 这里只存站点展示和业务开关，不能存数据库、邮件或对象存储密码。 */
@Data
@TableName("blog_site_config")
public class BlogSiteConfig {
    /** 唯一配置键，如site、comment。 */
    @TableId(value = "config_key", type = IdType.INPUT)
    private String configKey;

    /** 配置先按原始 JSON 文本保存，怎么解析交给应用。 */
    @TableField("config_value")
    private String configValue;

    /** 配置项用途说明。 */
    @TableField("description")
    private String description;

    /** 只有明确标成 1 的配置才能返回前台。 */
    @TableField("is_public")
    private Integer isPublic;

    /** 修改记录时数据库自动更新时间，应用不用赋值。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
