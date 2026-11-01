# 四端接口索引

先选择端，再按业务或完整路径查找。参数、响应、示例和特殊规则见 [接口文档](API.md)。

当前共 192 个 HTTP 路由、3 个 WebSocket 入口。同一路径的 authorId 扩展不重复计数。

**29 个后台动作统一支持单条和批量操作**，每次提交 1..100 项；原有 26 项替换旧单条写路由，新增用户批量启用、禁用和删除 3 项；另增未知路由上报2项，当前共 192 个路由（Admin 123 个）。公共参数、版本、事务与逐项结果见[统一操作](API.md#admin-batch)，实现与验收规则见[开发约定](API_CONTRACT.md#batch-contract)。

| 接口 | 方法与完整路径 |
| --- | --- |
| [Blog 未知路由上报](API.md#not-found-protection) | `POST /api/v1/blog/security/not-found` |
| [Admin 未知路由上报](API.md#not-found-protection) | `POST /api/v1/admin/security/not-found` |

## 1. [Admin 管理端](API.md#client-admin)

### 1.1 认证与第三方授权

| 接口 | 方法与完整路径 |
| --- | --- |
| [注册账号](API.md#admin-api-1) | `POST /api/v1/admin/auth/register` |
| [获取图形验证码](API.md#captcha) | `GET /api/v1/admin/auth/captcha` |
| [账号密码登录](API.md#admin-api-2) | `POST /api/v1/admin/auth/login` |
| [退出当前登录](API.md#admin-api-3) | `POST /api/v1/admin/auth/logout` |
| [查询可用第三方平台](API.md#admin-api-4) | `GET /api/v1/admin/auth/oauth/providers` |
| [发起第三方登录或绑定](API.md#admin-api-5) | `POST /api/v1/admin/auth/oauth/{source}/authorize` |
| [第三方平台授权回调](API.md#admin-api-6) | `GET /api/v1/admin/auth/oauth/{source}/callback` |
| [兑换一次性登录或绑定结果](API.md#admin-api-7) | `POST /api/v1/admin/auth/oauth/exchange` |

### 1.2 本人资料与安全设置

| 接口 | 方法与完整路径 |
| --- | --- |
| [查询本人资料](API.md#admin-api-8) | `GET /api/v1/admin/me` |
| [修改本人资料](API.md#admin-api-9) | `PATCH /api/v1/admin/me/profile` |
| [设置或修改本站密码](API.md#admin-api-10) | `PUT /api/v1/admin/me/password` |
| [清除本站密码](API.md#admin-api-11) | `DELETE /api/v1/admin/me/password` |
| [查询本人有效角色与权限](API.md#admin-api-12) | `GET /api/v1/admin/me/capabilities` |
| [查询本人可访问菜单树](API.md#admin-api-13) | `GET /api/v1/admin/me/menus` |
| [查询本人第三方绑定](API.md#admin-api-14) | `GET /api/v1/admin/me/oauth-bindings` |
| [解绑本人第三方账号](API.md#admin-api-15) | `DELETE /api/v1/admin/me/oauth-bindings/{source}` |

### 1.3 菜单管理

| 接口 | 方法与完整路径 |
| --- | --- |
| [查询后台完整菜单树](API.md#api-32) | `GET /api/v1/admin/menus` |
| [查询菜单详情](API.md#api-33) | `GET /api/v1/admin/menus/{menuId}` |
| [创建菜单节点](API.md#api-34) | `POST /api/v1/admin/menus` |
| [修改菜单节点](API.md#api-35) | `PUT /api/v1/admin/menus/{menuId}` |
| [批量删除菜单叶子节点](API.md#batch-menu-delete) | `POST /api/v1/admin/menus/batch-delete` |

### 1.4 用户管理

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页查询用户](API.md#api-16) | `GET /api/v1/admin/users` |
| [查询用户详情](API.md#api-17) | `GET /api/v1/admin/users/{userId}` |
| [创建用户](API.md#api-18) | `POST /api/v1/admin/users` |
| [修改用户资料及状态](API.md#api-19) | `PUT /api/v1/admin/users/{userId}` |
| [分配用户角色](API.md#api-20) | `PUT /api/v1/admin/users/{userId}/roles` |
| [批量启用用户](API.md#batch-user-enable) | `PUT /api/v1/admin/users/batch-enable` |
| [批量禁用用户](API.md#batch-user-disable) | `PUT /api/v1/admin/users/batch-disable` |
| [批量删除用户（支持单个）](API.md#batch-user-delete) | `POST /api/v1/admin/users/batch-delete` |

### 1.5 角色与授权管理

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页查询角色](API.md#api-21) | `GET /api/v1/admin/roles` |
| [查询角色详情](API.md#api-22) | `GET /api/v1/admin/roles/{roleId}` |
| [创建角色](API.md#api-23) | `POST /api/v1/admin/roles` |
| [修改角色](API.md#api-24) | `PUT /api/v1/admin/roles/{roleId}` |
| [配置角色权限与菜单](API.md#api-26) | `PUT /api/v1/admin/roles/{roleId}/grants` |
| [批量删除角色](API.md#batch-role-delete) | `POST /api/v1/admin/roles/batch-delete` |

### 1.6 权限字典管理

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页查询权限字典](API.md#api-27) | `GET /api/v1/admin/permissions` |
| [查询权限详情](API.md#api-28) | `GET /api/v1/admin/permissions/{permissionId}` |
| [创建权限字典项](API.md#api-29) | `POST /api/v1/admin/permissions` |
| [修改权限字典项](API.md#api-30) | `PUT /api/v1/admin/permissions/{permissionId}` |
| [批量删除权限字典项](API.md#batch-permission-delete) | `POST /api/v1/admin/permissions/batch-delete` |

### 1.7 文章与附件

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页查询后台文章](API.md#api-40) | `GET /api/v1/admin/articles` |
| [查询后台文章详情](API.md#api-41) | `GET /api/v1/admin/articles/{articleId}` |
| [创建草稿文章](API.md#api-42) | `POST /api/v1/admin/articles` |
| [编辑文章](API.md#api-43) | `PUT /api/v1/admin/articles/{articleId}` |
| [读取后台文章附件](API.md#api-86) | `GET /api/v1/admin/articles/{articleId}/files/{fileId}/content` |
| [批量发布或下架文章](API.md#batch-article-status) | `PUT /api/v1/admin/articles/batch-status` |
| [批量将文章移入回收站](API.md#batch-article-recycle) | `POST /api/v1/admin/articles/batch-recycle` |
| [批量恢复回收站文章](API.md#batch-article-restore) | `POST /api/v1/admin/articles/batch-restore` |
| [批量永久删除回收站文章](API.md#batch-article-permanent) | `POST /api/v1/admin/articles/batch-permanent-delete` |

### 1.8 分类与标签

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页查询后台分类](API.md#api-48) | `GET /api/v1/admin/categories` |
| [查询分类详情](API.md#api-49) | `GET /api/v1/admin/categories/{categoryId}` |
| [创建分类](API.md#api-50) | `POST /api/v1/admin/categories` |
| [修改分类](API.md#api-51) | `PUT /api/v1/admin/categories/{categoryId}` |
| [批量删除分类](API.md#batch-category-delete) | `POST /api/v1/admin/categories/batch-delete` |
| [分页查询后台标签](API.md#api-54) | `GET /api/v1/admin/tags` |
| [查询标签详情](API.md#api-55) | `GET /api/v1/admin/tags/{tagId}` |
| [创建标签](API.md#api-56) | `POST /api/v1/admin/tags` |
| [修改标签](API.md#api-57) | `PUT /api/v1/admin/tags/{tagId}` |
| [批量删除标签](API.md#batch-tag-delete) | `POST /api/v1/admin/tags/batch-delete` |

### 1.9 评论管理

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页查询后台评论](API.md#api-63) | `GET /api/v1/admin/comments` |
| [查询后台评论详情](API.md#api-64) | `GET /api/v1/admin/comments/{commentId}` |
| [批量审核评论](API.md#batch-comment-review) | `PUT /api/v1/admin/comments/batch-review` |
| [批量删除评论分支](API.md#batch-comment-delete) | `POST /api/v1/admin/comments/batch-delete` |

### 1.10 留言板与审核

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页查询后台留言](API.md#api-115) | `GET /api/v1/admin/messages` |
| [查询后台留言详情](API.md#api-116) | `GET /api/v1/admin/messages/{messageId}` |
| [后台回复留言](API.md#api-119) | `POST /api/v1/admin/messages/{messageId}/replies` |
| [批量审核留言](API.md#batch-message-review) | `PUT /api/v1/admin/messages/batch-review` |
| [批量删除留言分支](API.md#batch-message-delete) | `POST /api/v1/admin/messages/batch-delete` |

### 1.11 友链

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页查询后台友链](API.md#api-71) | `GET /api/v1/admin/links` |
| [查询友链详情](API.md#api-72) | `GET /api/v1/admin/links/{linkId}` |
| [创建友链](API.md#api-73) | `POST /api/v1/admin/links` |
| [修改或审核友链](API.md#api-74) | `PUT /api/v1/admin/links/{linkId}` |
| [批量删除友链](API.md#batch-link-delete) | `POST /api/v1/admin/links/batch-delete` |

### 1.12 站点配置

| 接口 | 方法与完整路径 |
| --- | --- |
| [查询后台配置列表](API.md#api-77) | `GET /api/v1/admin/configs` |
| [查询配置详情](API.md#api-78) | `GET /api/v1/admin/configs/{configKey}` |
| [修改配置](API.md#api-79) | `PUT /api/v1/admin/configs/{configKey}` |

### 1.13 文件管理

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页查询文件元数据](API.md#api-80) | `GET /api/v1/admin/files` |
| [查询文件元数据](API.md#api-81) | `GET /api/v1/admin/files/{fileId}` |
| [上传图片](API.md#api-82) | `POST /api/v1/admin/files` |
| [预览后台原文件](API.md#api-83) | `GET /api/v1/admin/files/{fileId}/content` |
| [批量删除文件](API.md#batch-file-delete) | `POST /api/v1/admin/files/batch-delete` |

### 1.14 主人资料与社交链接

| 接口 | 方法与完整路径 |
| --- | --- |
| [查询后台主页资料](API.md#api-89) | `GET /api/v1/admin/home/profile` |
| [设置主页资料](API.md#api-90) | `PUT /api/v1/admin/home/profile` |
| [查询后台社交链接](API.md#api-92) | `GET /api/v1/admin/home/social-links` |
| [查询社交链接详情](API.md#api-93) | `GET /api/v1/admin/home/social-links/{socialId}` |
| [创建社交链接](API.md#api-94) | `POST /api/v1/admin/home/social-links` |
| [修改社交链接](API.md#api-95) | `PUT /api/v1/admin/home/social-links/{socialId}` |
| [批量删除社交链接](API.md#batch-social-delete) | `POST /api/v1/admin/home/social-links/batch-delete` |

### 1.15 简历经历与技能

| 接口 | 方法与完整路径 |
| --- | --- |
| [查询后台简历经历](API.md#api-98) | `GET /api/v1/admin/cv/experiences` |
| [查询简历经历详情](API.md#api-99) | `GET /api/v1/admin/cv/experiences/{experienceId}` |
| [创建简历经历](API.md#api-100) | `POST /api/v1/admin/cv/experiences` |
| [修改简历经历](API.md#api-101) | `PUT /api/v1/admin/cv/experiences/{experienceId}` |
| [批量删除简历经历](API.md#batch-experience-delete) | `POST /api/v1/admin/cv/experiences/batch-delete` |
| [查询后台简历技能](API.md#api-104) | `GET /api/v1/admin/cv/skills` |
| [查询技能详情](API.md#api-105) | `GET /api/v1/admin/cv/skills/{skillId}` |
| [创建简历技能](API.md#api-106) | `POST /api/v1/admin/cv/skills` |
| [修改简历技能](API.md#api-107) | `PUT /api/v1/admin/cv/skills/{skillId}` |
| [批量删除简历技能](API.md#batch-skill-delete) | `POST /api/v1/admin/cv/skills/batch-delete` |

### 1.16 背景音乐

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页查询后台音乐](API.md#api-121) | `GET /api/v1/admin/music` |
| [查询音乐详情](API.md#api-122) | `GET /api/v1/admin/music/{musicId}` |
| [创建音乐](API.md#api-123) | `POST /api/v1/admin/music` |
| [修改音乐](API.md#api-124) | `PUT /api/v1/admin/music/{musicId}` |
| [批量删除音乐](API.md#batch-music-delete) | `POST /api/v1/admin/music/batch-delete` |

### 1.17 邮件订阅管理

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页查询后台邮件订阅](API.md#api-131) | `GET /api/v1/admin/rss-subscriptions` |
| [查询邮件订阅详情](API.md#api-132) | `GET /api/v1/admin/rss-subscriptions/{subscriptionId}` |
| [批量管理邮件订阅状态](API.md#batch-subscription-status) | `PUT /api/v1/admin/rss-subscriptions/batch-status` |
| [批量删除邮件订阅](API.md#batch-subscription-delete) | `POST /api/v1/admin/rss-subscriptions/batch-delete` |

### 1.18 访客管理

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页查询访客](API.md#api-138) | `GET /api/v1/admin/visitors` |
| [查询访客详情](API.md#api-139) | `GET /api/v1/admin/visitors/{visitorId}` |
| [批量封禁访客统计标识](API.md#batch-visitor-block) | `PUT /api/v1/admin/visitors/batch-block` |
| [批量解除访客统计限制](API.md#batch-visitor-unblock) | `PUT /api/v1/admin/visitors/batch-unblock` |
| [批量删除访客统计记录](API.md#batch-visitor-delete) | `POST /api/v1/admin/visitors/batch-delete` |

### 1.19 浏览记录管理

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页查询浏览记录](API.md#api-143) | `GET /api/v1/admin/page-views` |
| [批量清理浏览记录](API.md#batch-page-view-delete) | `POST /api/v1/admin/page-views/batch-delete` |

### 1.20 统计报表

| 接口 | 方法与完整路径 |
| --- | --- |
| [查询后台统计总览](API.md#api-146) | `GET /api/v1/admin/statistics/overview` |
| [查询浏览趋势](API.md#api-147) | `GET /api/v1/admin/statistics/page-views` |
| [查询新访客趋势](API.md#api-148) | `GET /api/v1/admin/statistics/visitors` |
| [查询访客省份分布](API.md#api-149) | `GET /api/v1/admin/statistics/provinces` |
| [查询文章访问排行](API.md#api-150) | `GET /api/v1/admin/statistics/articles` |

### 1.21 操作日志

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页查询操作日志](API.md#api-151) | `GET /api/v1/admin/operation-logs` |
| [查询操作日志详情](API.md#api-152) | `GET /api/v1/admin/operation-logs/{logId}` |
| [批量清理操作日志](API.md#batch-log-delete) | `POST /api/v1/admin/operation-logs/batch-delete` |

### 1.22 [后台统一操作公共约定](API.md#admin-batch)

以上批量入口按业务就近列出。请求模型、最多 100 项、逐条 revision、整批数据库事务、文件逐项结果与请求/响应示例统一见此节；单条与多条共用这些入口，不再保留重复的旧单条管理写接口。

## 2. [Blog 博客端](API.md#client-blog)

### 2.1 认证与第三方授权

| 接口 | 方法与完整路径 |
| --- | --- |
| [注册账号](API.md#api-1) | `POST /api/v1/blog/auth/register` |
| [获取图形验证码](API.md#captcha) | `GET /api/v1/blog/auth/captcha` |
| [账号密码登录](API.md#api-2) | `POST /api/v1/blog/auth/login` |
| [退出当前登录](API.md#api-3) | `POST /api/v1/blog/auth/logout` |
| [查询可用第三方平台](API.md#api-4) | `GET /api/v1/blog/auth/oauth/providers` |
| [发起第三方登录或绑定](API.md#api-5) | `POST /api/v1/blog/auth/oauth/{source}/authorize` |
| [第三方平台授权回调](API.md#api-6) | `GET /api/v1/blog/auth/oauth/{source}/callback` |
| [兑换一次性登录或绑定结果](API.md#api-7) | `POST /api/v1/blog/auth/oauth/exchange` |

### 2.2 本人资料与安全设置

| 接口 | 方法与完整路径 |
| --- | --- |
| [查询本人资料](API.md#api-8) | `GET /api/v1/blog/me` |
| [修改本人资料](API.md#api-9) | `PATCH /api/v1/blog/me/profile` |
| [设置或修改本站密码](API.md#api-10) | `PUT /api/v1/blog/me/password` |
| [清除本站密码](API.md#api-11) | `DELETE /api/v1/blog/me/password` |
| [查询本人有效角色与权限](API.md#api-12) | `GET /api/v1/blog/me/capabilities` |
| [查询本人可访问菜单树](API.md#api-13) | `GET /api/v1/blog/me/menus` |
| [查询本人第三方绑定](API.md#api-14) | `GET /api/v1/blog/me/oauth-bindings` |
| [解绑本人第三方账号](API.md#api-15) | `DELETE /api/v1/blog/me/oauth-bindings/{source}` |

### 2.3 文章与附件

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页查询公开文章](API.md#api-37) | `GET /api/v1/blog/articles` |
| [按 slug 查询公开文章详情](API.md#api-38) | `GET /api/v1/blog/articles/{slug}` |
| [分页查询文章月归档](API.md#api-39) | `GET /api/v1/blog/archives` |
| [查询文章热门榜](API.md#api-87) | `GET /api/v1/blog/article-rankings` |
| [分页查询文章公开评论](API.md#api-59) | `GET /api/v1/blog/articles/{articleId}/comments` |
| [发表评论或回复](API.md#api-60) | `POST /api/v1/blog/articles/{articleId}/comments` |
| [查询本人点赞状态](API.md#api-67) | `GET /api/v1/blog/articles/{articleId}/like` |
| [点赞文章](API.md#api-68) | `PUT /api/v1/blog/articles/{articleId}/like` |
| [取消文章点赞](API.md#api-69) | `DELETE /api/v1/blog/articles/{articleId}/like` |
| [读取公开文章附件](API.md#api-85) | `GET /api/v1/blog/articles/{articleId}/files/{fileId}/content` |
| [前台按作者查询公开文章（扩展既有接口）](API.md#article-author-query) | `GET /api/v1/blog/articles` |

### 2.4 作者主页

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页查询公开作者](API.md#api-154) | `GET /api/v1/blog/authors` |
| [查询作者公开主页资料](API.md#api-155) | `GET /api/v1/blog/authors/{userId}` |

### 2.5 分类与标签

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页查询公开分类](API.md#api-47) | `GET /api/v1/blog/categories` |
| [分页查询公开标签](API.md#api-53) | `GET /api/v1/blog/tags` |
| [按 slug 查询公开分类详情](API.md#api-156) | `GET /api/v1/blog/categories/{slug}` |
| [按 slug 查询公开标签详情](API.md#api-157) | `GET /api/v1/blog/tags/{slug}` |

### 2.6 留言板与审核

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页读取可见留言或回复](API.md#api-109) | `GET /api/v1/blog/messages` |
| [发表或回复留言](API.md#api-110) | `POST /api/v1/blog/messages` |

### 2.7 本人评论

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页查询本人评论](API.md#api-61) | `GET /api/v1/blog/me/comments` |
| [删除本人评论](API.md#api-62) | `DELETE /api/v1/blog/me/comments/{commentId}` |

### 2.8 本人留言

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页查询本人留言](API.md#api-111) | `GET /api/v1/blog/me/messages` |
| [查询本人留言详情](API.md#api-112) | `GET /api/v1/blog/me/messages/{messageId}` |
| [编辑本人留言](API.md#api-113) | `PUT /api/v1/blog/me/messages/{messageId}` |
| [删除本人留言](API.md#api-114) | `DELETE /api/v1/blog/me/messages/{messageId}` |

### 2.9 本人点赞记录

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页查询本人点赞的文章](API.md#api-158) | `GET /api/v1/blog/me/liked-articles` |

### 2.10 本人邮件订阅

| 接口 | 方法与完整路径 |
| --- | --- |
| [查询本人邮件订阅](API.md#api-126) | `GET /api/v1/blog/me/rss-subscription` |
| [申请或修改本人邮件订阅](API.md#api-127) | `PUT /api/v1/blog/me/rss-subscription` |
| [确认本人订阅邮箱](API.md#api-128) | `POST /api/v1/blog/me/rss-subscription/confirm` |
| [取消本人邮件订阅](API.md#api-129) | `DELETE /api/v1/blog/me/rss-subscription` |

### 2.11 友链

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页查询公开友链](API.md#api-70) | `GET /api/v1/blog/links` |

### 2.12 站点配置

| 接口 | 方法与完整路径 |
| --- | --- |
| [查询公开站点配置](API.md#api-76) | `GET /api/v1/blog/configs` |

### 2.13 主人资料与社交链接

| 接口 | 方法与完整路径 |
| --- | --- |
| [查询站点主人公开资料](API.md#blog-api-88) | `GET /api/v1/blog/profile` |
| [查询公开社交链接](API.md#blog-api-91) | `GET /api/v1/blog/social-links` |

### 2.14 背景音乐

| 接口 | 方法与完整路径 |
| --- | --- |
| [分页查询公开播放列表](API.md#api-120) | `GET /api/v1/blog/music` |

### 2.15 邮件订阅管理

| 接口 | 方法与完整路径 |
| --- | --- |
| [通过邮件链接退订](API.md#api-130) | `POST /api/v1/blog/rss-subscriptions/unsubscribe` |

### 2.16 RSS 与站点地图

| 接口 | 方法与完整路径 |
| --- | --- |
| [读取公开RSS Feed](API.md#api-135) | `GET /api/v1/blog/rss.xml` |
| [读取公开站点地图](API.md#api-136) | `GET /api/v1/blog/sitemap.xml` |

### 2.17 统计报表

| 接口 | 方法与完整路径 |
| --- | --- |
| [查询公开博客统计](API.md#api-145) | `GET /api/v1/blog/statistics` |

### 2.18 浏览统计采集

| 接口 | 方法与完整路径 |
| --- | --- |
| [记录站点页面浏览](API.md#api-137) | `POST /api/v1/blog/analytics/page-views` |

### 2.19 实时在线人数

| 接口 | 方法与完整路径 |
| --- | --- |
| [申请一次性在线票据](API.md#api-160) | `POST /api/v1/blog/analytics/online/tickets` |
| [加入原生 WebSocket 并接收快照](API.md#online-websocket) | `WebSocket /api/v1/blog/ws/online` |

## 3. [Home 主页端](API.md#client-home)

### 3.1 站点配置

| 接口 | 方法与完整路径 |
| --- | --- |
| [查询公开站点配置](API.md#home-api-76) | `GET /api/v1/home/configs` |

### 3.2 主人资料与社交链接

| 接口 | 方法与完整路径 |
| --- | --- |
| [查询站点主人公开资料](API.md#api-88) | `GET /api/v1/home/profile` |
| [查询公开社交链接](API.md#api-91) | `GET /api/v1/home/social-links` |

### 3.3 浏览统计采集

| 接口 | 方法与完整路径 |
| --- | --- |
| [记录站点页面浏览](API.md#home-api-137) | `POST /api/v1/home/analytics/page-views` |

### 3.4 实时在线人数

| 接口 | 方法与完整路径 |
| --- | --- |
| [申请一次性在线票据](API.md#home-api-160) | `POST /api/v1/home/analytics/online/tickets` |
| [加入原生 WebSocket 并接收快照](API.md#home-online-websocket) | `WebSocket /api/v1/home/ws/online` |

## 4. [Cv 简历端](API.md#client-cv)

### 4.1 站点配置

| 接口 | 方法与完整路径 |
| --- | --- |
| [查询公开站点配置](API.md#cv-api-76) | `GET /api/v1/cv/configs` |

### 4.2 主人资料与社交链接

| 接口 | 方法与完整路径 |
| --- | --- |
| [查询站点主人公开资料](API.md#cv-api-88) | `GET /api/v1/cv/profile` |
| [查询公开社交链接](API.md#cv-api-91) | `GET /api/v1/cv/social-links` |

### 4.3 简历经历与技能

| 接口 | 方法与完整路径 |
| --- | --- |
| [查询公开简历经历](API.md#api-97) | `GET /api/v1/cv/experiences` |
| [查询公开简历技能](API.md#api-103) | `GET /api/v1/cv/skills` |

### 4.4 浏览统计采集

| 接口 | 方法与完整路径 |
| --- | --- |
| [记录站点页面浏览](API.md#cv-api-137) | `POST /api/v1/cv/analytics/page-views` |

### 4.5 实时在线人数

| 接口 | 方法与完整路径 |
| --- | --- |
| [申请一次性在线票据](API.md#cv-api-160) | `POST /api/v1/cv/analytics/online/tickets` |
| [加入原生 WebSocket 并接收快照](API.md#cv-online-websocket) | `WebSocket /api/v1/cv/ws/online` |
