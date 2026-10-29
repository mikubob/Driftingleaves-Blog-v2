package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 账号与个人资料；注销/停用使用status，避免删除作者记录。 */
@Data
@TableName("sys_user")
public class SysUser {
    /** 用户ID，Java Long；API返回字符串。 */
    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;

    /** 登录名，不区分大小写。 */
    @TableField("username")
    private String username;

    /** 自适应密码哈希；NULL表示未设置本站密码，不可密码登录。 */
    @TableField("password_hash")
    private String passwordHash;

    /** 用户昵称。 */
    @TableField("nickname")
    private String nickname;

    /** 未绑定为NULL，空字符串由应用归一化为NULL。 */
    @TableField("email")
    private String email;

    /** 头像URL。 */
    @TableField("avatar")
    private String avatar;

    /** 个人简介。 */
    @TableField("intro")
    private String intro;

    /** 0禁用 1启用。 */
    @TableField("status")
    private Integer status;

    /** 最近一次登录时间，未登录为NULL。 */
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    /** 创建时间；由数据库默认值维护，应用不填充。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 最后更新时间，记录修改时自动更新；由数据库默认值维护，应用不填充。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
