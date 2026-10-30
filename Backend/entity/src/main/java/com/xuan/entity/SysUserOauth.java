package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 一个用户在每个平台只绑定一个账号，不保存第三方 Token。 */
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

    /** 使用 JustAuth 返回的稳定标识，保留大小写，不能用昵称或邮箱代替。 */
    @TableField("uuid")
    private String uuid;

    /** 绑定时间由数据库填写，应用不用赋值。 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
