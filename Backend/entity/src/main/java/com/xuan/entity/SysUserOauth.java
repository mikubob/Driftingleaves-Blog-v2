package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** JustAuth第三方身份绑定；每用户每平台一个账号，不存第三方Token。 */
@Data
@TableName("sys_user_oauth")
public class SysUserOauth {
    /** 第三方账号绑定ID。 */
    @TableId(value = "oauth_id", type = IdType.AUTO)
    private Long oauthId;

    /** 本站用户ID；Sa-Token使用此ID登录。 */
    @TableField("user_id")
    private Long userId;

    /** JustAuth平台标识，服务端固定为QQ、GITHUB等大写值。 */
    @TableField("source")
    private String source;

    /** JustAuth返回的稳定用户标识，区分大小写，不使用昵称或邮箱。 */
    @TableField("uuid")
    private String uuid;

    /** 绑定时间；由数据库默认值维护，应用不填充。 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
