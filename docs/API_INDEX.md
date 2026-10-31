# 接口索引

按业务名称或完整路径查找；调用参数与示例见 [接口文档](API.md)。共 159 个已实现路由，前台 authorId 参数扩展也已实现。

2026-10-31补充了[限流覆盖、阈值与实施状态](API.md#request-protection)、[Redis业务缓存范围与TTL](API.md#public-cache)，以及[缓存失效、鉴权边界与后续验收](API_CONTRACT.md#cache-contract)。业务查询缓存与补充限流现已接入；实际隔离验收及故障恢复步骤见[运行说明](后端运行说明.md#spring-cache-redis)。下表仍为原有159个业务路由，不新增清缓存接口。

新增、更新、删除、下架和回收后具体清哪些缓存，见[写接口缓存删除清单](API.md#cache-lifecycle)；对应参考项目的方法与CacheEvict缓存名见[源码经验对照](API_CONTRACT.md#reference-cache-eviction)，包含内部写库同样清理的规则。

简单缓存指定采用[Spring Cache注解方案](API_CONTRACT.md#spring-cache-annotations)：Cacheable读取、CacheEvict增删改清理、Caching联合清理，RedisCacheManager统一管理TTL；已启用；关联失效、事务与并发保护统一由缓存基础设施执行。

## 1. 认证与第三方授权

| 业务 | 方法与完整路径 | 状态 |
| --- | --- | --- |
| [1.1 注册账号](API.md#api-1) | `POST /api/v1/auth/register` | 已实现 |
| [1.2 账号密码登录](API.md#api-2) | `POST /api/v1/auth/login` | 已实现 |
| [1.3 退出当前登录](API.md#api-3) | `POST /api/v1/auth/logout` | 已实现 |
| [1.4 查询可用第三方平台](API.md#api-4) | `GET /api/v1/auth/oauth/providers` | 已实现 |
| [1.5 发起第三方登录或绑定](API.md#api-5) | `POST /api/v1/auth/oauth/{source}/authorize` | 已实现 |
| [1.6 第三方平台授权回调](API.md#api-6) | `GET /api/v1/auth/oauth/{source}/callback` | 已实现 |
| [1.7 兑换一次性登录或绑定结果](API.md#api-7) | `POST /api/v1/auth/oauth/exchange` | 已实现 |

## 2. 本人资料与安全设置

| 业务 | 方法与完整路径 | 状态 |
| --- | --- | --- |
| [2.1 查询本人资料](API.md#api-8) | `GET /api/v1/me` | 已实现 |
| [2.2 修改本人资料](API.md#api-9) | `PATCH /api/v1/me/profile` | 已实现 |
| [2.3 设置或修改本站密码](API.md#api-10) | `PUT /api/v1/me/password` | 已实现 |
| [2.4 清除本站密码](API.md#api-11) | `DELETE /api/v1/me/password` | 已实现 |
| [2.5 查询本人有效角色与权限](API.md#api-12) | `GET /api/v1/me/capabilities` | 已实现 |
| [2.6 查询本人可访问菜单树](API.md#api-13) | `GET /api/v1/me/menus` | 已实现 |
| [2.7 查询本人第三方绑定](API.md#api-14) | `GET /api/v1/me/oauth-bindings` | 已实现 |
| [2.8 解绑本人第三方账号](API.md#api-15) | `DELETE /api/v1/me/oauth-bindings/{source}` | 已实现 |

## 3. 用户管理

| 业务 | 方法与完整路径 | 状态 |
| --- | --- | --- |
| [3.1 分页查询用户](API.md#api-16) | `GET /api/v1/admin/users` | 已实现 |
| [3.2 查询用户详情](API.md#api-17) | `GET /api/v1/admin/users/{userId}` | 已实现 |
| [3.3 创建用户](API.md#api-18) | `POST /api/v1/admin/users` | 已实现 |
| [3.4 修改用户资料及状态](API.md#api-19) | `PUT /api/v1/admin/users/{userId}` | 已实现 |
| [3.5 分配用户角色](API.md#api-20) | `PUT /api/v1/admin/users/{userId}/roles` | 已实现 |

## 4. 角色与授权管理

| 业务 | 方法与完整路径 | 状态 |
| --- | --- | --- |
| [4.1 分页查询角色](API.md#api-21) | `GET /api/v1/admin/roles` | 已实现 |
| [4.2 查询角色详情](API.md#api-22) | `GET /api/v1/admin/roles/{roleId}` | 已实现 |
| [4.3 创建角色](API.md#api-23) | `POST /api/v1/admin/roles` | 已实现 |
| [4.4 修改角色](API.md#api-24) | `PUT /api/v1/admin/roles/{roleId}` | 已实现 |
| [4.5 删除角色](API.md#api-25) | `DELETE /api/v1/admin/roles/{roleId}` | 已实现 |
| [4.6 配置角色权限与菜单](API.md#api-26) | `PUT /api/v1/admin/roles/{roleId}/grants` | 已实现 |

## 5. 权限字典管理

| 业务 | 方法与完整路径 | 状态 |
| --- | --- | --- |
| [5.1 分页查询权限字典](API.md#api-27) | `GET /api/v1/admin/permissions` | 已实现 |
| [5.2 查询权限详情](API.md#api-28) | `GET /api/v1/admin/permissions/{permissionId}` | 已实现 |
| [5.3 创建权限字典项](API.md#api-29) | `POST /api/v1/admin/permissions` | 已实现 |
| [5.4 修改权限字典项](API.md#api-30) | `PUT /api/v1/admin/permissions/{permissionId}` | 已实现 |
| [5.5 删除权限字典项](API.md#api-31) | `DELETE /api/v1/admin/permissions/{permissionId}` | 已实现 |

## 6. 菜单管理

| 业务 | 方法与完整路径 | 状态 |
| --- | --- | --- |
| [6.1 查询后台完整菜单树](API.md#api-32) | `GET /api/v1/admin/menus` | 已实现 |
| [6.2 查询菜单详情](API.md#api-33) | `GET /api/v1/admin/menus/{menuId}` | 已实现 |
| [6.3 创建菜单节点](API.md#api-34) | `POST /api/v1/admin/menus` | 已实现 |
| [6.4 修改菜单节点](API.md#api-35) | `PUT /api/v1/admin/menus/{menuId}` | 已实现 |
| [6.5 删除菜单节点](API.md#api-36) | `DELETE /api/v1/admin/menus/{menuId}` | 已实现 |

## 7. 文章与归档

| 业务 | 方法与完整路径 | 状态 |
| --- | --- | --- |
| [7.1 分页查询公开文章](API.md#api-37) | `GET /api/v1/blog/articles` | 已实现 |
| [7.2 按 slug 查询公开文章详情](API.md#api-38) | `GET /api/v1/blog/articles/{slug}` | 已实现 |
| [7.3 分页查询文章月归档](API.md#api-39) | `GET /api/v1/blog/archives` | 已实现 |
| [7.4 分页查询后台文章](API.md#api-40) | `GET /api/v1/admin/articles` | 已实现 |
| [7.5 查询后台文章详情](API.md#api-41) | `GET /api/v1/admin/articles/{articleId}` | 已实现 |
| [7.6 创建草稿文章](API.md#api-42) | `POST /api/v1/admin/articles` | 已实现 |
| [7.7 编辑文章](API.md#api-43) | `PUT /api/v1/admin/articles/{articleId}` | 已实现 |
| [7.8 发布或下架文章](API.md#api-44) | `PUT /api/v1/admin/articles/{articleId}/status` | 已实现 |
| [7.9 将文章移入回收站](API.md#api-45) | `DELETE /api/v1/admin/articles/{articleId}` | 已实现 |
| [7.10 恢复回收站文章](API.md#api-46) | `POST /api/v1/admin/articles/{articleId}/restore` | 已实现 |
| [7.11 查询文章热门榜](API.md#api-87) | `GET /api/v1/blog/article-rankings` | 已实现 |

## 8. 分类与标签

| 业务 | 方法与完整路径 | 状态 |
| --- | --- | --- |
| [8.1 分页查询公开分类](API.md#api-47) | `GET /api/v1/blog/categories` | 已实现 |
| [8.2 分页查询后台分类](API.md#api-48) | `GET /api/v1/admin/categories` | 已实现 |
| [8.3 查询分类详情](API.md#api-49) | `GET /api/v1/admin/categories/{categoryId}` | 已实现 |
| [8.4 创建分类](API.md#api-50) | `POST /api/v1/admin/categories` | 已实现 |
| [8.5 修改分类](API.md#api-51) | `PUT /api/v1/admin/categories/{categoryId}` | 已实现 |
| [8.6 删除分类](API.md#api-52) | `DELETE /api/v1/admin/categories/{categoryId}` | 已实现 |
| [8.7 分页查询公开标签](API.md#api-53) | `GET /api/v1/blog/tags` | 已实现 |
| [8.8 分页查询后台标签](API.md#api-54) | `GET /api/v1/admin/tags` | 已实现 |
| [8.9 查询标签详情](API.md#api-55) | `GET /api/v1/admin/tags/{tagId}` | 已实现 |
| [8.10 创建标签](API.md#api-56) | `POST /api/v1/admin/tags` | 已实现 |
| [8.11 修改标签](API.md#api-57) | `PUT /api/v1/admin/tags/{tagId}` | 已实现 |
| [8.12 删除标签](API.md#api-58) | `DELETE /api/v1/admin/tags/{tagId}` | 已实现 |

## 9. 评论审核与点赞

| 业务 | 方法与完整路径 | 状态 |
| --- | --- | --- |
| [9.1 分页查询文章公开评论](API.md#api-59) | `GET /api/v1/blog/articles/{articleId}/comments` | 已实现 |
| [9.2 发表评论或回复](API.md#api-60) | `POST /api/v1/blog/articles/{articleId}/comments` | 已实现 |
| [9.3 分页查询本人评论](API.md#api-61) | `GET /api/v1/me/comments` | 已实现 |
| [9.4 删除本人评论](API.md#api-62) | `DELETE /api/v1/me/comments/{commentId}` | 已实现 |
| [9.5 分页查询后台评论](API.md#api-63) | `GET /api/v1/admin/comments` | 已实现 |
| [9.6 查询后台评论详情](API.md#api-64) | `GET /api/v1/admin/comments/{commentId}` | 已实现 |
| [9.7 审核评论](API.md#api-65) | `PUT /api/v1/admin/comments/{commentId}/review` | 已实现 |
| [9.8 后台管理删除评论](API.md#api-66) | `DELETE /api/v1/admin/comments/{commentId}` | 已实现 |
| [9.9 查询本人点赞状态](API.md#api-67) | `GET /api/v1/blog/articles/{articleId}/like` | 已实现 |
| [9.10 点赞文章](API.md#api-68) | `PUT /api/v1/blog/articles/{articleId}/like` | 已实现 |
| [9.11 取消文章点赞](API.md#api-69) | `DELETE /api/v1/blog/articles/{articleId}/like` | 已实现 |

## 10. 友链与站点配置

| 业务 | 方法与完整路径 | 状态 |
| --- | --- | --- |
| [10.1 分页查询公开友链](API.md#api-70) | `GET /api/v1/blog/links` | 已实现 |
| [10.2 分页查询后台友链](API.md#api-71) | `GET /api/v1/admin/links` | 已实现 |
| [10.3 查询友链详情](API.md#api-72) | `GET /api/v1/admin/links/{linkId}` | 已实现 |
| [10.4 创建友链](API.md#api-73) | `POST /api/v1/admin/links` | 已实现 |
| [10.5 修改或审核友链](API.md#api-74) | `PUT /api/v1/admin/links/{linkId}` | 已实现 |
| [10.6 删除友链](API.md#api-75) | `DELETE /api/v1/admin/links/{linkId}` | 已实现 |
| [10.7 查询公开站点配置](API.md#api-76) | `GET /api/v1/blog/configs` | 已实现 |
| [10.8 查询后台配置列表](API.md#api-77) | `GET /api/v1/admin/configs` | 已实现 |
| [10.9 查询配置详情](API.md#api-78) | `GET /api/v1/admin/configs/{configKey}` | 已实现 |
| [10.10 修改配置](API.md#api-79) | `PUT /api/v1/admin/configs/{configKey}` | 已实现 |

## 11. 文件与附件

| 业务 | 方法与完整路径 | 状态 |
| --- | --- | --- |
| [11.1 分页查询文件元数据](API.md#api-80) | `GET /api/v1/admin/files` | 已实现 |
| [11.2 查询文件元数据](API.md#api-81) | `GET /api/v1/admin/files/{fileId}` | 已实现 |
| [11.3 上传图片](API.md#api-82) | `POST /api/v1/admin/files` | 已实现 |
| [11.4 预览后台原文件](API.md#api-83) | `GET /api/v1/admin/files/{fileId}/content` | 已实现 |
| [11.5 删除文件](API.md#api-84) | `DELETE /api/v1/admin/files/{fileId}` | 已实现 |
| [11.6 读取公开文章附件](API.md#api-85) | `GET /api/v1/blog/articles/{articleId}/files/{fileId}/content` | 已实现 |
| [11.7 读取后台文章附件](API.md#api-86) | `GET /api/v1/admin/articles/{articleId}/files/{fileId}/content` | 已实现 |

## 15. 主页展示与在线简历

| 业务 | 方法与完整路径 | 状态 |
| --- | --- | --- |
| [15.1 查询站点主人公开资料](API.md#api-88) | `GET /api/v1/home/profile` | 已实现 |
| [15.2 查询后台主页资料](API.md#api-89) | `GET /api/v1/admin/home/profile` | 已实现 |
| [15.3 设置主页资料](API.md#api-90) | `PUT /api/v1/admin/home/profile` | 已实现 |
| [15.4 查询公开社交链接](API.md#api-91) | `GET /api/v1/home/social-links` | 已实现 |
| [15.5 查询后台社交链接](API.md#api-92) | `GET /api/v1/admin/home/social-links` | 已实现 |
| [15.6 查询社交链接详情](API.md#api-93) | `GET /api/v1/admin/home/social-links/{socialId}` | 已实现 |
| [15.7 创建社交链接](API.md#api-94) | `POST /api/v1/admin/home/social-links` | 已实现 |
| [15.8 修改社交链接](API.md#api-95) | `PUT /api/v1/admin/home/social-links/{socialId}` | 已实现 |
| [15.9 删除社交链接](API.md#api-96) | `DELETE /api/v1/admin/home/social-links/{socialId}` | 已实现 |
| [15.10 查询公开简历经历](API.md#api-97) | `GET /api/v1/cv/experiences` | 已实现 |
| [15.11 查询后台简历经历](API.md#api-98) | `GET /api/v1/admin/cv/experiences` | 已实现 |
| [15.12 查询简历经历详情](API.md#api-99) | `GET /api/v1/admin/cv/experiences/{experienceId}` | 已实现 |
| [15.13 创建简历经历](API.md#api-100) | `POST /api/v1/admin/cv/experiences` | 已实现 |
| [15.14 修改简历经历](API.md#api-101) | `PUT /api/v1/admin/cv/experiences/{experienceId}` | 已实现 |
| [15.15 删除简历经历](API.md#api-102) | `DELETE /api/v1/admin/cv/experiences/{experienceId}` | 已实现 |
| [15.16 查询公开简历技能](API.md#api-103) | `GET /api/v1/cv/skills` | 已实现 |
| [15.17 查询后台简历技能](API.md#api-104) | `GET /api/v1/admin/cv/skills` | 已实现 |
| [15.18 查询技能详情](API.md#api-105) | `GET /api/v1/admin/cv/skills/{skillId}` | 已实现 |
| [15.19 创建简历技能](API.md#api-106) | `POST /api/v1/admin/cv/skills` | 已实现 |
| [15.20 修改简历技能](API.md#api-107) | `PUT /api/v1/admin/cv/skills/{skillId}` | 已实现 |
| [15.21 删除简历技能](API.md#api-108) | `DELETE /api/v1/admin/cv/skills/{skillId}` | 已实现 |

## 16. 留言板、本人留言与审核

| 业务 | 方法与完整路径 | 状态 |
| --- | --- | --- |
| [16.1 分页读取可见留言或回复](API.md#api-109) | `GET /api/v1/blog/messages` | 已实现 |
| [16.2 发表或回复留言](API.md#api-110) | `POST /api/v1/blog/messages` | 已实现 |
| [16.3 分页查询本人留言](API.md#api-111) | `GET /api/v1/me/messages` | 已实现 |
| [16.4 查询本人留言详情](API.md#api-112) | `GET /api/v1/me/messages/{messageId}` | 已实现 |
| [16.5 编辑本人留言](API.md#api-113) | `PUT /api/v1/me/messages/{messageId}` | 已实现 |
| [16.6 删除本人留言](API.md#api-114) | `DELETE /api/v1/me/messages/{messageId}` | 已实现 |
| [16.7 分页查询后台留言](API.md#api-115) | `GET /api/v1/admin/messages` | 已实现 |
| [16.8 查询后台留言详情](API.md#api-116) | `GET /api/v1/admin/messages/{messageId}` | 已实现 |
| [16.9 审核留言](API.md#api-117) | `PUT /api/v1/admin/messages/{messageId}/review` | 已实现 |
| [16.10 后台删除留言](API.md#api-118) | `DELETE /api/v1/admin/messages/{messageId}` | 已实现 |
| [16.11 后台回复留言](API.md#api-119) | `POST /api/v1/admin/messages/{messageId}/replies` | 已实现 |

## 17. 背景音乐与播放列表

| 业务 | 方法与完整路径 | 状态 |
| --- | --- | --- |
| [17.1 分页查询公开播放列表](API.md#api-120) | `GET /api/v1/blog/music` | 已实现 |
| [17.2 分页查询后台音乐](API.md#api-121) | `GET /api/v1/admin/music` | 已实现 |
| [17.3 查询音乐详情](API.md#api-122) | `GET /api/v1/admin/music/{musicId}` | 已实现 |
| [17.4 创建音乐](API.md#api-123) | `POST /api/v1/admin/music` | 已实现 |
| [17.5 修改音乐](API.md#api-124) | `PUT /api/v1/admin/music/{musicId}` | 已实现 |
| [17.6 删除音乐](API.md#api-125) | `DELETE /api/v1/admin/music/{musicId}` | 已实现 |

## 18. 邮件订阅、RSS Feed 与站点地图

| 业务 | 方法与完整路径 | 状态 |
| --- | --- | --- |
| [18.1 查询本人邮件订阅](API.md#api-126) | `GET /api/v1/me/rss-subscription` | 已实现 |
| [18.2 申请或修改本人邮件订阅](API.md#api-127) | `PUT /api/v1/me/rss-subscription` | 已实现 |
| [18.3 确认本人订阅邮箱](API.md#api-128) | `POST /api/v1/me/rss-subscription/confirm` | 已实现 |
| [18.4 取消本人邮件订阅](API.md#api-129) | `DELETE /api/v1/me/rss-subscription` | 已实现 |
| [18.5 通过邮件链接退订](API.md#api-130) | `POST /api/v1/blog/rss-subscriptions/unsubscribe` | 已实现 |
| [18.6 分页查询后台邮件订阅](API.md#api-131) | `GET /api/v1/admin/rss-subscriptions` | 已实现 |
| [18.7 查询邮件订阅详情](API.md#api-132) | `GET /api/v1/admin/rss-subscriptions/{subscriptionId}` | 已实现 |
| [18.8 管理邮件订阅状态](API.md#api-133) | `PUT /api/v1/admin/rss-subscriptions/{subscriptionId}/status` | 已实现 |
| [18.9 删除邮件订阅](API.md#api-134) | `DELETE /api/v1/admin/rss-subscriptions/{subscriptionId}` | 已实现 |
| [18.10 读取公开RSS Feed](API.md#api-135) | `GET /api/v1/blog/rss.xml` | 已实现 |
| [18.11 读取公开站点地图](API.md#api-136) | `GET /api/v1/blog/sitemap.xml` | 已实现 |

## 19. 访客、浏览记录与统计报表

| 业务 | 方法与完整路径 | 状态 |
| --- | --- | --- |
| [19.1 记录站点页面浏览](API.md#api-137) | `POST /api/v1/analytics/page-views` | 已实现 |
| [19.2 分页查询访客](API.md#api-138) | `GET /api/v1/admin/visitors` | 已实现 |
| [19.3 查询访客详情](API.md#api-139) | `GET /api/v1/admin/visitors/{visitorId}` | 已实现 |
| [19.4 封禁访客统计标识](API.md#api-140) | `PUT /api/v1/admin/visitors/{visitorId}/block` | 已实现 |
| [19.5 解除访客统计限制](API.md#api-141) | `PUT /api/v1/admin/visitors/{visitorId}/unblock` | 已实现 |
| [19.6 删除访客统计记录](API.md#api-142) | `DELETE /api/v1/admin/visitors/{visitorId}` | 已实现 |
| [19.7 分页查询浏览记录](API.md#api-143) | `GET /api/v1/admin/page-views` | 已实现 |
| [19.8 删除浏览记录](API.md#api-144) | `DELETE /api/v1/admin/page-views/{viewId}` | 已实现 |
| [19.9 查询公开博客统计](API.md#api-145) | `GET /api/v1/blog/statistics` | 已实现 |
| [19.10 查询后台统计总览](API.md#api-146) | `GET /api/v1/admin/statistics/overview` | 已实现 |
| [19.11 查询浏览趋势](API.md#api-147) | `GET /api/v1/admin/statistics/page-views` | 已实现 |
| [19.12 查询新访客趋势](API.md#api-148) | `GET /api/v1/admin/statistics/visitors` | 已实现 |
| [19.13 查询访客省份分布](API.md#api-149) | `GET /api/v1/admin/statistics/provinces` | 已实现 |
| [19.14 查询文章访问排行](API.md#api-150) | `GET /api/v1/admin/statistics/articles` | 已实现 |

## 20. 操作日志

| 业务 | 方法与完整路径 | 状态 |
| --- | --- | --- |
| [20.1 分页查询操作日志](API.md#api-151) | `GET /api/v1/admin/operation-logs` | 已实现 |
| [20.2 查询操作日志详情](API.md#api-152) | `GET /api/v1/admin/operation-logs/{logId}` | 已实现 |
| [20.3 清理单条操作日志](API.md#api-153) | `DELETE /api/v1/admin/operation-logs/{logId}` | 已实现 |

## 22. 文章发现与内容管理补充

| 业务 | 方法与完整路径 | 状态 |
| --- | --- | --- |
| [22.1 前台按作者查询公开文章（扩展既有接口）](API.md#article-author-query) | `GET /api/v1/blog/articles`，新增可选 `authorId` | 已实现参数扩展 |
| [22.2 分页查询公开作者](API.md#api-154) | `GET /api/v1/blog/authors` | 已实现 |
| [22.3 查询作者公开主页资料](API.md#api-155) | `GET /api/v1/blog/authors/{userId}` | 已实现 |
| [22.4 按 slug 查询公开分类详情](API.md#api-156) | `GET /api/v1/blog/categories/{slug}` | 已实现 |
| [22.5 按 slug 查询公开标签详情](API.md#api-157) | `GET /api/v1/blog/tags/{slug}` | 已实现 |
| [22.6 分页查询本人点赞的文章](API.md#api-158) | `GET /api/v1/me/liked-articles` | 已实现 |
| [22.7 永久删除回收站文章](API.md#api-159) | `DELETE /api/v1/admin/articles/{articleId}/permanent` | 已实现 |
