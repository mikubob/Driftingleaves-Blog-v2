package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账号和个人资料放在这里，停用用 status，避免删除文章作者记录。
 */
@Data
@TableName("sys_user")
public class SysUser {
    /** 用户ID，Java Long；API返回字符串。 */
    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;

    /** 登录名，不区分大小写。 */
    @TableField("username")
    private String username;

    /** 密码保存为 BCrypt 哈希；NULL 表示没设本站密码，不能密码登录。 */
    @TableField("password_hash")
    private String passwordHash;

    /** 用户昵称。 */
    @TableField("nickname")
    private String nickname;

    /** 没绑定时为 NULL，传空串时由应用转成 NULL。 */
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

    /** 创建时间由数据库填写，应用不用赋值。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 修改记录时数据库自动更新时间，应用不用赋值。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
