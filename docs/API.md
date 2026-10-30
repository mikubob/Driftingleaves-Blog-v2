# DriftingLeavesV2 总接口文档

> **文档说明**：依据 V2 当前 POM、28 表数据库设计、现有鉴权实现和旧项目实际业务编写，沿用逐接口格式。更新日期：2026-10-30（Asia/Shanghai）。当前已实现第 1..6 章的 36 个身份、本人安全、后台用户、角色授权、权限字典及菜单管理接口和基础设施；其余接口均为待实现契约。隔离测试不代表真实平台已联调或新增业务接口已上线，运行说明见 [后端运行说明](后端运行说明.md)。
>
> **扩充范围**：第 15..20 章补充主页、简历、留言、音乐、邮件订阅、访问统计及操作日志；第 7、10、12..14 章同步文章导航/热门榜、配置白名单、共享模型、业务一致性和错误码。新增表中 sys_operation_log 已有实体与后台写操作审计，其余 9 表尚无实体；第 7..20 章业务 HTTP 接口仍待实现。来源与迁移取舍见第 21 章，数据库约束见 [数据库说明](../sql/README.md)。保留原有章节编号和 api-1..api-86 锚点。
>
> **维护提示**：每个接口同步维护基本信息、参数表、请求/响应示例、访问要求和业务说明。各示例使用独立的虚构场景；`<...>` 是占位值，部署 origin 和 OAuth 应用需实际配置。

## 目录

- [0. 通用协议与鉴权](#common)
- [1. 认证与第三方授权](#group-1)
- [2. 本人资料与安全设置](#group-2)
- [3. 用户管理](#group-3)
- [4. 角色与授权管理](#group-4)
- [5. 权限字典管理](#group-5)
- [6. 菜单管理](#group-6)
- [7. 文章与归档](#group-7)
- [8. 分类与标签](#group-8)
- [9. 评论审核与点赞](#group-9)
- [10. 友链与站点配置](#group-10)
- [11. 文件与附件](#group-11)
- [12. 共享模型与状态](#models)
- [13. OAuth、业务事务与一致性](#business)
- [14. 错误码与实施边界](#errors)
- [15. 主页展示与在线简历](#group-15)
- [16. 留言板、本人留言与审核](#group-16)
- [17. 背景音乐与播放列表](#group-17)
- [18. 邮件订阅、RSS Feed 与站点地图](#group-18)
- [19. 访客、浏览记录与统计报表](#group-19)
- [20. 操作日志](#group-20)
- [21. 旧项目来源与迁移对照](#legacy-sources)

---

<a id="common"></a>

## 0. 通用协议与鉴权

### 0.1 路径、类型与输入

- 根路径 `/api/v1`。`/auth` 身份流程，`/me` 操作本人，`/blog` 博客前台，`/home` 主站展示，`/cv` 简历展示，`/analytics` 公开访问采集，`/admin` 后台。下文基本信息与请求示例均使用含此前缀的完整调用路径。
- HTTPS；JSON 请求/响应 `application/json; charset=UTF-8`，属性 camelCase。设计决定JSON请求体上限8MiB（正文另有解码后1MiB限制）。文件上传为 multipart，文件内容和 OAuth 回调例外见各节。无请求体标为 `—`，不得用 GET 改变业务状态（OAuth 回调和阅读计数按各节约定）。
- `ID` 是十进制字符串，范围 `1..9223372036854775807`，拒绝 JSON 数字、负数、小数和前导零；所有主键、外键和 ID 数组均如此。非 ID 的 BIGINT 计数、字节数也用十进制字符串输出，避免精度丢失；分页统计同样见下表。
- `Time` 是 UTC、毫秒精度的 RFC 3339 字符串，如 `2026-10-29T02:00:00.000Z`。应用、连接统一使用 UTC，DATETIME(3) 按 UTC 存取；前端再转本地时区。请求中的审计时间和发布时间均不可由客户端写入；仅查询条件及明确声明的简历日期、建站日期、访客封禁到期时间可输入。
- 字符串长度按 Unicode 字符计数，密码另按 UTF-8 字节约束。除密码/Markdown 外，文本先去首尾空白；可空字符串的空串归一为 null，非空字段拒绝空白。简介/描述为纯文本。未知 JSON 字段、无效枚举、错误类型、重复 JSON 属性均返回 `400/VALIDATION_ERROR`，禁止把 DTO 直接批量映射到实体。
- `S(n)` 表示长度 1..n 的非空字符串，`S0(n)` 表示长度 0..n；`?` 表示可为 null；`[]` 表示数组。模型表内未标 `?` 的响应字段必有且非 null；列表无数据为 `[]`，不为 null。
- `URL?` 最大 2048 字符；外部 URL 默认只接受绝对 http/https（社交mailto链接的明确例外见12.9），不接受 userinfo、控制字符、javascript/data/file 协议。本站文件 URL 仅接受[文件与附件](#group-11)生成的路径。写 URL 不触发服务端抓取。头像/封面只能使用已公开的资源，不能借展示字段公开私密附件。
- `slug` 为 ASCII `[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*`，文章最长 200，分类/标签最长 100；区分大小写，不自动转小写。名称/username/email 按库 `utf8mb4_0900_ai_ci` 唯一性判断，可能连重音差异也视为相等；不能仅靠前端查重。
- 本文所有“必填”字段均不允许缺失或 null，除非显式写 `?`。PUT 是完整替换该请求模型，可空字段也必须出现；POST 的可选默认值逐项列出。PATCH 仅用于本人资料，缺失保持、null 清空可空字段。

### 0.2 响应格式与分页

正常 JSON 响应统一为 `Result<T>`：

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| code | string | 成功固定 `OK`；失败为第 14 节错误码，与旧项目整数 1/0 不同 |
| msg | string | 成功固定 `成功`；失败供人阅读，前端以 code 分支 |
| data | T 或 null | 错误和无返回值操作均为 null |
| errors | `{field:string,message:string}[]` | 固定存在；仅字段校验失败非空，不回显密码等输入 |

`Page<T>` 使用旧分页命名：`{page:number,pageSize:number,total:string,totalPages:string,records:T[]}`。`page` 默认 1、范围 1..100000；`pageSize` 默认 10、范围 1..100，评论上限 50；越界报 400，不静默纠正。`totalPages=ceil(total/pageSize)`；零记录为 `"0"`，超末页 records 为空但保留请求页码。查询与 total 使用同一过滤/数据范围。分页不承诺跨请求快照；相同排序值追加主键确定顺序。

### 0.3 并发校验与幂等

设计决定：文章及后台可变资源的**单条详情 GET** 返回业务并发版本 `X-Revision: "<opaque>"`；本人资料 GET 同样返回。需要 `If-Revision` 的写接口在基本信息和参数表中明确标注，请求原样携带该 X-Revision（含引号），只接受单个具体值，拒绝 `*`。这些写操作成功返回新 X-Revision；删除成功无需 X-Revision。列表不提供逐条 X-Revision，编辑前先取详情。

X-Revision 是本站自定义业务版本，不承担HTTP缓存校验；If-Revision也是自定义请求头。在服务端由当前业务可写字段及所属关联集合按固定顺序生成SHA-256摘要，不是新增 SQL 版本列。文章包括元数据（含status、publishTime、deletedAt）、Markdown 和排序后的 tagIds；用户包括资料/状态/roleIds；角色包括资料/permissionIds/menuIds；其他为对应详情的持久化业务字段。排除阅读/点赞/评论计数、lastLoginTime、createTime/updateTime、临时下载地址和会话信息；密码哈希不参与公开 X-Revision 或响应。先验证权限/范围，再在同一行锁事务内进行If-Revision、状态校验和写入；仅正文/关联集合变化也必须改变相应聚合 X-Revision。幂等无变化可保留 X-Revision。时间戳单独作版本可能同毫秒碰撞，不能替代此规则。

不支持通用幂等键。POST 创建重复可能 409；前端请求超时先查询确认，勿盲目重发。点赞 PUT/DELETE、注销、评论占位删除幂等；OAuth state/ticket 一次性。状态转换即使同目标也须先检查权限和当前 X-Revision；旧 X-Revision 返回 412。

<a id="auth-rules"></a>

### 0.4 Sa-Token 会话与最近认证

Sa-Token 使用不透明本站 Token，不使用 JWT。固定通过 `Authorization: Bearer <token>` 传递；后续实现配置相同 tokenName/tokenPrefix，关闭从请求体、URL、普通登录 Cookie 读取 Token。密码登录和 OAuth 登录交换结果返回相同 `Session`。多端可同时登录，每次登录签发独立 Token，不复用上次 Token。

| Session 字段 | 类型 | 含义 |
| --- | --- | --- |
| tokenType | string | 固定 Bearer |
| accessToken | string | 仅登录成功返回，不写 URL/日志；没有 refreshToken |
| expiresIn | number | 本次 Token 剩余有效秒数，首期绝对有效期 7200 秒，无滑动续期 |
| user | Me | 当前用户模型，不含密码哈希 |

浏览器前端 Token 仅存内存；刷新页面后重新登录。需要持久登录应另立设计，不自行把 Token 放 localStorage。OAuth 浏览器 Cookie 仅用于绑定流程，不作为业务登录凭证。带身份的响应及所有 `/auth、/me、/admin` 响应 `Cache-Control: no-store`；前台接口不混入隐式私人字段。除幂等注销接口外，携带过期或已注销会话访问受保护接口返回 401，前端清内存并回登录页。注销当前会话不依赖客户端 userId。

改密码、清除密码、解绑成功后撤销该用户**所有**本站会话；前端重新登录。账号禁用也撤销所有会话。角色/权限/授权变更撤销受影响会话并清除菜单权限缓存，重新登录后重新加载。停用菜单只影响菜单，不撤销接口权限。每个受保护请求均检查账号和有效授权，不能只信登录时快照。

设计决定：需撤销会话的变更持有目标用户行锁，先确认Redis撤销/清缓存成功，再提交数据库变更；Redis失败则回滚数据库、返回503。所有登录发Token也持有同一用户行锁，避免旧凭据校验后跨过撤销点又签发会话。数据库提交失败时可能已退出登录，允许重新登录；不声称MySQL与Redis具备共同事务。已经进入执行的请求以其锁内最终权限检查为准。

安全操作（设置/清除密码、发起绑定、解绑）要求当前 Token 来自最近 300 秒内的密码或第三方成功登录；超出返回 REAUTH_REQUIRED，前端重新执行登录。拥有密码时设置新密码和清除密码还必须校验 currentPassword；纯第三方账号设置首个密码通过最近一次第三方登录证明身份。不存在通用“填写 userId 证明身份”入口。

### 0.5 RBAC、菜单与数据范围

本文中 `登录本人` = 登录且账号启用，无额外业务权限，目标只取会话。`公开` = 无需登录。`后台(P)` = 登录 + 有效 `admin:access` + 有效权限 P。后台每个接口即使在前端菜单隐藏也执行上述检查；`admin` 角色不绕过检查，无通配权限。

有效权限来自启用用户、启用角色、启用权限的连接；`ALL/SELF` 只从**授予本次 P 的有效角色**求并集，有 ALL 则全部，否则 SELF。文章归属为 authorId，文件为 uploaderId，评论管理为评论所属文章 authorId。每次查询、更新和删除在 SQL 中落实归属。操作需要多个权限时分别计算并同时满足其范围，例如已发布文章正文修改同时检查 update 与 publish，不能以 update 的 ALL 扩大 publish 的 SELF。

分类、标签、友链、配置、用户、角色、权限和菜单为全局资源，SELF 不生成虚构的“本人版本”；相应权限一经授予即有全局影响。`deleteOwn` 始终校验评论 userId，不被 ALL 扩大。所有请求都拒绝未声明的 authorId/uploaderId/userId/roleIds/status 等身份或授权字段，授权专用接口除外。

设计决定：保护“最后有效管理员”指至少一个启用用户关联启用的 `admin` 角色（ALL），该角色仍拥有启用的 `admin:access` 及 `AuthorizationProvider.ADMIN_PERMISSIONS` 固定列出的用户、角色、菜单、权限管理能力；新增日志权限不自动扩大保护白名单。影响此条件的用户/角色/权限/授权写入，统一先锁定初始化 `sys_role(role_id=1)` 作为串行检查点，再检查事务后的结果；并发不能各自通过检查而合计清空管理入口。初始化 admin/author/member 角色码及所有已由后端使用的权限码不可改名或删除；状态/授权仍受此保护。普通显示名称可编辑。新增字典权限不会自动产生后端能力，也不会自动授予管理员。

### 0.6 请求保护与跨域

Redis 限流采用服务端识别 IP（仅信任明确配置的反向代理）与账号/Token 双维度；登录失败按 IP+归一 username 限制，注册/OAuth 发起/交换也按浏览器+IP，评论/点赞/上传按用户+IP。认证入口阈值是部署参数；已实现的 PUT/DELETE `/me/password` 共用 IP+当前用户的60秒10次窗口，两种方法共同计数，不与登录失败计数混用。429 使用 Retry-After，不增加验证码系统。日志不得记录密码、Authorization、OAuth code/state/ticket 或第三方 Token。跨域只允许明确前端 origin，预检允许 Authorization/If-Revision，暴露 X-Revision/Location/Retry-After；带 OAuth Cookie 请求允许 credentials，不能使用通配 origin。非白名单业务路由默认拒绝匿名。

初始角色按当前 SQL 显式授权；匿名访客不对应数据库角色：

| 角色码 | 初始能力 |
| --- | --- |
| admin | 全部初始接口权限及后台菜单，ALL；新增权限仍需显式授予 |
| author | 后台入口、本人文章创建/查询/编辑/发布/回收/恢复、分类/标签只读、本人文件管理、前台评论/点赞、本人留言与邮件订阅，SELF；无评论审核或系统授权管理权限 |
| member | 前台评论、删除本人评论、点赞/取消点赞、发表/编辑/删除本人留言、本人订阅/退订，SELF；无后台入口和菜单 |

### 0.7 鉴权组件落地要求

Sa-Token 只管理本站登录与权限校验，JustAuth 只获取经平台验证的身份；应用负责查用户、查绑定和加载授权。注册、绑定、菜单、ALL/SELF 不由 starter 自动完成。下表为本项目要求，需在实现时配置并验证：

| 配置/扩展 | 本项目契约 |
| --- | --- |
| Sa-Token 读取 | tokenName=Authorization、tokenPrefix=Bearer；只从 Header 读，关闭 Body/Query/Cookie 读取 |
| 会话 | timeout=7200 秒；不启用滑动续期；多端并存、各次 Token 独立；最近认证时间存 Token 级会话，不能只存用户级时间 |
| `StpInterface` | 返回启用用户经启用角色得到的有效 role_code/permission_code；角色码无 ROLE_ 前缀 |
| 拦截器与注解 | 实际注册登录/路由/权限检查；后台要求 admin:access 与具体权限同时成立；不依赖菜单隐藏或单个角色名放行 |
| ALL/SELF | 业务查询和更新的 SQL 按本次权限计算归属，普通权限集合不足以表达此范围 |
| Redis | 保存会话与 OAuth 流程，使用隔离键前缀；安全校验不可用返回 503，禁止降级匿名放行 |
| JustAuth state | Redis AuthStateCache timeout 使用毫秒；默认存在性检查不能代替本站流程一次性消费 |
| OAuth 回调 | 回调白名单仅允许具体平台地址；绑定回调验证服务端存储的原会话，不要求平台附加本站 Authorization |

框架核对依据：[Sa-Token 固定版本会话配置](https://raw.githubusercontent.com/dromara/Sa-Token/v1.46.0/sa-token-core/src/main/java/cn/dev33/satoken/config/SaTokenConfig.java)、[JustAuth 1.16.7 AuthStateCache](https://raw.githubusercontent.com/justauth/JustAuth/v1.16.7/src/main/java/me/zhyd/oauth/cache/AuthStateCache.java)、[JustAuth 与本站用户整合](https://www.justauth.cn/features/integrate-existing-systems/)。有效期、票据、白名单与业务授权组合是本站设计，不是框架默认值。

### 0.8 业务范围与接口索引

共 **153 个不重复的 HTTP 方法与路径组合**，其中第 1..6 章 36 个已实现，其余为待实现契约。本次新增 67 个方法与路径组合；公开查询不等于已经加入当前运行配置的白名单。关联表不单独暴露 CRUD；未规划用户物理删除、文章永久删除、密码找回、账号登录邮箱验证、公开友链申请、独立评论编辑或第三方 Token 托管。RSS 订阅邮箱确认独立定义在第 18 章。首位管理员由受控初始化创建，无公开初始化接口和默认密码。

| 模块 | 接口数 | 方法与完整路径索引 |
| --- | --- | --- |
| 认证与第三方授权 | 7 | [POST /api/v1/auth/register](#api-1)、[POST /api/v1/auth/login](#api-2)、[POST /api/v1/auth/logout](#api-3)、[GET /api/v1/auth/oauth/providers](#api-4)、[POST /api/v1/auth/oauth/{source}/authorize](#api-5)、[GET /api/v1/auth/oauth/{source}/callback](#api-6)、[POST /api/v1/auth/oauth/exchange](#api-7) |
| 本人资料与安全设置 | 8 | [GET /api/v1/me](#api-8)、[PATCH /api/v1/me/profile](#api-9)、[PUT /api/v1/me/password](#api-10)、[DELETE /api/v1/me/password](#api-11)、[GET /api/v1/me/capabilities](#api-12)、[GET /api/v1/me/menus](#api-13)、[GET /api/v1/me/oauth-bindings](#api-14)、[DELETE /api/v1/me/oauth-bindings/{source}](#api-15) |
| 用户管理 | 5 | [GET /api/v1/admin/users](#api-16)、[GET /api/v1/admin/users/{userId}](#api-17)、[POST /api/v1/admin/users](#api-18)、[PUT /api/v1/admin/users/{userId}](#api-19)、[PUT /api/v1/admin/users/{userId}/roles](#api-20) |
| 角色与授权管理 | 6 | [GET /api/v1/admin/roles](#api-21)、[GET /api/v1/admin/roles/{roleId}](#api-22)、[POST /api/v1/admin/roles](#api-23)、[PUT /api/v1/admin/roles/{roleId}](#api-24)、[DELETE /api/v1/admin/roles/{roleId}](#api-25)、[PUT /api/v1/admin/roles/{roleId}/grants](#api-26) |
| 权限字典管理 | 5 | [GET /api/v1/admin/permissions](#api-27)、[GET /api/v1/admin/permissions/{permissionId}](#api-28)、[POST /api/v1/admin/permissions](#api-29)、[PUT /api/v1/admin/permissions/{permissionId}](#api-30)、[DELETE /api/v1/admin/permissions/{permissionId}](#api-31) |
| 菜单管理 | 5 | [GET /api/v1/admin/menus](#api-32)、[GET /api/v1/admin/menus/{menuId}](#api-33)、[POST /api/v1/admin/menus](#api-34)、[PUT /api/v1/admin/menus/{menuId}](#api-35)、[DELETE /api/v1/admin/menus/{menuId}](#api-36) |
| 文章与归档 | 11 | [GET /api/v1/blog/articles](#api-37)、[GET /api/v1/blog/articles/{slug}](#api-38)、[GET /api/v1/blog/archives](#api-39)、[GET /api/v1/admin/articles](#api-40)、[GET /api/v1/admin/articles/{articleId}](#api-41)、[POST /api/v1/admin/articles](#api-42)、[PUT /api/v1/admin/articles/{articleId}](#api-43)、[PUT /api/v1/admin/articles/{articleId}/status](#api-44)、[DELETE /api/v1/admin/articles/{articleId}](#api-45)、[POST /api/v1/admin/articles/{articleId}/restore](#api-46)、[GET /api/v1/blog/article-rankings](#api-87) |
| 分类与标签 | 12 | [GET /api/v1/blog/categories](#api-47)、[GET /api/v1/admin/categories](#api-48)、[GET /api/v1/admin/categories/{categoryId}](#api-49)、[POST /api/v1/admin/categories](#api-50)、[PUT /api/v1/admin/categories/{categoryId}](#api-51)、[DELETE /api/v1/admin/categories/{categoryId}](#api-52)、[GET /api/v1/blog/tags](#api-53)、[GET /api/v1/admin/tags](#api-54)、[GET /api/v1/admin/tags/{tagId}](#api-55)、[POST /api/v1/admin/tags](#api-56)、[PUT /api/v1/admin/tags/{tagId}](#api-57)、[DELETE /api/v1/admin/tags/{tagId}](#api-58) |
| 评论审核与点赞 | 11 | [GET /api/v1/blog/articles/{articleId}/comments](#api-59)、[POST /api/v1/blog/articles/{articleId}/comments](#api-60)、[GET /api/v1/me/comments](#api-61)、[DELETE /api/v1/me/comments/{commentId}](#api-62)、[GET /api/v1/admin/comments](#api-63)、[GET /api/v1/admin/comments/{commentId}](#api-64)、[PUT /api/v1/admin/comments/{commentId}/review](#api-65)、[DELETE /api/v1/admin/comments/{commentId}](#api-66)、[GET /api/v1/blog/articles/{articleId}/like](#api-67)、[PUT /api/v1/blog/articles/{articleId}/like](#api-68)、[DELETE /api/v1/blog/articles/{articleId}/like](#api-69) |
| 友链与站点配置 | 10 | [GET /api/v1/blog/links](#api-70)、[GET /api/v1/admin/links](#api-71)、[GET /api/v1/admin/links/{linkId}](#api-72)、[POST /api/v1/admin/links](#api-73)、[PUT /api/v1/admin/links/{linkId}](#api-74)、[DELETE /api/v1/admin/links/{linkId}](#api-75)、[GET /api/v1/blog/configs](#api-76)、[GET /api/v1/admin/configs](#api-77)、[GET /api/v1/admin/configs/{configKey}](#api-78)、[PUT /api/v1/admin/configs/{configKey}](#api-79) |
| 文件与附件 | 7 | [GET /api/v1/admin/files](#api-80)、[GET /api/v1/admin/files/{fileId}](#api-81)、[POST /api/v1/admin/files](#api-82)、[GET /api/v1/admin/files/{fileId}/content](#api-83)、[DELETE /api/v1/admin/files/{fileId}](#api-84)、[GET /api/v1/blog/articles/{articleId}/files/{fileId}/content](#api-85)、[GET /api/v1/admin/articles/{articleId}/files/{fileId}/content](#api-86) |
| 主页展示与在线简历 | 21 | [GET /api/v1/home/profile](#api-88)、[GET /api/v1/admin/home/profile](#api-89)、[PUT /api/v1/admin/home/profile](#api-90)、[GET /api/v1/home/social-links](#api-91)、[GET /api/v1/admin/home/social-links](#api-92)、[GET /api/v1/admin/home/social-links/{socialId}](#api-93)、[POST /api/v1/admin/home/social-links](#api-94)、[PUT /api/v1/admin/home/social-links/{socialId}](#api-95)、[DELETE /api/v1/admin/home/social-links/{socialId}](#api-96)、[GET /api/v1/cv/experiences](#api-97)、[GET /api/v1/admin/cv/experiences](#api-98)、[GET /api/v1/admin/cv/experiences/{experienceId}](#api-99)、[POST /api/v1/admin/cv/experiences](#api-100)、[PUT /api/v1/admin/cv/experiences/{experienceId}](#api-101)、[DELETE /api/v1/admin/cv/experiences/{experienceId}](#api-102)、[GET /api/v1/cv/skills](#api-103)、[GET /api/v1/admin/cv/skills](#api-104)、[GET /api/v1/admin/cv/skills/{skillId}](#api-105)、[POST /api/v1/admin/cv/skills](#api-106)、[PUT /api/v1/admin/cv/skills/{skillId}](#api-107)、[DELETE /api/v1/admin/cv/skills/{skillId}](#api-108) |
| 留言板、本人留言与审核 | 11 | [GET /api/v1/blog/messages](#api-109)、[POST /api/v1/blog/messages](#api-110)、[GET /api/v1/me/messages](#api-111)、[GET /api/v1/me/messages/{messageId}](#api-112)、[PUT /api/v1/me/messages/{messageId}](#api-113)、[DELETE /api/v1/me/messages/{messageId}](#api-114)、[GET /api/v1/admin/messages](#api-115)、[GET /api/v1/admin/messages/{messageId}](#api-116)、[PUT /api/v1/admin/messages/{messageId}/review](#api-117)、[DELETE /api/v1/admin/messages/{messageId}](#api-118)、[POST /api/v1/admin/messages/{messageId}/replies](#api-119) |
| 背景音乐与播放列表 | 6 | [GET /api/v1/blog/music](#api-120)、[GET /api/v1/admin/music](#api-121)、[GET /api/v1/admin/music/{musicId}](#api-122)、[POST /api/v1/admin/music](#api-123)、[PUT /api/v1/admin/music/{musicId}](#api-124)、[DELETE /api/v1/admin/music/{musicId}](#api-125) |
| 邮件订阅、RSS Feed 与站点地图 | 11 | [GET /api/v1/me/rss-subscription](#api-126)、[PUT /api/v1/me/rss-subscription](#api-127)、[POST /api/v1/me/rss-subscription/confirm](#api-128)、[DELETE /api/v1/me/rss-subscription](#api-129)、[POST /api/v1/blog/rss-subscriptions/unsubscribe](#api-130)、[GET /api/v1/admin/rss-subscriptions](#api-131)、[GET /api/v1/admin/rss-subscriptions/{subscriptionId}](#api-132)、[PUT /api/v1/admin/rss-subscriptions/{subscriptionId}/status](#api-133)、[DELETE /api/v1/admin/rss-subscriptions/{subscriptionId}](#api-134)、[GET /api/v1/blog/rss.xml](#api-135)、[GET /api/v1/blog/sitemap.xml](#api-136) |
| 访客、浏览记录与统计报表 | 14 | [POST /api/v1/analytics/page-views](#api-137)、[GET /api/v1/admin/visitors](#api-138)、[GET /api/v1/admin/visitors/{visitorId}](#api-139)、[PUT /api/v1/admin/visitors/{visitorId}/block](#api-140)、[PUT /api/v1/admin/visitors/{visitorId}/unblock](#api-141)、[DELETE /api/v1/admin/visitors/{visitorId}](#api-142)、[GET /api/v1/admin/page-views](#api-143)、[DELETE /api/v1/admin/page-views/{viewId}](#api-144)、[GET /api/v1/blog/statistics](#api-145)、[GET /api/v1/admin/statistics/overview](#api-146)、[GET /api/v1/admin/statistics/page-views](#api-147)、[GET /api/v1/admin/statistics/visitors](#api-148)、[GET /api/v1/admin/statistics/provinces](#api-149)、[GET /api/v1/admin/statistics/articles](#api-150) |
| 操作日志 | 3 | [GET /api/v1/admin/operation-logs](#api-151)、[GET /api/v1/admin/operation-logs/{logId}](#api-152)、[DELETE /api/v1/admin/operation-logs/{logId}](#api-153) |

第7.11节复用原文章模块；第 15..20 章新增公共入口只在实际实现时逐项登记 HTTP 方法和完整路径；匿名统计不赋予登录身份。第 21 章是来源与取舍，不新增路由。

<a id="group-1"></a>

## 1. 认证与第三方授权

<a id="api-1"></a>

### 1.1 注册账号

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/auth/register` |
| 接口描述 | 注册账号 |
| Content-Type | application/json |
| 访问要求 | 公开 |
| 成功状态 | 201 Created |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `username` | JSON Body | string | 是 | 3..64 位 ASCII 字母、数字或下划线；全库不区分大小写唯一 |
| `password` | JSON Body | string | 是 | 8..64 个字符且 UTF-8 ≤72 字节；不 trim、不截断 |
| `nickname` | JSON Body | string | 是 | 1..64 字符 |

#### 请求示例

```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "username": "leaf",
  "password": "Example-password-2026",
  "nickname": "落叶"
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Me | 本接口业务结果 |
| `data.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.username` | string | 登录名 |
| `data.nickname` | string | 展示昵称 |
| `data.email` | string&#124;null | 未验证联系邮箱，可 null |
| `data.avatar` | string&#124;null | 外部头像 URL，可 null |
| `data.intro` | string&#124;null | 个人简介，可 null |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.hasPassword` | boolean | 是否设置有效本站密码，不返回哈希 |
| `data.lastLoginTime` | Time | 最近登录 UTC 时间，未登录可 null |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |

#### 响应示例

```http
HTTP/1.1 201 Created
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "userId": "12",
    "username": "leaf",
    "nickname": "落叶",
    "email": null,
    "avatar": null,
    "intro": null,
    "status": 1,
    "hasPassword": true,
    "lastLoginTime": "2026-10-29T02:00:00.000Z",
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

用户和 member 关联同事务创建；仅返回资料，不自动登录，不接受角色、状态或客户端 userId。

---

<a id="api-2"></a>

### 1.2 账号密码登录

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/auth/login` |
| 接口描述 | 账号密码登录 |
| Content-Type | application/json |
| 访问要求 | 公开 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `username` | JSON Body | string | 是 | 3..64 位 ASCII 字母、数字或下划线；全库不区分大小写唯一 |
| `password` | JSON Body | string | 是 | 8..64 个字符且 UTF-8 ≤72 字节；不 trim、不截断 |

#### 请求示例

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "leaf",
  "password": "Example-password-2026"
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Session | 本接口业务结果 |
| `data.tokenType` | string | 固定 Bearer |
| `data.accessToken` | string | 本站不透明 Token，仅成功登录返回 |
| `data.expiresIn` | integer | 剩余有效秒数 |
| `data.user` | object | 本人资料 Me |
| `data.user.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.user.username` | string | 登录名 |
| `data.user.nickname` | string | 展示昵称 |
| `data.user.email` | string&#124;null | 未验证联系邮箱，可 null |
| `data.user.avatar` | string&#124;null | 外部头像 URL，可 null |
| `data.user.intro` | string&#124;null | 个人简介，可 null |
| `data.user.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.user.hasPassword` | boolean | 是否设置有效本站密码，不返回哈希 |
| `data.user.lastLoginTime` | Time | 最近登录 UTC 时间，未登录可 null |
| `data.user.createTime` | Time | 创建 UTC 时间 |
| `data.user.updateTime` | Time | 修改 UTC 时间 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "tokenType": "Bearer",
    "accessToken": "<site-token>",
    "expiresIn": 7200,
    "user": {
      "userId": "12",
      "username": "leaf",
      "nickname": "落叶",
      "email": null,
      "avatar": null,
      "intro": null,
      "status": 1,
      "hasPassword": true,
      "lastLoginTime": "2026-10-29T02:00:00.000Z",
      "createTime": "2026-10-29T02:00:00.000Z",
      "updateTime": "2026-10-29T02:00:00.000Z"
    }
  },
  "errors": []
}
```

#### 业务说明

用户名不存在、密码错误、纯第三方账号无密码统一返回 401/AUTH_FAILED；验证凭据后再区分禁用账号。持有用户行锁确认身份和状态后创建会话、更新 lastLoginTime，提交后才返回；数据库提交失败撤销刚签发 Token，Redis 创建失败返回 503。

---

<a id="api-3"></a>

### 1.3 退出当前登录

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/auth/logout` |
| 接口描述 | 退出当前登录 |
| Content-Type | 无请求体 |
| 访问要求 | 幂等注销；无 Token 或已失效仍返回 200 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 否 | 提供时撤销该 Bearer Token；缺失或已失效仍成功 |

#### 请求示例

```http
POST /api/v1/auth/logout
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 无业务返回值，固定 null |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

仅撤销当前 Token；缺失或已失效也返回 200，不泄露会话状态。

---

<a id="api-4"></a>

### 1.4 查询可用第三方平台

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/auth/oauth/providers` |
| 接口描述 | 查询可用第三方平台 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

无 Path、Query 或请求体参数；GET/无模型写操作不发送请求体。

#### 请求示例

```http
GET /api/v1/auth/oauth/providers
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Provider[] | 本接口业务结果 |
| `data[].source` | string | 已配置且启用的平台标识 |
| `data[].displayName` | string | 平台展示名称 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": [
    {
      "source": "GITHUB",
      "displayName": "GitHub"
    }
  ],
  "errors": []
}
```

#### 业务说明

只列配置完整且启用的平台，示例 GITHUB 不代表已经开通；不返回 App ID/Secret。

---

<a id="api-5"></a>

### 1.5 发起第三方登录或绑定

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/auth/oauth/{source}/authorize` |
| 接口描述 | 发起第三方登录或绑定 |
| Content-Type | application/json |
| 访问要求 | LOGIN 公开；BIND 登录本人 + 最近 300 秒认证；均需浏览器 Cookie/Origin |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `source` | Path | string | 是 | 服务端已启用平台的大写标识；未知或未启用 404 |
| `Authorization` | Header | string | BIND 必填 | 发起绑定的原本站 Bearer Token；LOGIN 不要求；不是第三方平台 Token |
| `operation` | JSON Body | string | 是 | LOGIN 登录；BIND 绑定当前用户；不接收 redirectUri/userId/uuid |
| `__Host-dl-oauth-browser` | Cookie | string | 是（发起时可由后端新设） | Secure、HttpOnly、SameSite=Lax、Path=/，仅绑定浏览器，不是业务登录凭证 |
| `Origin` | Header | string | 是 | 浏览器自动发送，必须匹配配置前端 origin；BIND/兑换附加身份要求见访问要求 |

#### 请求示例

```http
POST /api/v1/auth/oauth/GITHUB/authorize
Cookie: __Host-dl-oauth-browser=<browser-id>
Origin: https://blog.example.com
Content-Type: application/json

{
  "operation": "LOGIN"
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | OAuthAuthorizeResult | 本接口业务结果 |
| `data.authorizationUrl` | string | 服务端生成的第三方授权 URL，前端不可自定回调地址 |
| `data.expiresIn` | integer | 剩余有效秒数 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
Set-Cookie: __Host-dl-oauth-browser=<browser-id>; Max-Age=600; Path=/; Secure; HttpOnly; SameSite=Lax
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "authorizationUrl": "https://github.com/login/oauth/authorize?client_id=<configured>&state=<random>",
    "expiresIn": 300
  },
  "errors": []
}
```

#### 业务说明

LOGIN 公开；BIND 需当前 Token 及最近 300 秒认证。两者均校验 Origin，并设置/读取 OAuth 浏览器 Cookie；BIND 使用弹窗保留原页面内存 Token。

---

<a id="api-6"></a>

### 1.6 第三方平台授权回调

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/auth/oauth/{source}/callback` |
| 接口描述 | 第三方平台授权回调 |
| Content-Type | 无请求体 |
| 访问要求 | 仅平台回调；校验 state、浏览器 Cookie、流程及原会话；不要求回调请求携带本站 Bearer |
| 成功状态 | 303（可信流程重定向） |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `source` | Path | string | 是 | 服务端已启用平台的大写标识；未知或未启用 404 |
| `__Host-dl-oauth-browser` | Cookie | string | 是（发起时可由后端新设） | Secure、HttpOnly、SameSite=Lax、Path=/，仅绑定浏览器，不是业务登录凭证 |
| `state` | Query | string | 是 | 与当前浏览器、平台和流程匹配，一次性 |
| `code` | Query | string | 成功时 | 所选平台 GET 回调授权码；适配器负责平台实际参数 |
| `error` | Query | string | 拒绝时 | 平台取消/错误标记，不透传平台说明 |

#### 请求示例

```http
GET /api/v1/auth/oauth/GITHUB/callback?code=<platform-code>&state=<random-state>
Cookie: __Host-dl-oauth-browser=<browser-id>
```

#### 响应参数

| 字段/响应头 | 类型 | 说明 |
| --- | --- | --- |
| `Location` | string | 固定前端完成页；成功 fragment 携带 ticket，可信失败携带白名单错误码 |
| `Cache-Control` | string | no-store |
| `Referrer-Policy` | string | no-referrer |
| 响应体 | — | 成功重定向不返回 Result；不可信/无效流程直接返回对应 HTTP JSON 错误 |

#### 响应示例

```http
HTTP/1.1 303 See Other
Location: https://blog.example.com/oauth/complete#ticket=<one-time-ticket>
Cache-Control: no-store
Referrer-Policy: no-referrer
```

#### 业务说明

平台顶层 GET 回调不携带本站 Bearer。后端凭 state、浏览器 Cookie 和 Redis 流程定位操作；BIND 再校验服务端保存的原会话仍有效且属于目标用户。可信流程成功后 303 到固定完成页，不在回调直接向浏览器发本站 Token。已消费的可信流程遇到数据库访问或事务提交失败时，303 到固定完成页的 `#error=SERVICE_UNAVAILABLE`，不签发成功票据，且不能重放本次 state。

---

<a id="api-7"></a>

### 1.7 兑换一次性登录或绑定结果

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/auth/oauth/exchange` |
| 接口描述 | 兑换一次性登录或绑定结果 |
| Content-Type | application/json |
| 访问要求 | 同浏览器 Cookie；BIND 须原 Token；LOGIN 不需本站 Token |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | BIND 必填 | 发起绑定的原本站 Bearer Token；LOGIN 不要求；不是第三方平台 Token |
| `ticket` | JSON Body | string | 是 | 回调得到的本站一次性票据；有效期 60 秒，不是平台 code 或本站 Token |
| `__Host-dl-oauth-browser` | Cookie | string | 是（发起时可由后端新设） | Secure、HttpOnly、SameSite=Lax、Path=/，仅绑定浏览器，不是业务登录凭证 |
| `Origin` | Header | string | 是 | 浏览器自动发送，必须匹配配置前端 origin；BIND/兑换附加身份要求见访问要求 |

#### 请求示例

```http
POST /api/v1/auth/oauth/exchange
Cookie: __Host-dl-oauth-browser=<browser-id>
Origin: https://blog.example.com
Content-Type: application/json

{
  "ticket": "<one-time-ticket>"
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | OAuthResult | 本接口业务结果 |
| `data.operation` | string | LOGIN 或 BIND |
| `data.session` | object | LOGIN 分支的本站 Session |
| `data.session.tokenType` | string | 固定 Bearer |
| `data.session.accessToken` | string | 本站不透明 Token，仅成功登录返回 |
| `data.session.expiresIn` | integer | 剩余有效秒数 |
| `data.session.user` | object | 本人资料 Me |
| `data.session.user.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.session.user.username` | string | 登录名 |
| `data.session.user.nickname` | string | 展示昵称 |
| `data.session.user.email` | string&#124;null | 未验证联系邮箱，可 null |
| `data.session.user.avatar` | string&#124;null | 外部头像 URL，可 null |
| `data.session.user.intro` | string&#124;null | 个人简介，可 null |
| `data.session.user.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.session.user.hasPassword` | boolean | 是否设置有效本站密码，不返回哈希 |
| `data.session.user.lastLoginTime` | Time | 最近登录 UTC 时间，未登录可 null |
| `data.session.user.createTime` | Time | 创建 UTC 时间 |
| `data.session.user.updateTime` | Time | 修改 UTC 时间 |
| `data.binding` | OAuthBinding | 仅 BIND 分支，含 source/createTime；与 session 二选一 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "operation": "LOGIN",
    "session": {
      "tokenType": "Bearer",
      "accessToken": "<site-token>",
      "expiresIn": 7200,
      "user": {
        "userId": "12",
        "username": "leaf",
        "nickname": "落叶",
        "email": null,
        "avatar": null,
        "intro": null,
        "status": 1,
        "hasPassword": true,
        "lastLoginTime": "2026-10-29T02:00:00.000Z",
        "createTime": "2026-10-29T02:00:00.000Z",
        "updateTime": "2026-10-29T02:00:00.000Z"
      }
    }
  },
  "errors": []
}
```

BIND 成功分支（仍为 200，原 Token 必须有效，不返回新 Session）：

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "operation": "BIND",
    "binding": {
      "source": "GITHUB",
      "createTime": "2026-10-29T02:00:00.000Z"
    }
  },
  "errors": []
}
```

#### 业务说明

必须携带同浏览器 Cookie；LOGIN 返回 Session，BIND 必须带发起时的原 Token，仅返回 binding，不切换账号。票据有效期 60 秒且仅消费一次。

---


<a id="group-2"></a>

## 2. 本人资料与安全设置

<a id="api-8"></a>

### 2.1 查询本人资料

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/me` |
| 接口描述 | 查询本人资料 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用，仅操作本人；身份从会话读取 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
GET /api/v1/me
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Me | 本接口业务结果 |
| `data.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.username` | string | 登录名 |
| `data.nickname` | string | 展示昵称 |
| `data.email` | string&#124;null | 未验证联系邮箱，可 null |
| `data.avatar` | string&#124;null | 外部头像 URL，可 null |
| `data.intro` | string&#124;null | 个人简介，可 null |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.hasPassword` | boolean | 是否设置有效本站密码，不返回哈希 |
| `data.lastLoginTime` | Time | 最近登录 UTC 时间，未登录可 null |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "userId": "12",
    "username": "leaf",
    "nickname": "落叶",
    "email": null,
    "avatar": null,
    "intro": null,
    "status": 1,
    "hasPassword": true,
    "lastLoginTime": "2026-10-29T02:00:00.000Z",
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

登录本人

---

<a id="api-9"></a>

### 2.2 修改本人资料

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PATCH /api/v1/me/profile` |
| 接口描述 | 修改本人资料 |
| Content-Type | application/json |
| 访问要求 | 登录且启用，仅操作本人；身份从会话读取 |
| 成功状态 | 200 OK |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |
| `nickname` | JSON Body | string | 否 | 提供时不可为 null，1..64 字符 |
| `email` | JSON Body | string&#124;null | 否 | 合法邮箱，≤254 字符，唯一；null 清空 |
| `avatar` | JSON Body | string&#124;null | 否 | 外部 http(s) URL ≤2048 字符；null 清空 |
| `intro` | JSON Body | string&#124;null | 否 | 纯文本 ≤500 字符；null 清空；四项至少提供一项 |

#### 请求示例

```http
PATCH /api/v1/me/profile
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "nickname": "新落叶",
  "email": null,
  "avatar": null,
  "intro": null
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Me | 本接口业务结果 |
| `data.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.username` | string | 登录名 |
| `data.nickname` | string | 展示昵称 |
| `data.email` | string&#124;null | 未验证联系邮箱，可 null |
| `data.avatar` | string&#124;null | 外部头像 URL，可 null |
| `data.intro` | string&#124;null | 个人简介，可 null |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.hasPassword` | boolean | 是否设置有效本站密码，不返回哈希 |
| `data.lastLoginTime` | Time | 最近登录 UTC 时间，未登录可 null |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "userId": "12",
    "username": "leaf",
    "nickname": "新落叶",
    "email": null,
    "avatar": null,
    "intro": null,
    "status": 1,
    "hasPassword": true,
    "lastLoginTime": "2026-10-29T02:00:00.000Z",
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

至少一项；缺失保持，null 清空可空字段，可空字符串去首尾空白后为空也清空。username、status、角色、权限不可改。头像只接受外部 HTTP(S) 资源，不接受附件 API 路径及其等价绝对 URL（含路径编码、归一化后的形式）。

先检查权限及资源范围，再在同一事务内验证聚合版本并修改；缺失返回 428，不匹配返回 412，不自动覆盖。

---

<a id="api-10"></a>

### 2.3 设置或修改本站密码

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/me/password` |
| 接口描述 | 设置或修改本站密码 |
| Content-Type | application/json |
| 访问要求 | 登录且启用，仅操作本人；身份从会话读取 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `newPassword` | JSON Body | string | 是 | 同注册密码规则 |
| `currentPassword` | JSON Body | string | 条件必填 | 已有密码时必填且必须验证；纯第三方无密码账号必须省略 |

#### 请求示例

```http
PUT /api/v1/me/password
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "newPassword": "New-example-password-2026",
  "currentPassword": "Example-password-2026"
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 无业务返回值，固定 null |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

最近认证；有密码验证 currentPassword，无密码必须由最近第三方登录证明身份。成功撤销本人全部会话。

---

<a id="api-11"></a>

### 2.4 清除本站密码

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/me/password` |
| 接口描述 | 清除本站密码 |
| Content-Type | application/json |
| 访问要求 | 登录且启用，仅操作本人；身份从会话读取 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `currentPassword` | JSON Body | string | 是 | 验证现有密码；无密码账号返回 STATE_CONFLICT |

#### 请求示例

```http
DELETE /api/v1/me/password
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "currentPassword": "Example-password-2026"
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 无业务返回值，固定 null |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

最近认证并验证现密码；锁用户后确认至少保留一种可用第三方登录方式，成功撤销全部会话。

---

<a id="api-12"></a>

### 2.5 查询本人有效角色与权限

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/me/capabilities` |
| 接口描述 | 查询本人有效角色与权限 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用，仅操作本人；身份从会话读取 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
GET /api/v1/me/capabilities
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Capabilities | 本接口业务结果 |
| `data.roleCodes` | array | 当前有效角色码，字典序 |
| `data.permissionCodes` | array | 当前有效权限码，字典序 |
| `data.dataScopes` | object | 权限码到 ALL/SELF 的映射，不是单个用户全局范围 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "roleCodes": [
      "member"
    ],
    "permissionCodes": [
      "blog:article:like",
      "blog:comment:create",
      "blog:comment:deleteOwn",
      "blog:message:create",
      "blog:message:deleteOwn",
      "blog:message:updateOwn",
      "blog:rss:subscribe",
      "blog:rss:unsubscribe"
    ],
    "dataScopes": {
      "blog:article:like": "SELF",
      "blog:comment:create": "SELF",
      "blog:comment:deleteOwn": "SELF",
      "blog:message:create": "SELF",
      "blog:message:deleteOwn": "SELF",
      "blog:message:updateOwn": "SELF",
      "blog:rss:subscribe": "SELF",
      "blog:rss:unsubscribe": "SELF"
    }
  },
  "errors": []
}
```

#### 业务说明

普通 member 也可调用；仅返回当前有效授权，角色/权限字典序，数据范围按每个权限计算。

---

<a id="api-13"></a>

### 2.6 查询本人可访问菜单树

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/me/menus` |
| 接口描述 | 查询本人可访问菜单树 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用，仅操作本人；身份从会话读取 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
GET /api/v1/me/menus
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | MenuNode[] | 本接口业务结果 |
| `data[].menuId` | ID | 菜单 ID |
| `data[].parentId` | ID&#124;null | 父节点 ID，根为 null |
| `data[].menuCode` | string | 稳定菜单编码 |
| `data[].menuName` | string | 菜单名 |
| `data[].menuType` | string | D 目录、M 页面、B 按钮 |
| `data[].routeName` | string | 前端路由名，按钮为 null |
| `data[].path` | string | 内部前端路径，非 API 路径，按钮为 null |
| `data[].component` | string | 前端组件白名单键，按钮为 null |
| `data[].icon` | string | 图标白名单键，可 null |
| `data[].permissionId` | ID&#124;null | 权限 ID；目录节点为 null |
| `data[].sortOrder` | integer | int32 升序排序值 |
| `data[].isVisible` | integer | 0 隐藏、1 显示；隐藏不撤权 |
| `data[].keepAlive` | integer | 仅 M 页面可为 1 |
| `data[].status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data[].createTime` | Time | 创建 UTC 时间 |
| `data[].updateTime` | Time | 修改 UTC 时间 |
| `data[].permissionCode` | string&#124;null | 稳定接口权限码，目录节点为 null |
| `data[].children` | array | 子菜单，叶子 []，同 MenuNode 递归结构 |
| `data[].children[].menuId` | ID | 菜单 ID |
| `data[].children[].parentId` | ID | 父节点 ID，根为 null |
| `data[].children[].menuCode` | string | 稳定菜单编码 |
| `data[].children[].menuName` | string | 菜单名 |
| `data[].children[].menuType` | string | D 目录、M 页面、B 按钮 |
| `data[].children[].routeName` | string | 前端路由名，按钮为 null |
| `data[].children[].path` | string | 内部前端路径，非 API 路径，按钮为 null |
| `data[].children[].component` | string | 前端组件白名单键，按钮为 null |
| `data[].children[].icon` | string | 图标白名单键，可 null |
| `data[].children[].permissionId` | ID | 权限 ID；目录节点为 null |
| `data[].children[].sortOrder` | integer | int32 升序排序值 |
| `data[].children[].isVisible` | integer | 0 隐藏、1 显示；隐藏不撤权 |
| `data[].children[].keepAlive` | integer | 仅 M 页面可为 1 |
| `data[].children[].status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data[].children[].createTime` | Time | 创建 UTC 时间 |
| `data[].children[].updateTime` | Time | 修改 UTC 时间 |
| `data[].children[].permissionCode` | string | 稳定接口权限码，目录节点为 null |
| `data[].children[].children` | array | 子菜单，叶子 []，同 MenuNode 递归结构 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": [
    {
      "menuId": "100",
      "parentId": null,
      "menuCode": "content",
      "menuName": "内容管理",
      "menuType": "D",
      "routeName": "Content",
      "path": "/content",
      "component": "Layout",
      "icon": "document",
      "permissionId": null,
      "sortOrder": 10,
      "isVisible": 1,
      "keepAlive": 0,
      "status": 1,
      "createTime": "2026-10-29T02:00:00.000Z",
      "updateTime": "2026-10-29T02:00:00.000Z",
      "permissionCode": null,
      "children": [
        {
          "menuId": "110",
          "parentId": "100",
          "menuCode": "article",
          "menuName": "文章管理",
          "menuType": "M",
          "routeName": "Article",
          "path": "/content/articles",
          "component": "content/article/index",
          "icon": "document",
          "permissionId": "10",
          "sortOrder": 10,
          "isVisible": 1,
          "keepAlive": 0,
          "status": 1,
          "createTime": "2026-10-29T02:00:00.000Z",
          "updateTime": "2026-10-29T02:00:00.000Z",
          "permissionCode": "blog:article:query",
          "children": []
        }
      ]
    }
  ],
  "errors": []
}
```

#### 业务说明

无 admin:access 返回 []。菜单授权与权限求交集并检查祖先；返回隐藏但有权的路由和按钮，按钮不能生成前端路由。

---

<a id="api-14"></a>

### 2.7 查询本人第三方绑定

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/me/oauth-bindings` |
| 接口描述 | 查询本人第三方绑定 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用，仅操作本人；身份从会话读取 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
GET /api/v1/me/oauth-bindings
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | OAuthBinding[] | 本接口业务结果 |
| `data[].source` | string | 已配置且启用的平台标识 |
| `data[].createTime` | Time | 创建 UTC 时间 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": [
    {
      "source": "GITHUB",
      "createTime": "2026-10-29T02:00:00.000Z"
    }
  ],
  "errors": []
}
```

#### 业务说明

source 升序，只列当前配置完整且启用的平台绑定，仅返回平台和绑定时间，不暴露 uuid、绑定记录 ID 或第三方 Token。未启用或配置不完整的绑定记录仍保留在库中，不计为可用登录方式。

---

<a id="api-15"></a>

### 2.8 解绑本人第三方账号

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/me/oauth-bindings/{source}` |
| 接口描述 | 解绑本人第三方账号 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用，仅操作本人；身份从会话读取 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `source` | Path | string | 是 | 服务端已启用平台的大写标识；未知或未启用 404 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
DELETE /api/v1/me/oauth-bindings/GITHUB
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 无业务返回值，固定 null |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

最近认证；未绑定 404。锁同一用户，检查剩余有效密码或可用平台绑定；禁止解绑最后登录方式，成功撤销全部会话。

---


<a id="group-3"></a>

## 3. 用户管理

<a id="api-16"></a>

### 3.1 分页查询用户

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/users` |
| 接口描述 | 分页查询用户 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `sys:user:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `page` | Query | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | Query | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | Query | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `status` | Query | integer | 否 | 可选，0/1；含义见该业务状态约定 |
| `roleId` | Query | ID | 否 | 可选，按关联角色筛选 |

#### 请求示例

```http
GET /api/v1/admin/users?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<UserAdmin> | 本接口业务结果 |
| `data.page` | integer | 当前页码，数字 |
| `data.pageSize` | integer | 每页条数，数字 |
| `data.total` | string | 同过滤条件的总条数，字符串 |
| `data.totalPages` | string | 总页数，字符串 |
| `data.records` | array | 本页数据，无记录为 [] |
| `data.records[].userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.records[].username` | string | 登录名 |
| `data.records[].nickname` | string | 展示昵称 |
| `data.records[].email` | string&#124;null | 未验证联系邮箱，可 null |
| `data.records[].avatar` | string&#124;null | 外部头像 URL，可 null |
| `data.records[].intro` | string&#124;null | 个人简介，可 null |
| `data.records[].status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.records[].hasPassword` | boolean | 是否设置有效本站密码，不返回哈希 |
| `data.records[].lastLoginTime` | Time | 最近登录 UTC 时间，未登录可 null |
| `data.records[].createTime` | Time | 创建 UTC 时间 |
| `data.records[].updateTime` | Time | 修改 UTC 时间 |
| `data.records[].roleIds` | ID[] | 已分配角色 ID 集合，包含禁用角色 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "userId": "12",
        "username": "leaf",
        "nickname": "落叶",
        "email": null,
        "avatar": null,
        "intro": null,
        "status": 1,
        "hasPassword": true,
        "lastLoginTime": "2026-10-29T02:00:00.000Z",
        "createTime": "2026-10-29T02:00:00.000Z",
        "updateTime": "2026-10-29T02:00:00.000Z",
        "roleIds": [
          "3"
        ]
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

按 createTime DESC,userId DESC；全局操作。keyword 去首尾空白后按 username、nickname、email 字面包含匹配，遵循数据库字符排序规则，`%`、`_`、反斜杠均不作为 SQL 通配符。roleId 按完整关联过滤，包含已分配但禁用的角色；多个角色不会重复计算用户。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-17"></a>

### 3.2 查询用户详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/users/{userId}` |
| 接口描述 | 查询用户详情 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `sys:user:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `userId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
GET /api/v1/admin/users/12
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | UserAdmin | 本接口业务结果 |
| `data.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.username` | string | 登录名 |
| `data.nickname` | string | 展示昵称 |
| `data.email` | string&#124;null | 未验证联系邮箱，可 null |
| `data.avatar` | string&#124;null | 外部头像 URL，可 null |
| `data.intro` | string&#124;null | 个人简介，可 null |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.hasPassword` | boolean | 是否设置有效本站密码，不返回哈希 |
| `data.lastLoginTime` | Time | 最近登录 UTC 时间，未登录可 null |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `data.roleIds` | ID[] | 已分配角色 ID 集合，包含禁用角色 |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "userId": "12",
    "username": "leaf",
    "nickname": "落叶",
    "email": null,
    "avatar": null,
    "intro": null,
    "status": 1,
    "hasPassword": true,
    "lastLoginTime": "2026-10-29T02:00:00.000Z",
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z",
    "roleIds": [
      "3"
    ]
  },
  "errors": []
}
```

#### 业务说明

详情返回完整 roleIds（包含已分配但禁用的角色）和与本人资料共用的 X-Revision；不存在返回 404。密码、登录时间及审计时间不参与版本。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-18"></a>

### 3.3 创建用户

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/users` |
| 接口描述 | 创建用户 |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `sys:user:create`；按对应操作校验范围 |
| 成功状态 | 201 Created |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `username` | JSON Body | string | 是 | 3..64 位 ASCII 字母、数字或下划线；全库不区分大小写唯一 |
| `password` | JSON Body | string | 是 | 8..64 个字符且 UTF-8 ≤72 字节；不 trim、不截断 |
| `nickname` | JSON Body | string | 是 | 1..64 字符 |
| `email` | JSON Body | string&#124;null | 否 | 同本人资料，默认 null |
| `avatar` | JSON Body | string&#124;null | 否 | 仅外部 http(s)，默认 null |
| `intro` | JSON Body | string&#124;null | 否 | ≤500 字符，默认 null |
| `status` | JSON Body | integer | 否 | 0 禁用、1 启用，默认 1；不接受 roleIds |

#### 请求示例

```http
POST /api/v1/admin/users
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "username": "leaf",
  "password": "Example-password-2026",
  "nickname": "落叶",
  "email": null,
  "avatar": null,
  "intro": null,
  "status": 1
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | UserAdmin | 本接口业务结果 |
| `data.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.username` | string | 登录名 |
| `data.nickname` | string | 展示昵称 |
| `data.email` | string&#124;null | 未验证联系邮箱，可 null |
| `data.avatar` | string&#124;null | 外部头像 URL，可 null |
| `data.intro` | string&#124;null | 个人简介，可 null |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.hasPassword` | boolean | 是否设置有效本站密码，不返回哈希 |
| `data.lastLoginTime` | string&#124;null | 最近登录 UTC 时间，未登录可 null |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `data.roleIds` | ID[] | 已分配角色 ID 集合，包含禁用角色 |

#### 响应示例

```http
HTTP/1.1 201 Created
Content-Type: application/json; charset=UTF-8
Location: /api/v1/admin/users/12
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "userId": "12",
    "username": "leaf",
    "nickname": "落叶",
    "email": null,
    "avatar": null,
    "intro": null,
    "status": 1,
    "hasPassword": true,
    "lastLoginTime": null,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z",
    "roleIds": []
  },
  "errors": []
}
```

#### 业务说明

新建用户不分配角色；需要 assignRole 权限才能另行分配，不沿用公开注册的 member 默认。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-19"></a>

### 3.4 修改用户资料及状态

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/users/{userId}` |
| 接口描述 | 修改用户资料及状态 |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `sys:user:update`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `userId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |
| `nickname` | JSON Body | string | 是 | 1..64 字符 |
| `email` | JSON Body | string&#124;null | 是，可 null | 合法邮箱，≤254 字符，唯一 |
| `avatar` | JSON Body | string&#124;null | 是，可 null | 仅外部 http(s) URL |
| `intro` | JSON Body | string&#124;null | 是，可 null | ≤500 字符 |
| `status` | JSON Body | integer | 是 | 0/1；禁用撤销全部会话 |

#### 请求示例

```http
PUT /api/v1/admin/users/12
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "nickname": "落叶",
  "email": null,
  "avatar": null,
  "intro": null,
  "status": 1
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | UserAdmin | 本接口业务结果 |
| `data.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.username` | string | 登录名 |
| `data.nickname` | string | 展示昵称 |
| `data.email` | string&#124;null | 未验证联系邮箱，可 null |
| `data.avatar` | string&#124;null | 外部头像 URL，可 null |
| `data.intro` | string&#124;null | 个人简介，可 null |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.hasPassword` | boolean | 是否设置有效本站密码，不返回哈希 |
| `data.lastLoginTime` | Time | 最近登录 UTC 时间，未登录可 null |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `data.roleIds` | ID[] | 已分配角色 ID 集合，包含禁用角色 |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "userId": "12",
    "username": "leaf",
    "nickname": "落叶",
    "email": null,
    "avatar": null,
    "intro": null,
    "status": 1,
    "hasPassword": true,
    "lastLoginTime": "2026-10-29T02:00:00.000Z",
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z",
    "roleIds": [
      "3"
    ]
  },
  "errors": []
}
```

#### 业务说明

不能修改 username 或密码；禁用撤销全部会话，重启用不恢复旧 Token。

先检查权限及资源范围，再在同一事务内验证聚合版本并修改；缺失返回 428，不匹配返回 412，不自动覆盖。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-20"></a>

### 3.5 分配用户角色

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/users/{userId}/roles` |
| 接口描述 | 分配用户角色 |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `sys:user:assignRole`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `userId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |
| `roleIds` | JSON Body | ID[] | 是 | 0..100 个，不重复、存在且启用；[] 清空；整体替换 |

#### 请求示例

```http
PUT /api/v1/admin/users/12/roles
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "roleIds": [
    "3"
  ]
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | UserAdmin | 本接口业务结果 |
| `data.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.username` | string | 登录名 |
| `data.nickname` | string | 展示昵称 |
| `data.email` | string&#124;null | 未验证联系邮箱，可 null |
| `data.avatar` | string&#124;null | 外部头像 URL，可 null |
| `data.intro` | string&#124;null | 个人简介，可 null |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.hasPassword` | boolean | 是否设置有效本站密码，不返回哈希 |
| `data.lastLoginTime` | Time | 最近登录 UTC 时间，未登录可 null |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `data.roleIds` | ID[] | 已分配角色 ID 集合，包含禁用角色 |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "userId": "12",
    "username": "leaf",
    "nickname": "落叶",
    "email": null,
    "avatar": null,
    "intro": null,
    "status": 1,
    "hasPassword": true,
    "lastLoginTime": "2026-10-29T02:00:00.000Z",
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z",
    "roleIds": [
      "3"
    ]
  },
  "errors": []
}
```

#### 业务说明

整体替换，不接受隐藏的增量追加；If-Revision 来自该用户详情，受最后管理员保护。集合中的角色必须全部存在且启用；任一项无效整笔返回 400。角色实际变化后撤销目标全部会话，Redis 失败则回滚关联替换并返回 503；同一集合的顺序不同不算变更，保持版本和会话。

先检查权限及资源范围，再在同一事务内验证聚合版本并修改；缺失返回 428，不匹配返回 412，不自动覆盖。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---


<a id="group-4"></a>

## 4. 角色与授权管理

<a id="api-21"></a>

### 4.1 分页查询角色

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/roles` |
| 接口描述 | 分页查询角色 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `sys:role:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `page` | Query | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | Query | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | Query | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `status` | Query | integer | 否 | 可选，0/1；含义见该业务状态约定 |

#### 请求示例

```http
GET /api/v1/admin/roles?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<Role> | 本接口业务结果 |
| `data.page` | integer | 当前页码，数字 |
| `data.pageSize` | integer | 每页条数，数字 |
| `data.total` | string | 同过滤条件的总条数，字符串 |
| `data.totalPages` | string | 总页数，字符串 |
| `data.records` | array | 本页数据，无记录为 [] |
| `data.records[].roleId` | ID | 角色 ID |
| `data.records[].roleCode` | string | 稳定角色码，不带 ROLE_ 前缀 |
| `data.records[].roleName` | string | 角色名称 |
| `data.records[].description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
| `data.records[].dataScope` | string | ALL 或 SELF；按当前操作权限计算 |
| `data.records[].status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.records[].createTime` | Time | 创建 UTC 时间 |
| `data.records[].updateTime` | Time | 修改 UTC 时间 |
| `data.records[].permissionIds` | ID[] | 授权的权限 ID，升序，含禁用项 |
| `data.records[].menuIds` | ID[] | 授权的菜单 ID，升序，含禁用项 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "roleId": "4",
        "roleCode": "editor",
        "roleName": "内容编辑",
        "description": null,
        "dataScope": "SELF",
        "status": 1,
        "createTime": "2026-10-29T02:00:00.000Z",
        "updateTime": "2026-10-29T02:00:00.000Z",
        "permissionIds": [],
        "menuIds": []
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

按 roleId ASC。keyword 去首尾空白后按 roleCode、roleName 字面包含匹配，遵循对应数据库列的字符排序规则；`%`、`_`、反斜杠均不作为 SQL 通配符。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-22"></a>

### 4.2 查询角色详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/roles/{roleId}` |
| 接口描述 | 查询角色详情 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `sys:role:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `roleId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
GET /api/v1/admin/roles/4
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Role | 本接口业务结果 |
| `data.roleId` | ID | 角色 ID |
| `data.roleCode` | string | 稳定角色码，不带 ROLE_ 前缀 |
| `data.roleName` | string | 角色名称 |
| `data.description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
| `data.dataScope` | string | ALL 或 SELF；按当前操作权限计算 |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `data.permissionIds` | ID[] | 授权的权限 ID，升序，含禁用项 |
| `data.menuIds` | ID[] | 授权的菜单 ID，升序，含禁用项 |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "roleId": "4",
    "roleCode": "editor",
    "roleName": "内容编辑",
    "description": null,
    "dataScope": "SELF",
    "status": 1,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z",
    "permissionIds": [],
    "menuIds": []
  },
  "errors": []
}
```

#### 业务说明

sys:role:query

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-23"></a>

### 4.3 创建角色

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/roles` |
| 接口描述 | 创建角色 |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `sys:role:create`；按对应操作校验范围 |
| 成功状态 | 201 Created |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `roleCode` | JSON Body | string | 是 | [a-z][a-z0-9_]{0,63}，唯一且创建后不可变 |
| `roleName` | JSON Body | string | 是 | 1..64 字符 |
| `description` | JSON Body | string&#124;null | 否 | ≤255 字符，默认 null |
| `dataScope` | JSON Body | string | 否 | ALL/SELF，默认 SELF |
| `status` | JSON Body | integer | 否 | 0/1，默认 1 |

#### 请求示例

```http
POST /api/v1/admin/roles
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "roleCode": "editor",
  "roleName": "内容编辑",
  "description": null,
  "dataScope": "SELF",
  "status": 1
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Role | 本接口业务结果 |
| `data.roleId` | ID | 角色 ID |
| `data.roleCode` | string | 稳定角色码，不带 ROLE_ 前缀 |
| `data.roleName` | string | 角色名称 |
| `data.description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
| `data.dataScope` | string | ALL 或 SELF；按当前操作权限计算 |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `data.permissionIds` | ID[] | 授权的权限 ID，升序，含禁用项 |
| `data.menuIds` | ID[] | 授权的菜单 ID，升序，含禁用项 |

#### 响应示例

```http
HTTP/1.1 201 Created
Content-Type: application/json; charset=UTF-8
Location: /api/v1/admin/roles/4
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "roleId": "4",
    "roleCode": "editor",
    "roleName": "内容编辑",
    "description": null,
    "dataScope": "SELF",
    "status": 1,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z",
    "permissionIds": [],
    "menuIds": []
  },
  "errors": []
}
```

#### 业务说明

初始无菜单、无权限；分配角色不隐式授予未配置的接口权限。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-24"></a>

### 4.4 修改角色

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/roles/{roleId}` |
| 接口描述 | 修改角色 |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `sys:role:update`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `roleId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |
| `roleName` | JSON Body | string | 是 | 1..64 字符 |
| `description` | JSON Body | string&#124;null | 是，可 null | ≤255 字符，默认 null |
| `dataScope` | JSON Body | string | 是 | ALL/SELF，默认 SELF |
| `status` | JSON Body | integer | 是 | 0/1，默认 1 |

#### 请求示例

```http
PUT /api/v1/admin/roles/4
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "roleName": "内容编辑",
  "description": null,
  "dataScope": "SELF",
  "status": 1
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Role | 本接口业务结果 |
| `data.roleId` | ID | 角色 ID |
| `data.roleCode` | string | 稳定角色码，不带 ROLE_ 前缀 |
| `data.roleName` | string | 角色名称 |
| `data.description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
| `data.dataScope` | string | ALL 或 SELF；按当前操作权限计算 |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `data.permissionIds` | ID[] | 授权的权限 ID，升序，含禁用项 |
| `data.menuIds` | ID[] | 授权的菜单 ID，升序，含禁用项 |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "roleId": "4",
    "roleCode": "editor",
    "roleName": "内容编辑",
    "description": null,
    "dataScope": "SELF",
    "status": 1,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z",
    "permissionIds": [],
    "menuIds": []
  },
  "errors": []
}
```

#### 业务说明

roleCode 不可改；dataScope 或 status 实际变化时撤销全部关联用户的所有会话（包含禁用用户），角色禁用后保留权限与菜单关联，重新启用不恢复旧 Token。仅修改 roleName/description 不撤销会话。管理员角色仍须满足最后有效管理员保护，不能缩为 SELF 或停用。

先检查权限及资源范围，再在同一事务内验证聚合版本并修改；缺失返回 428，不匹配返回 412，不自动覆盖。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-25"></a>

### 4.5 删除角色

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/roles/{roleId}` |
| 接口描述 | 删除角色 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `sys:role:delete`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `roleId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |

#### 请求示例

```http
DELETE /api/v1/admin/roles/4
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 无业务返回值，固定 null |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

内置 admin/author/member 禁止删除，返回 409/STATE_CONFLICT；自定义角色即使已分配给用户仍允许删除，级联清理 sys_user_role、sys_role_permission、sys_role_menu 三类关联，保留用户及权限/菜单字典，撤销全部关联用户的所有会话。关联删除使受影响用户的聚合版本变化。Redis 失败则整笔回滚并返回 503。

先检查权限及资源范围，再在同一事务内验证聚合版本并修改；缺失返回 428，不匹配返回 412，不自动覆盖。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-26"></a>

### 4.6 配置角色权限与菜单

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/roles/{roleId}/grants` |
| 接口描述 | 配置角色权限与菜单 |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `sys:role:grant`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `roleId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |
| `permissionIds` | JSON Body | ID[] | 是 | ≤1000 个，不重复；整体替换，权限存在且启用 |
| `menuIds` | JSON Body | ID[] | 是 | ≤1000 个，不重复；存在且启用，自动补齐祖先；页面/按钮权限须显式列在 permissionIds |

#### 请求示例

```http
PUT /api/v1/admin/roles/4/grants
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "permissionIds": [
    "1",
    "10",
    "11"
  ],
  "menuIds": [
    "110",
    "1011"
  ]
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Role | 本接口业务结果 |
| `data.roleId` | ID | 角色 ID |
| `data.roleCode` | string | 稳定角色码，不带 ROLE_ 前缀 |
| `data.roleName` | string | 角色名称 |
| `data.description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
| `data.dataScope` | string | ALL 或 SELF；按当前操作权限计算 |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `data.permissionIds` | ID[] | 授权的权限 ID，升序，含禁用项 |
| `data.menuIds` | ID[] | 授权的菜单 ID，升序，含禁用项 |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "roleId": "4",
    "roleCode": "editor",
    "roleName": "内容编辑",
    "description": null,
    "dataScope": "SELF",
    "status": 1,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z",
    "permissionIds": [
      "1",
      "10",
      "11"
    ],
    "menuIds": [
      "100",
      "110",
      "1011"
    ]
  },
  "errors": []
}
```

#### 业务说明

两类授权同事务完整替换；menuIds 补齐祖先，最终集合升序返回。选择的节点及祖先均须存在、启用且符合父类型和无环约束；页面/按钮所需权限必须显式列入 permissionIds 并启用，不静默扩大授权。permissionIds 可包含无菜单的独立接口权限，[] 可清空；任一无效整笔返回 400/VALIDATION_ERROR。最终集合实际变化后撤销全部关联用户的所有会话，Redis 失败则回滚两个集合并返回 503；输入顺序或祖先显式/自动补齐不同但最终集合相同，保留版本及会话。admin 授权受最后有效管理员保护。

先检查权限及资源范围，再在同一事务内验证聚合版本并修改；缺失返回 428，不匹配返回 412，不自动覆盖。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---


<a id="group-5"></a>

## 5. 权限字典管理

<a id="api-27"></a>

### 5.1 分页查询权限字典

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/permissions` |
| 接口描述 | 分页查询权限字典 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `sys:permission:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `page` | Query | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | Query | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | Query | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `module` | Query | string | 否 | 可选，[a-z][a-z0-9_]{0,31} |
| `status` | Query | integer | 否 | 可选，0/1；含义见该业务状态约定 |

#### 请求示例

```http
GET /api/v1/admin/permissions?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<Permission> | 本接口业务结果 |
| `data.page` | integer | 当前页码，数字 |
| `data.pageSize` | integer | 每页条数，数字 |
| `data.total` | string | 同过滤条件的总条数，字符串 |
| `data.totalPages` | string | 总页数，字符串 |
| `data.records` | array | 本页数据，无记录为 [] |
| `data.records[].permissionId` | ID | 权限 ID |
| `data.records[].permissionCode` | string | 稳定接口权限码 |
| `data.records[].permissionName` | string | 权限显示名 |
| `data.records[].module` | string | 权限模块 |
| `data.records[].description` | string&#124;null | 说明，可 null；空白归一为 null |
| `data.records[].status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.records[].createTime` | Time | 创建 UTC 时间 |
| `data.records[].updateTime` | Time | 修改 UTC 时间 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "permissionId": "114",
        "permissionCode": "blog:article:export",
        "permissionName": "导出文章",
        "module": "blog",
        "description": null,
        "status": 1,
        "createTime": "2026-10-29T02:00:00.000Z",
        "updateTime": "2026-10-29T02:00:00.000Z"
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

按 permissionId ASC。keyword 去首尾空白后按 permissionCode、permissionName 字面包含匹配，遵循对应列的数据库排序规则；`%`、`_`、反斜杠均不作为 SQL 通配符。module 精确匹配，所有过滤 AND 组合；列表与总数使用相同条件，包含禁用项。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-28"></a>

### 5.2 查询权限详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/permissions/{permissionId}` |
| 接口描述 | 查询权限详情 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `sys:permission:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `permissionId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
GET /api/v1/admin/permissions/114
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Permission | 本接口业务结果 |
| `data.permissionId` | ID | 权限 ID |
| `data.permissionCode` | string | 稳定接口权限码 |
| `data.permissionName` | string | 权限显示名 |
| `data.module` | string | 权限模块 |
| `data.description` | string&#124;null | 说明，可 null；空白归一为 null |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "permissionId": "114",
    "permissionCode": "blog:article:export",
    "permissionName": "导出文章",
    "module": "blog",
    "description": null,
    "status": 1,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

sys:permission:query

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-29"></a>

### 5.3 创建权限字典项

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/permissions` |
| 接口描述 | 创建权限字典项 |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `sys:permission:create`；按对应操作校验范围 |
| 成功状态 | 201 Created |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `permissionCode` | JSON Body | string | 是 | ≤100 字符；模块:资源:动作，允许 admin:access 例外，创建后不可变 |
| `permissionName` | JSON Body | string | 是 | 1..64 字符 |
| `module` | JSON Body | string | 是 | [a-z][a-z0-9_]{0,31} |
| `description` | JSON Body | string&#124;null | 否 | ≤255 字符，默认 null |
| `status` | JSON Body | integer | 否 | 0/1，默认 1 |

#### 请求示例

```http
POST /api/v1/admin/permissions
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "permissionCode": "blog:article:export",
  "permissionName": "导出文章",
  "module": "blog",
  "description": null,
  "status": 1
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Permission | 本接口业务结果 |
| `data.permissionId` | ID | 权限 ID |
| `data.permissionCode` | string | 稳定接口权限码 |
| `data.permissionName` | string | 权限显示名 |
| `data.module` | string | 权限模块 |
| `data.description` | string&#124;null | 说明，可 null；空白归一为 null |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |

#### 响应示例

```http
HTTP/1.1 201 Created
Content-Type: application/json; charset=UTF-8
Location: /api/v1/admin/permissions/114
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "permissionId": "114",
    "permissionCode": "blog:article:export",
    "permissionName": "导出文章",
    "module": "blog",
    "description": null,
    "status": 1,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

新增字典不会自动建立路由能力，也不自动授予 admin。示例 export 是自定义字典项，不表示已有导出 API。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-30"></a>

### 5.4 修改权限字典项

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/permissions/{permissionId}` |
| 接口描述 | 修改权限字典项 |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `sys:permission:update`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `permissionId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |
| `permissionName` | JSON Body | string | 是 | 1..64 字符 |
| `module` | JSON Body | string | 是 | [a-z][a-z0-9_]{0,31} |
| `description` | JSON Body | string&#124;null | 是，可 null | ≤255 字符，默认 null |
| `status` | JSON Body | integer | 是 | 0/1，默认 1 |

#### 请求示例

```http
PUT /api/v1/admin/permissions/114
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "permissionName": "导出文章",
  "module": "blog",
  "description": null,
  "status": 1
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Permission | 本接口业务结果 |
| `data.permissionId` | ID | 权限 ID |
| `data.permissionCode` | string | 稳定接口权限码 |
| `data.permissionName` | string | 权限显示名 |
| `data.module` | string | 权限模块 |
| `data.description` | string&#124;null | 说明，可 null；空白归一为 null |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "permissionId": "114",
    "permissionCode": "blog:article:export",
    "permissionName": "导出文章",
    "module": "blog",
    "description": null,
    "status": 1,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

permissionCode 创建后不可改，PUT 不接受该字段。仅名称、模块或说明变化不撤销会话；status 实际变化（停用或重新启用）撤销经任意角色关联该权限的全部用户的所有会话，包含禁用用户、禁用角色的关联，同一用户去重。停用保留 sys_role_permission 和菜单门禁引用；实时授权和菜单查询排除禁用权限，重新启用不恢复旧 Token。同值 PUT 仍须校验版本，业务值相同可保留版本和会话。

写事务复用固定 sys_role(role_id=1) 保护锁，再按 ID 升序锁操作者及全部受影响用户，最后锁目标权限；锁内复核原 Token、当前启用身份及双权限，然后校验版本。固定管理员保护权限停用受 LAST_ADMIN 约束。会话撤销成功后才提交数据库，Redis 失败返回 503 并回滚资料、状态；授权和菜单每次实时读库，无旧授权缓存。

先检查权限及资源范围，再在同一事务内验证聚合版本并修改；缺失返回 428，不匹配返回 412，不自动覆盖。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-31"></a>

### 5.5 删除权限字典项

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/permissions/{permissionId}` |
| 接口描述 | 删除权限字典项 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `sys:permission:delete`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `permissionId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |

#### 请求示例

```http
DELETE /api/v1/admin/permissions/114
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 无业务返回值，固定 null |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

固定后端权限（当前 AuthorizationProvider.ADMIN_PERMISSIONS 明确列出的后台入口、用户、角色、菜单及权限管理权限）禁止删除，返回 409/STATE_CONFLICT。此白名单不随 SQL 新增字典项自动扩大。任何菜单引用（含禁用或隐藏节点）均返回 409/RESOURCE_IN_USE，不能静默移除门禁。

其他字典项可物理删除，已授权给角色不是额外删除限制；角色权限关联由现有外键 CASCADE 清理，菜单外键保持 RESTRICT。采用与修改相同的保护锁、升序用户锁和目标权限锁，先锁内复核身份、双权限及版本，再检查删除限制。删除前枚举并锁定全部关联用户，撤销所有会话后提交；Redis 失败回滚字典及角色关联，返回 503。数据库最终提交失败时会话可能已撤销，MySQL 与 Redis 无共同事务。

先检查权限及资源范围，再在同一事务内验证聚合版本并修改；缺失返回 428，不匹配返回 412，不自动覆盖。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---


<a id="group-6"></a>

## 6. 菜单管理

<a id="api-32"></a>

### 6.1 查询后台完整菜单树

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/menus` |
| 接口描述 | 查询后台完整菜单树 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `sys:menu:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
GET /api/v1/admin/menus
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | MenuNode[] | 本接口业务结果 |
| `data[].menuId` | ID | 菜单 ID |
| `data[].parentId` | ID&#124;null | 父节点 ID，根为 null |
| `data[].menuCode` | string | 稳定菜单编码 |
| `data[].menuName` | string | 菜单名 |
| `data[].menuType` | string | D 目录、M 页面、B 按钮 |
| `data[].routeName` | string | 前端路由名，按钮为 null |
| `data[].path` | string | 内部前端路径，非 API 路径，按钮为 null |
| `data[].component` | string | 前端组件白名单键，按钮为 null |
| `data[].icon` | string | 图标白名单键，可 null |
| `data[].permissionId` | ID&#124;null | 权限 ID；目录节点为 null |
| `data[].sortOrder` | integer | int32 升序排序值 |
| `data[].isVisible` | integer | 0 隐藏、1 显示；隐藏不撤权 |
| `data[].keepAlive` | integer | 仅 M 页面可为 1 |
| `data[].status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data[].createTime` | Time | 创建 UTC 时间 |
| `data[].updateTime` | Time | 修改 UTC 时间 |
| `data[].permissionCode` | string&#124;null | 稳定接口权限码，目录节点为 null |
| `data[].children` | array | 子菜单，叶子 []，同 MenuNode 递归结构 |
| `data[].children[].menuId` | ID | 菜单 ID |
| `data[].children[].parentId` | ID | 父节点 ID，根为 null |
| `data[].children[].menuCode` | string | 稳定菜单编码 |
| `data[].children[].menuName` | string | 菜单名 |
| `data[].children[].menuType` | string | D 目录、M 页面、B 按钮 |
| `data[].children[].routeName` | string | 前端路由名，按钮为 null |
| `data[].children[].path` | string | 内部前端路径，非 API 路径，按钮为 null |
| `data[].children[].component` | string | 前端组件白名单键，按钮为 null |
| `data[].children[].icon` | string | 图标白名单键，可 null |
| `data[].children[].permissionId` | ID | 权限 ID；目录节点为 null |
| `data[].children[].sortOrder` | integer | int32 升序排序值 |
| `data[].children[].isVisible` | integer | 0 隐藏、1 显示；隐藏不撤权 |
| `data[].children[].keepAlive` | integer | 仅 M 页面可为 1 |
| `data[].children[].status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data[].children[].createTime` | Time | 创建 UTC 时间 |
| `data[].children[].updateTime` | Time | 修改 UTC 时间 |
| `data[].children[].permissionCode` | string | 稳定接口权限码，目录节点为 null |
| `data[].children[].children` | array | 子菜单，叶子 []，同 MenuNode 递归结构 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": [
    {
      "menuId": "100",
      "parentId": null,
      "menuCode": "content",
      "menuName": "内容管理",
      "menuType": "D",
      "routeName": "Content",
      "path": "/content",
      "component": "Layout",
      "icon": "document",
      "permissionId": null,
      "sortOrder": 10,
      "isVisible": 1,
      "keepAlive": 0,
      "status": 1,
      "createTime": "2026-10-29T02:00:00.000Z",
      "updateTime": "2026-10-29T02:00:00.000Z",
      "permissionCode": null,
      "children": [
        {
          "menuId": "110",
          "parentId": "100",
          "menuCode": "article",
          "menuName": "文章管理",
          "menuType": "M",
          "routeName": "Article",
          "path": "/content/articles",
          "component": "content/article/index",
          "icon": "document",
          "permissionId": "10",
          "sortOrder": 10,
          "isVisible": 1,
          "keepAlive": 0,
          "status": 1,
          "createTime": "2026-10-29T02:00:00.000Z",
          "updateTime": "2026-10-29T02:00:00.000Z",
          "permissionCode": "blog:article:query",
          "children": []
        }
      ]
    }
  ],
  "errors": []
}
```

#### 业务说明

含禁用、隐藏及按钮节点，按 sortOrder ASC,menuId ASC 逐层排序。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-33"></a>

### 6.2 查询菜单详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/menus/{menuId}` |
| 接口描述 | 查询菜单详情 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `sys:menu:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 返回 X-Revision，读取不要求 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `menuId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
GET /api/v1/admin/menus/110
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Menu | 本接口业务结果 |
| `data.menuId` | ID | 菜单 ID |
| `data.parentId` | ID | 父节点 ID，根为 null |
| `data.menuCode` | string | 稳定菜单编码 |
| `data.menuName` | string | 菜单名 |
| `data.menuType` | string | D 目录、M 页面、B 按钮 |
| `data.routeName` | string | 前端路由名，按钮为 null |
| `data.path` | string | 内部前端路径，非 API 路径，按钮为 null |
| `data.component` | string | 前端组件白名单键，按钮为 null |
| `data.icon` | string | 图标白名单键，可 null |
| `data.permissionId` | ID | 权限 ID；目录节点为 null |
| `data.sortOrder` | integer | int32 升序排序值 |
| `data.isVisible` | integer | 0 隐藏、1 显示；隐藏不撤权 |
| `data.keepAlive` | integer | 仅 M 页面可为 1 |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "menuId": "110",
    "parentId": "100",
    "menuCode": "article",
    "menuName": "文章管理",
    "menuType": "M",
    "routeName": "Article",
    "path": "/content/articles",
    "component": "content/article/index",
    "icon": "document",
    "permissionId": "10",
    "sortOrder": 10,
    "isVisible": 1,
    "keepAlive": 0,
    "status": 1,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

返回完整持久化菜单字段和 X-Revision，不按启用、可见性或本人菜单授权过滤。摘要覆盖 MenuWrite 全部业务字段（含稳定 menuCode），不包含审计时间、子节点及 role_menu 集合。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-34"></a>

### 6.3 创建菜单节点

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/menus` |
| 接口描述 | 创建菜单节点 |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `sys:menu:create`；按对应操作校验范围 |
| 成功状态 | 201 Created |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `parentId` | JSON Body | ID&#124;null | 是，可 null | 根节点 null；D/M 父为 D 或 null；B 父必须为 M |
| `menuCode` | JSON Body | string | 是 | [A-Za-z0-9:_-]+，≤64，区分大小写唯一、创建后不可变 |
| `menuName` | JSON Body | string | 是 | 1..64 字符 |
| `menuType` | JSON Body | string | 是 | D 目录、M 页面、B 按钮 |
| `routeName` | JSON Body | string&#124;null | 条件必填 | D/M 非空且唯一，[A-Za-z][A-Za-z0-9_]* ≤64；B 必须 null |
| `path` | JSON Body | string&#124;null | 条件必填 | D/M 以 / 起始的唯一内部路径 ≤255；无 ..、反斜线、协议、查询串、fragment；B 为 null |
| `component` | JSON Body | string&#124;null | 条件必填 | D/M 为组件注册白名单键 ≤255，B 为 null |
| `icon` | JSON Body | string&#124;null | 否 | 图标白名单键 ≤64，默认 null |
| `permissionId` | JSON Body | ID&#124;null | 条件必填 | D 必须 null；M/B 指向已存在权限 |
| `sortOrder` | JSON Body | integer | 否 | int32，默认 0，升序 |
| `isVisible` | JSON Body | integer | 否 | 0/1，默认 1；B 默认且必须 0 |
| `keepAlive` | JSON Body | integer | 否 | 默认 0；仅 M 可为 1 |
| `status` | JSON Body | integer | 否 | 0/1，默认 1 |

#### 请求示例

```http
POST /api/v1/admin/menus
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "parentId": "100",
  "menuCode": "articleExtra",
  "menuName": "文章扩展页",
  "menuType": "M",
  "routeName": "ArticleExtra",
  "path": "/content/articles-extra",
  "component": "content/article/index",
  "icon": "document",
  "permissionId": "10",
  "sortOrder": 20,
  "isVisible": 1,
  "keepAlive": 0,
  "status": 1
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Menu | 本接口业务结果 |
| `data.menuId` | ID | 菜单 ID |
| `data.parentId` | ID | 父节点 ID，根为 null |
| `data.menuCode` | string | 稳定菜单编码 |
| `data.menuName` | string | 菜单名 |
| `data.menuType` | string | D 目录、M 页面、B 按钮 |
| `data.routeName` | string | 前端路由名，按钮为 null |
| `data.path` | string | 内部前端路径，非 API 路径，按钮为 null |
| `data.component` | string | 前端组件白名单键，按钮为 null |
| `data.icon` | string | 图标白名单键，可 null |
| `data.permissionId` | ID | 权限 ID；目录节点为 null |
| `data.sortOrder` | integer | int32 升序排序值 |
| `data.isVisible` | integer | 0 隐藏、1 显示；隐藏不撤权 |
| `data.keepAlive` | integer | 仅 M 页面可为 1 |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |

#### 响应示例

```http
HTTP/1.1 201 Created
Content-Type: application/json; charset=UTF-8
Location: /api/v1/admin/menus/10
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "menuId": "300",
    "parentId": "100",
    "menuCode": "articleExtra",
    "menuName": "文章扩展页",
    "menuType": "M",
    "routeName": "ArticleExtra",
    "path": "/content/articles-extra",
    "component": "content/article/index",
    "icon": "document",
    "permissionId": "10",
    "sortOrder": 20,
    "isVisible": 1,
    "keepAlive": 0,
    "status": 1,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

在同一 READ COMMITTED 事务先锁固定菜单结构检查点（sys_role.role_id=1），再锁操作者，锁内复核原 Token、启用身份及双权限后，验证父类型、完整祖先链、组件/图标白名单及页面/按钮权限引用。M/B 仅要求门禁权限存在，允许引用停用权限，此时本人树不返回该节点；不隐式授予任何角色。允许在禁用父节点下维护结构，本人树仍截断禁用分支。后端菜单 path 不作为 API 鉴权规则。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-35"></a>

### 6.4 修改菜单节点

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/menus/{menuId}` |
| 接口描述 | 修改菜单节点 |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `sys:menu:update`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `menuId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |
| `parentId` | JSON Body | ID&#124;null | 是，可 null | 根节点 null；D/M 父为 D 或 null；B 父必须为 M |
| `menuName` | JSON Body | string | 是 | 1..64 字符 |
| `menuType` | JSON Body | string | 是 | D 目录、M 页面、B 按钮 |
| `routeName` | JSON Body | string&#124;null | 是，可 null | D/M 非空且唯一，[A-Za-z][A-Za-z0-9_]* ≤64；B 必须 null |
| `path` | JSON Body | string&#124;null | 是，可 null | D/M 以 / 起始的唯一内部路径 ≤255；无 ..、反斜线、协议、查询串、fragment；B 为 null |
| `component` | JSON Body | string&#124;null | 是，可 null | D/M 为组件注册白名单键 ≤255，B 为 null |
| `icon` | JSON Body | string&#124;null | 是，可 null | 图标白名单键 ≤64，默认 null |
| `permissionId` | JSON Body | ID&#124;null | 是，可 null | D 必须 null；M/B 指向已存在权限 |
| `sortOrder` | JSON Body | integer | 是 | int32，默认 0，升序 |
| `isVisible` | JSON Body | integer | 是 | 0/1，默认 1；B 默认且必须 0 |
| `keepAlive` | JSON Body | integer | 是 | 默认 0；仅 M 可为 1 |
| `status` | JSON Body | integer | 是 | 0/1，默认 1 |

#### 请求示例

```http
PUT /api/v1/admin/menus/110
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "parentId": "100",
  "menuName": "文章扩展页",
  "menuType": "M",
  "routeName": "ArticleExtra",
  "path": "/content/articles-extra",
  "component": "content/article/index",
  "icon": "document",
  "permissionId": "10",
  "sortOrder": 20,
  "isVisible": 1,
  "keepAlive": 0,
  "status": 1
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Menu | 本接口业务结果 |
| `data.menuId` | ID | 菜单 ID |
| `data.parentId` | ID | 父节点 ID，根为 null |
| `data.menuCode` | string | 稳定菜单编码 |
| `data.menuName` | string | 菜单名 |
| `data.menuType` | string | D 目录、M 页面、B 按钮 |
| `data.routeName` | string | 前端路由名，按钮为 null |
| `data.path` | string | 内部前端路径，非 API 路径，按钮为 null |
| `data.component` | string | 前端组件白名单键，按钮为 null |
| `data.icon` | string | 图标白名单键，可 null |
| `data.permissionId` | ID | 权限 ID；目录节点为 null |
| `data.sortOrder` | integer | int32 升序排序值 |
| `data.isVisible` | integer | 0 隐藏、1 显示；隐藏不撤权 |
| `data.keepAlive` | integer | 仅 M 页面可为 1 |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "menuId": "110",
    "parentId": "100",
    "menuCode": "article",
    "menuName": "文章扩展页",
    "menuType": "M",
    "routeName": "ArticleExtra",
    "path": "/content/articles-extra",
    "component": "content/article/index",
    "icon": "document",
    "permissionId": "10",
    "sortOrder": 20,
    "isVisible": 1,
    "keepAlive": 0,
    "status": 1,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

先锁固定菜单结构检查点，再按 ID 升序锁操作者及受影响分支关联用户，最后锁目标节点并复核身份、双权限与版本。检查自引用、移入后代、完整祖先链和类型变化对全部后代的约束，禁止并发移动产生环；menuCode 不可改。字段与门禁校验同创建，NULL 必须实际清空。停用、隐藏、移动及修改门禁均保留角色关联及会话；菜单实时读库，无旧菜单缓存。移动不自动授予新祖先，修改门禁不自动授予新权限；需显式重新保存角色 grants。

先检查权限及资源范围，再在同一事务内验证聚合版本并修改；缺失返回 428，不匹配返回 412，不自动覆盖。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-36"></a>

### 6.5 删除菜单节点

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/menus/{menuId}` |
| 接口描述 | 删除菜单节点 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `sys:menu:delete`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `menuId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |

#### 请求示例

```http
DELETE /api/v1/admin/menus/110
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 无业务返回值，固定 null |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

沿用修改的结构锁、升序用户锁和目标节点锁，锁内复核原 Token、身份、双权限及版本。有任意状态子节点返回 409/RESOURCE_IN_USE；叶子删除级联 role_menu，使关联角色聚合版本变化，独立 role_permission、用户及会话均保留。菜单和权限实时读库；Redis 身份校验不可用返回 503，不能跳过身份校验提交写入。

先检查权限及资源范围，再在同一事务内验证聚合版本并修改；缺失返回 428，不匹配返回 412，不自动覆盖。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---


<a id="group-7"></a>

## 7. 文章与归档

<a id="api-37"></a>

### 7.1 分页查询公开文章

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/articles` |
| 接口描述 | 分页查询公开文章 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；涉及文章时必须满足 PUBLIC 条件 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `page` | Query | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | Query | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | Query | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `categoryId` | Query | ID | 否 | 可选，分类筛选 |
| `tagId` | Query | ID | 否 | 可选，标签筛选 |
| `month` | Query | string | 否 | 可选，合法 YYYY-MM，年份 1000..9999；按 UTC 月份过滤 |

#### 请求示例

```http
GET /api/v1/blog/articles?page=1&pageSize=10
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<ArticlePublic> | 本接口业务结果 |
| `data.page` | integer | 当前页码，数字 |
| `data.pageSize` | integer | 每页条数，数字 |
| `data.total` | string | 同过滤条件的总条数，字符串 |
| `data.totalPages` | string | 总页数，字符串 |
| `data.records` | array | 本页数据，无记录为 [] |
| `data.records[].articleId` | ID | 文章 ID |
| `data.records[].author` | object | 作者公开资料；删除占位评论为 null |
| `data.records[].author.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.records[].author.nickname` | string | 展示昵称 |
| `data.records[].author.avatar` | string&#124;null | 外部头像 URL，可 null |
| `data.records[].category` | object&#124;null | 当前分类，未设置或前台隐藏时 null |
| `data.records[].title` | string | 文章或站点标题 |
| `data.records[].slug` | string | 稳定 URL 标识，区分大小写 |
| `data.records[].summary` | string | 文章摘要，可空串 |
| `data.records[].coverUrl` | string&#124;null | 封面 URL，可 null |
| `data.records[].articleType` | integer | 1 原创、2 转载、3 翻译 |
| `data.records[].originalUrl` | string&#124;null | 原文 URL，可 null |
| `data.records[].isTop` | integer | 0 普通、1 置顶 |
| `data.records[].allowComment` | integer | 文章是否允许评论；服务端还检查站点开关 |
| `data.records[].viewCount` | string | 浏览计数字符串 |
| `data.records[].likeCount` | string | 当前点赞明细计数字符串 |
| `data.records[].commentCount` | string | 当前可见且通过的评论计数字符串 |
| `data.records[].publishTime` | Time | 首次发布时间，未发布可 null |
| `data.records[].createTime` | Time | 创建 UTC 时间 |
| `data.records[].updateTime` | Time | 修改 UTC 时间 |
| `data.records[].tags` | array | 标签对象数组，tagId 升序 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "articleId": "101",
        "author": {
          "userId": "12",
          "nickname": "落叶",
          "avatar": null
        },
        "category": null,
        "title": "第一片落叶",
        "slug": "first-leaf",
        "summary": "记录与分享",
        "coverUrl": null,
        "articleType": 1,
        "originalUrl": null,
        "isTop": 0,
        "allowComment": 1,
        "viewCount": "0",
        "likeCount": "0",
        "commentCount": "0",
        "publishTime": "2026-10-29T02:00:00.000Z",
        "createTime": "2026-10-29T02:00:00.000Z",
        "updateTime": "2026-10-29T02:00:00.000Z",
        "tags": []
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

全部过滤 AND 组合；isTop DESC,publishTime DESC,articleId DESC。keyword 标题、摘要与Markdown原文字面包含，隐藏分类不暴露。

---

<a id="api-38"></a>

### 7.2 按 slug 查询公开文章详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/articles/{slug}` |
| 接口描述 | 按 slug 查询公开文章详情 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；涉及文章时必须满足 PUBLIC 条件 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `slug` | Path | string | 是 | 文章 slug ≤200，区分大小写 |

#### 请求示例

```http
GET /api/v1/blog/articles/first-leaf
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | ArticlePublicDetail | 本接口业务结果 |
| `data.articleId` | ID | 文章 ID |
| `data.author` | object | 作者公开资料；删除占位评论为 null |
| `data.author.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.author.nickname` | string | 展示昵称 |
| `data.author.avatar` | string&#124;null | 外部头像 URL，可 null |
| `data.category` | object&#124;null | 当前分类，未设置或前台隐藏时 null |
| `data.title` | string | 文章或站点标题 |
| `data.slug` | string | 稳定 URL 标识，区分大小写 |
| `data.summary` | string | 文章摘要，可空串 |
| `data.coverUrl` | string&#124;null | 封面 URL，可 null |
| `data.articleType` | integer | 1 原创、2 转载、3 翻译 |
| `data.originalUrl` | string&#124;null | 原文 URL，可 null |
| `data.isTop` | integer | 0 普通、1 置顶 |
| `data.allowComment` | integer | 文章是否允许评论；服务端还检查站点开关 |
| `data.viewCount` | string | 浏览计数字符串 |
| `data.likeCount` | string | 当前点赞明细计数字符串 |
| `data.commentCount` | string | 当前可见且通过的评论计数字符串 |
| `data.publishTime` | Time | 首次发布时间，未发布可 null |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `data.tags` | array | 标签对象数组，tagId 升序 |
| `data.htmlContent` | string | 服务端渲染并清理的 HTML |
| `data.wordCount` | string | 可见正文派生字数，见12.15 |
| `data.readingMinutes` | integer | max(1,ceil(wordCount/300)) |
| `data.prevArticle` / `data.nextArticle` | ArticleNavigation&#124;null | 更早/更晚的相邻PUBLIC文章 |
| `data.prevArticle.articleId` / `data.nextArticle.articleId` | ID | 非空导航的文章ID |
| `data.prevArticle.title` / `data.nextArticle.title` | string | 非空导航的标题 |
| `data.prevArticle.slug` / `data.nextArticle.slug` | string | 非空导航的slug |
| `data.relatedArticles` | ArticleNavigation[] | 最多6篇同分类PUBLIC文章，元素也只有articleId/title/slug |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "articleId": "101",
    "author": {
      "userId": "12",
      "nickname": "落叶",
      "avatar": null
    },
    "category": null,
    "title": "第一片落叶",
    "slug": "first-leaf",
    "summary": "记录与分享",
    "coverUrl": null,
    "articleType": 1,
    "originalUrl": null,
    "isTop": 0,
    "allowComment": 1,
    "viewCount": "0",
    "likeCount": "0",
    "commentCount": "0",
    "publishTime": "2026-10-29T02:00:00.000Z",
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z",
    "tags": [],
    "htmlContent": "<h1 id=\"第一片落叶\">第一片落叶</h1>\n<p>你好。</p>",
    "wordCount": "4",
    "readingMinutes": 1,
    "prevArticle": null,
    "nextArticle": null,
    "relatedArticles": []
  },
  "errors": []
}
```

#### 业务说明

每次成功原子增加 view_count；失败不计数，这是阅读次数而非独立访客。仅输出清理后的HTML；字数/阅读时长、上下篇和相关推荐见12.15，所有关联文章也必须PUBLIC。

---

<a id="api-39"></a>

### 7.3 分页查询文章月归档

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/archives` |
| 接口描述 | 分页查询文章月归档 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；涉及文章时必须满足 PUBLIC 条件 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `page` | Query | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | Query | integer | 否 | 默认 10，范围 1..100 |

#### 请求示例

```http
GET /api/v1/blog/archives?page=1&pageSize=10
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<Archive> | 本接口业务结果 |
| `data.page` | integer | 当前页码，数字 |
| `data.pageSize` | integer | 每页条数，数字 |
| `data.total` | string | 同过滤条件的总条数，字符串 |
| `data.totalPages` | string | 总页数，字符串 |
| `data.records` | array | 本页数据，无记录为 [] |
| `data.records[].month` | string | UTC 年月 YYYY-MM |
| `data.records[].articleCount` | string | 该月公开文章数，字符串 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "month": "2026-10",
        "articleCount": "1"
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

仅统计 PUBLIC；UTC 月份 YYYY-MM，month DESC。点击月份复用文章列表 month。

---

<a id="api-40"></a>

### 7.4 分页查询后台文章

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/articles` |
| 接口描述 | 分页查询后台文章 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:article:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `page` | Query | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | Query | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | Query | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `categoryId` | Query | ID | 否 | 可选，分类筛选 |
| `tagId` | Query | ID | 否 | 可选，标签筛选 |
| `authorId` | Query | ID | 否 | 可选，作者筛选，不扩大 SELF |
| `status` | Query | integer | 否 | 可选，0/1/2；含义见该业务状态约定 |
| `visibility` | Query | integer | 否 | 可选，1 公开、2 私密 |
| `trash` | Query | integer | 否 | 可选，0 正常、1 回收站，默认 0 |

#### 请求示例

```http
GET /api/v1/admin/articles?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<ArticleAdminSummary> | 本接口业务结果 |
| `data.page` | integer | 当前页码，数字 |
| `data.pageSize` | integer | 每页条数，数字 |
| `data.total` | string | 同过滤条件的总条数，字符串 |
| `data.totalPages` | string | 总页数，字符串 |
| `data.records` | array | 本页数据，无记录为 [] |
| `data.records[].articleId` | ID | 文章 ID |
| `data.records[].authorId` | ID | 文章作者 ID |
| `data.records[].author` | object | 作者公开资料；删除占位评论为 null |
| `data.records[].author.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.records[].author.nickname` | string | 展示昵称 |
| `data.records[].category` | object&#124;null | 当前分类，未设置或前台隐藏时 null |
| `data.records[].title` | string | 文章或站点标题 |
| `data.records[].slug` | string | 稳定 URL 标识，区分大小写 |
| `data.records[].summary` | string | 文章摘要，可空串 |
| `data.records[].coverUrl` | string&#124;null | 封面 URL，可 null |
| `data.records[].articleType` | integer | 1 原创、2 转载、3 翻译 |
| `data.records[].originalUrl` | string&#124;null | 原文 URL，可 null |
| `data.records[].status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.records[].visibility` | integer | 1 公开、2 私密 |
| `data.records[].isTop` | integer | 0 普通、1 置顶 |
| `data.records[].allowComment` | integer | 文章是否允许评论；服务端还检查站点开关 |
| `data.records[].viewCount` | string | 浏览计数字符串 |
| `data.records[].likeCount` | string | 当前点赞明细计数字符串 |
| `data.records[].commentCount` | string | 当前可见且通过的评论计数字符串 |
| `data.records[].publishTime` | string&#124;null | 首次发布时间，未发布可 null |
| `data.records[].deletedAt` | string&#124;null | 回收时间，正常为 null |
| `data.records[].createTime` | Time | 创建 UTC 时间 |
| `data.records[].updateTime` | Time | 修改 UTC 时间 |
| `data.records[].tags` | array | 标签对象数组，tagId 升序 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "articleId": "101",
        "authorId": "12",
        "author": {
          "userId": "12",
          "nickname": "落叶"
        },
        "category": null,
        "title": "第一片落叶",
        "slug": "first-leaf",
        "summary": "记录与分享",
        "coverUrl": null,
        "articleType": 1,
        "originalUrl": null,
        "status": 0,
        "visibility": 1,
        "isTop": 0,
        "allowComment": 1,
        "viewCount": "0",
        "likeCount": "0",
        "commentCount": "0",
        "publishTime": null,
        "deletedAt": null,
        "createTime": "2026-10-29T02:00:00.000Z",
        "updateTime": "2026-10-29T02:00:00.000Z",
        "tags": []
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

updateTime DESC,articleId DESC；trash 默认 0。SELF 强制本人，authorId 不能扩大范围。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-41"></a>

### 7.5 查询后台文章详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/articles/{articleId}` |
| 接口描述 | 查询后台文章详情 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:article:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `articleId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
GET /api/v1/admin/articles/101
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | ArticleAdminDetail | 本接口业务结果 |
| `data.articleId` | ID | 文章 ID |
| `data.authorId` | ID | 文章作者 ID |
| `data.author` | object | 作者公开资料；删除占位评论为 null |
| `data.author.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.author.nickname` | string | 展示昵称 |
| `data.category` | object&#124;null | 当前分类，未设置或前台隐藏时 null |
| `data.title` | string | 文章或站点标题 |
| `data.slug` | string | 稳定 URL 标识，区分大小写 |
| `data.summary` | string | 文章摘要，可空串 |
| `data.coverUrl` | string&#124;null | 封面 URL，可 null |
| `data.articleType` | integer | 1 原创、2 转载、3 翻译 |
| `data.originalUrl` | string&#124;null | 原文 URL，可 null |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.visibility` | integer | 1 公开、2 私密 |
| `data.isTop` | integer | 0 普通、1 置顶 |
| `data.allowComment` | integer | 文章是否允许评论；服务端还检查站点开关 |
| `data.viewCount` | string | 浏览计数字符串 |
| `data.likeCount` | string | 当前点赞明细计数字符串 |
| `data.commentCount` | string | 当前可见且通过的评论计数字符串 |
| `data.publishTime` | string&#124;null | 首次发布时间，未发布可 null |
| `data.deletedAt` | string&#124;null | 回收时间，正常为 null |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `data.tags` | array | 标签对象数组，tagId 升序 |
| `data.categoryId` | ID&#124;null | 分类 ID，可 null |
| `data.tagIds` | ID[] | 标签 ID 数组，升序 |
| `data.markdownContent` | string | Markdown 源文，仅后台详情返回 |
| `data.htmlContent` | string | 服务端渲染并清理的 HTML |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "articleId": "101",
    "authorId": "12",
    "author": {
      "userId": "12",
      "nickname": "落叶"
    },
    "category": null,
    "title": "第一片落叶",
    "slug": "first-leaf",
    "summary": "记录与分享",
    "coverUrl": null,
    "articleType": 1,
    "originalUrl": null,
    "status": 0,
    "visibility": 1,
    "isTop": 0,
    "allowComment": 1,
    "viewCount": "0",
    "likeCount": "0",
    "commentCount": "0",
    "publishTime": null,
    "deletedAt": null,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z",
    "tags": [],
    "categoryId": null,
    "tagIds": [],
    "markdownContent": "# 第一片落叶\n\n你好。",
    "htmlContent": "<h1 id=\"第一片落叶\">第一片落叶</h1>\n<p>你好。</p>"
  },
  "errors": []
}
```

#### 业务说明

含回收站及 Markdown；关联与正文属于同一聚合版本。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-42"></a>

### 7.6 创建草稿文章

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/articles` |
| 接口描述 | 创建草稿文章 |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `blog:article:create`；按对应操作校验范围 |
| 成功状态 | 201 Created |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `title` | JSON Body | string | 是 | 1..200 字符 |
| `slug` | JSON Body | string | 是 | ≤200，ASCII 连字符分词、区分大小写唯一；回收站占用 |
| `summary` | JSON Body | string | 否 | 0..500 字符，默认空串，不可 null |
| `categoryId` | JSON Body | ID&#124;null | 否 | 默认 null，非 null 必须存在 |
| `coverUrl` | JSON Body | string&#124;null | 否 | URL ≤2048，默认 null；内部文件只能引用当前文章上下文，创建时不可指定未知文章 ID |
| `articleType` | JSON Body | integer | 否 | 1 原创、2 转载、3 翻译，默认 1 |
| `originalUrl` | JSON Body | string&#124;null | 否 | 默认 null；转载/翻译必须提供合法 URL |
| `visibility` | JSON Body | integer | 否 | 1 公开、2 私密，默认 1 |
| `isTop` | JSON Body | integer | 否 | 0/1，默认 0 |
| `allowComment` | JSON Body | integer | 否 | 0/1，默认 1 |
| `markdownContent` | JSON Body | string | 是 | UTF-8 ≤1MiB；草稿可空串，发布须非空白；拒绝 htmlContent |
| `tagIds` | JSON Body | ID[] | 否 | ≤20 个，不重复且全部存在，默认 [] |

#### 请求示例

```http
POST /api/v1/admin/articles
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "title": "第一片落叶",
  "slug": "first-leaf",
  "summary": "记录与分享",
  "categoryId": null,
  "coverUrl": null,
  "articleType": 1,
  "originalUrl": null,
  "visibility": 1,
  "isTop": 0,
  "allowComment": 1,
  "markdownContent": "# 第一片落叶\n\n你好。",
  "tagIds": []
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | ArticleAdminDetail | 本接口业务结果 |
| `data.articleId` | ID | 文章 ID |
| `data.authorId` | ID | 文章作者 ID |
| `data.author` | object | 作者公开资料；删除占位评论为 null |
| `data.author.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.author.nickname` | string | 展示昵称 |
| `data.category` | object&#124;null | 当前分类，未设置或前台隐藏时 null |
| `data.title` | string | 文章或站点标题 |
| `data.slug` | string | 稳定 URL 标识，区分大小写 |
| `data.summary` | string | 文章摘要，可空串 |
| `data.coverUrl` | string&#124;null | 封面 URL，可 null |
| `data.articleType` | integer | 1 原创、2 转载、3 翻译 |
| `data.originalUrl` | string&#124;null | 原文 URL，可 null |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.visibility` | integer | 1 公开、2 私密 |
| `data.isTop` | integer | 0 普通、1 置顶 |
| `data.allowComment` | integer | 文章是否允许评论；服务端还检查站点开关 |
| `data.viewCount` | string | 浏览计数字符串 |
| `data.likeCount` | string | 当前点赞明细计数字符串 |
| `data.commentCount` | string | 当前可见且通过的评论计数字符串 |
| `data.publishTime` | string&#124;null | 首次发布时间，未发布可 null |
| `data.deletedAt` | string&#124;null | 回收时间，正常为 null |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `data.tags` | array | 标签对象数组，tagId 升序 |
| `data.categoryId` | ID&#124;null | 分类 ID，可 null |
| `data.tagIds` | ID[] | 标签 ID 数组，升序 |
| `data.markdownContent` | string | Markdown 源文，仅后台详情返回 |
| `data.htmlContent` | string | 服务端渲染并清理的 HTML |

#### 响应示例

```http
HTTP/1.1 201 Created
Content-Type: application/json; charset=UTF-8
Location: /api/v1/admin/articles/101
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "articleId": "101",
    "authorId": "12",
    "author": {
      "userId": "12",
      "nickname": "落叶"
    },
    "category": null,
    "title": "第一片落叶",
    "slug": "first-leaf",
    "summary": "记录与分享",
    "coverUrl": null,
    "articleType": 1,
    "originalUrl": null,
    "status": 0,
    "visibility": 1,
    "isTop": 0,
    "allowComment": 1,
    "viewCount": "0",
    "likeCount": "0",
    "commentCount": "0",
    "publishTime": null,
    "deletedAt": null,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z",
    "tags": [],
    "categoryId": null,
    "tagIds": [],
    "markdownContent": "# 第一片落叶\n\n你好。",
    "htmlContent": "<h1 id=\"第一片落叶\">第一片落叶</h1>\n<p>你好。</p>"
  },
  "errors": []
}
```

#### 业务说明

作者固定当前用户，status 固定 0；文章、正文、标签同事务。创建并取得 ID 后再添加本站附件引用。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-43"></a>

### 7.7 编辑文章

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/articles/{articleId}` |
| 接口描述 | 编辑文章 |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `blog:article:update`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `articleId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |
| `title` | JSON Body | string | 是 | 1..200 字符 |
| `slug` | JSON Body | string | 是 | ≤200，ASCII 连字符分词、区分大小写唯一；回收站占用 |
| `summary` | JSON Body | string | 是 | 0..500 字符，默认空串，不可 null |
| `categoryId` | JSON Body | ID&#124;null | 是，可 null | 默认 null，非 null 必须存在 |
| `coverUrl` | JSON Body | string&#124;null | 是，可 null | URL ≤2048，默认 null；内部文件只能引用当前文章上下文，创建时不可指定未知文章 ID |
| `articleType` | JSON Body | integer | 是 | 1 原创、2 转载、3 翻译，默认 1 |
| `originalUrl` | JSON Body | string&#124;null | 是，可 null | 默认 null；转载/翻译必须提供合法 URL |
| `visibility` | JSON Body | integer | 是 | 1 公开、2 私密，默认 1 |
| `isTop` | JSON Body | integer | 是 | 0/1，默认 0 |
| `allowComment` | JSON Body | integer | 是 | 0/1，默认 1 |
| `markdownContent` | JSON Body | string | 是 | UTF-8 ≤1MiB；草稿可空串，发布须非空白；拒绝 htmlContent |
| `tagIds` | JSON Body | ID[] | 是 | ≤20 个，不重复且全部存在，默认 [] |

#### 请求示例

```http
PUT /api/v1/admin/articles/101
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "title": "第一片落叶",
  "slug": "first-leaf",
  "summary": "记录与分享",
  "categoryId": null,
  "coverUrl": null,
  "articleType": 1,
  "originalUrl": null,
  "visibility": 1,
  "isTop": 0,
  "allowComment": 1,
  "markdownContent": "# 第一片落叶\n\n你好。",
  "tagIds": []
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | ArticleAdminDetail | 本接口业务结果 |
| `data.articleId` | ID | 文章 ID |
| `data.authorId` | ID | 文章作者 ID |
| `data.author` | object | 作者公开资料；删除占位评论为 null |
| `data.author.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.author.nickname` | string | 展示昵称 |
| `data.category` | object&#124;null | 当前分类，未设置或前台隐藏时 null |
| `data.title` | string | 文章或站点标题 |
| `data.slug` | string | 稳定 URL 标识，区分大小写 |
| `data.summary` | string | 文章摘要，可空串 |
| `data.coverUrl` | string&#124;null | 封面 URL，可 null |
| `data.articleType` | integer | 1 原创、2 转载、3 翻译 |
| `data.originalUrl` | string&#124;null | 原文 URL，可 null |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.visibility` | integer | 1 公开、2 私密 |
| `data.isTop` | integer | 0 普通、1 置顶 |
| `data.allowComment` | integer | 文章是否允许评论；服务端还检查站点开关 |
| `data.viewCount` | string | 浏览计数字符串 |
| `data.likeCount` | string | 当前点赞明细计数字符串 |
| `data.commentCount` | string | 当前可见且通过的评论计数字符串 |
| `data.publishTime` | string&#124;null | 首次发布时间，未发布可 null |
| `data.deletedAt` | string&#124;null | 回收时间，正常为 null |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `data.tags` | array | 标签对象数组，tagId 升序 |
| `data.categoryId` | ID&#124;null | 分类 ID，可 null |
| `data.tagIds` | ID[] | 标签 ID 数组，升序 |
| `data.markdownContent` | string | Markdown 源文，仅后台详情返回 |
| `data.htmlContent` | string | 服务端渲染并清理的 HTML |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "articleId": "101",
    "authorId": "12",
    "author": {
      "userId": "12",
      "nickname": "落叶"
    },
    "category": null,
    "title": "第一片落叶",
    "slug": "first-leaf",
    "summary": "记录与分享",
    "coverUrl": null,
    "articleType": 1,
    "originalUrl": null,
    "status": 0,
    "visibility": 1,
    "isTop": 0,
    "allowComment": 1,
    "viewCount": "0",
    "likeCount": "0",
    "commentCount": "0",
    "publishTime": null,
    "deletedAt": null,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z",
    "tags": [],
    "categoryId": null,
    "tagIds": [],
    "markdownContent": "# 第一片落叶\n\n你好。",
    "htmlContent": "<h1 id=\"第一片落叶\">第一片落叶</h1>\n<p>你好。</p>"
  },
  "errors": []
}
```

#### 业务说明

回收站不可编辑；当前已发布时还须 publish 权限及其数据范围。拒绝 status/authorId/publishTime/htmlContent 等服务端字段。

先检查权限及资源范围，再在同一事务内验证聚合版本并修改；缺失返回 428，不匹配返回 412，不自动覆盖。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-44"></a>

### 7.8 发布或下架文章

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/articles/{articleId}/status` |
| 接口描述 | 发布或下架文章 |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `blog:article:publish`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `articleId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |
| `status` | JSON Body | integer | 是 | 1 发布、2 下架；禁止传 publishTime，回收站不可变更 |

#### 请求示例

```http
PUT /api/v1/admin/articles/101/status
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "status": 1
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | ArticleAdminDetail | 本接口业务结果 |
| `data.articleId` | ID | 文章 ID |
| `data.authorId` | ID | 文章作者 ID |
| `data.author` | object | 作者公开资料；删除占位评论为 null |
| `data.author.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.author.nickname` | string | 展示昵称 |
| `data.category` | object&#124;null | 当前分类，未设置或前台隐藏时 null |
| `data.title` | string | 文章或站点标题 |
| `data.slug` | string | 稳定 URL 标识，区分大小写 |
| `data.summary` | string | 文章摘要，可空串 |
| `data.coverUrl` | string&#124;null | 封面 URL，可 null |
| `data.articleType` | integer | 1 原创、2 转载、3 翻译 |
| `data.originalUrl` | string&#124;null | 原文 URL，可 null |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.visibility` | integer | 1 公开、2 私密 |
| `data.isTop` | integer | 0 普通、1 置顶 |
| `data.allowComment` | integer | 文章是否允许评论；服务端还检查站点开关 |
| `data.viewCount` | string | 浏览计数字符串 |
| `data.likeCount` | string | 当前点赞明细计数字符串 |
| `data.commentCount` | string | 当前可见且通过的评论计数字符串 |
| `data.publishTime` | Time | 首次发布时间，未发布可 null |
| `data.deletedAt` | string&#124;null | 回收时间，正常为 null |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `data.tags` | array | 标签对象数组，tagId 升序 |
| `data.categoryId` | ID&#124;null | 分类 ID，可 null |
| `data.tagIds` | ID[] | 标签 ID 数组，升序 |
| `data.markdownContent` | string | Markdown 源文，仅后台详情返回 |
| `data.htmlContent` | string | 服务端渲染并清理的 HTML |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "articleId": "101",
    "authorId": "12",
    "author": {
      "userId": "12",
      "nickname": "落叶"
    },
    "category": null,
    "title": "第一片落叶",
    "slug": "first-leaf",
    "summary": "记录与分享",
    "coverUrl": null,
    "articleType": 1,
    "originalUrl": null,
    "status": 1,
    "visibility": 1,
    "isTop": 0,
    "allowComment": 1,
    "viewCount": "0",
    "likeCount": "0",
    "commentCount": "0",
    "publishTime": "2026-10-29T02:00:00.000Z",
    "deletedAt": null,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z",
    "tags": [],
    "categoryId": null,
    "tagIds": [],
    "markdownContent": "# 第一片落叶\n\n你好。",
    "htmlContent": "<h1 id=\"第一片落叶\">第一片落叶</h1>\n<p>你好。</p>"
  },
  "errors": []
}
```

#### 业务说明

草稿→发布、发布→下架、下架→发布；首次发布写服务端当前 UTC，之后保留，不支持定时发布。

先检查权限及资源范围，再在同一事务内验证聚合版本并修改；缺失返回 428，不匹配返回 412，不自动覆盖。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-45"></a>

### 7.9 将文章移入回收站

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/articles/{articleId}` |
| 接口描述 | 将文章移入回收站 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:article:delete`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `articleId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |

#### 请求示例

```http
DELETE /api/v1/admin/articles/101
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 无业务返回值，固定 null |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

仅写 deletedAt，不物理清理；保留正文、slug、标签与互动。同版本且已回收可幂等成功。

先检查权限及资源范围，再在同一事务内验证聚合版本并修改；缺失返回 428，不匹配返回 412，不自动覆盖。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-46"></a>

### 7.10 恢复回收站文章

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/articles/{articleId}/restore` |
| 接口描述 | 恢复回收站文章 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:article:restore`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `articleId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |

#### 请求示例

```http
POST /api/v1/admin/articles/101/restore
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | ArticleAdminDetail | 本接口业务结果 |
| `data.articleId` | ID | 文章 ID |
| `data.authorId` | ID | 文章作者 ID |
| `data.author` | object | 作者公开资料；删除占位评论为 null |
| `data.author.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.author.nickname` | string | 展示昵称 |
| `data.category` | object&#124;null | 当前分类，未设置或前台隐藏时 null |
| `data.title` | string | 文章或站点标题 |
| `data.slug` | string | 稳定 URL 标识，区分大小写 |
| `data.summary` | string | 文章摘要，可空串 |
| `data.coverUrl` | string&#124;null | 封面 URL，可 null |
| `data.articleType` | integer | 1 原创、2 转载、3 翻译 |
| `data.originalUrl` | string&#124;null | 原文 URL，可 null |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.visibility` | integer | 1 公开、2 私密 |
| `data.isTop` | integer | 0 普通、1 置顶 |
| `data.allowComment` | integer | 文章是否允许评论；服务端还检查站点开关 |
| `data.viewCount` | string | 浏览计数字符串 |
| `data.likeCount` | string | 当前点赞明细计数字符串 |
| `data.commentCount` | string | 当前可见且通过的评论计数字符串 |
| `data.publishTime` | string&#124;null | 首次发布时间，未发布可 null |
| `data.deletedAt` | string&#124;null | 回收时间，正常为 null |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `data.tags` | array | 标签对象数组，tagId 升序 |
| `data.categoryId` | ID&#124;null | 分类 ID，可 null |
| `data.tagIds` | ID[] | 标签 ID 数组，升序 |
| `data.markdownContent` | string | Markdown 源文，仅后台详情返回 |
| `data.htmlContent` | string | 服务端渲染并清理的 HTML |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "articleId": "101",
    "authorId": "12",
    "author": {
      "userId": "12",
      "nickname": "落叶"
    },
    "category": null,
    "title": "第一片落叶",
    "slug": "first-leaf",
    "summary": "记录与分享",
    "coverUrl": null,
    "articleType": 1,
    "originalUrl": null,
    "status": 2,
    "visibility": 1,
    "isTop": 0,
    "allowComment": 1,
    "viewCount": "0",
    "likeCount": "0",
    "commentCount": "0",
    "publishTime": null,
    "deletedAt": null,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z",
    "tags": [],
    "categoryId": null,
    "tagIds": [],
    "markdownContent": "# 第一片落叶\n\n你好。",
    "htmlContent": "<h1 id=\"第一片落叶\">第一片落叶</h1>\n<p>你好。</p>"
  },
  "errors": []
}
```

#### 业务说明

恢复固定下架 status=2，保留首次发布时间，须显式发布才公开。非回收站 409。

先检查权限及资源范围，再在同一事务内验证聚合版本并修改；缺失返回 428，不匹配返回 412，不自动覆盖。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---


<a id="api-87"></a>

### 7.11 查询文章热门榜

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/article-rankings` |
| 接口描述 | 查询文章热门榜（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 公开；仅按本接口声明的能力与可见范围访问 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `period` | Query | string | 否 | site全站/month本月，默认site |
| `metric` | Query | string | 否 | view浏览/like点赞，默认view |
| `limit` | Query | integer | 否 | 默认5，1..20 |

#### 请求示例

```http
GET /api/v1/blog/article-rankings?period=month&metric=view&limit=5
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | HotArticle[] | 本接口业务结果 |
| `data[].articleId` | ID | PUBLIC文章ID |
| `data[].title` | string | 标题 |
| `data[].slug` | string | URL标识 |
| `data[].summary` | string | 摘要 |
| `data[].coverUrl` | URL? | 外部公开封面 |
| `data[].viewCount` | string | 文章累计阅读数 |
| `data[].likeCount` | string | 当前点赞明细数 |
| `data[].hotValue` | string | 按metric选择的累计阅读数或点赞数 |
| `data[].publishTime` | Time | 首次发布时间 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": [
    {
      "articleId": "101",
      "title": "第一片落叶",
      "slug": "first-leaf",
      "summary": "记录与分享",
      "coverUrl": null,
      "viewCount": "120",
      "likeCount": "8",
      "hotValue": "120",
      "publishTime": "2026-10-30T02:00:00.000Z"
    }
  ],
  "errors": []
}
```

#### 业务说明

保留旧站4个榜单的业务，用period与metric枚举参数表达。month按UTC本月首次发布时间过滤候选文章，再按当前累计view_count或点赞明细COUNT排序；不是本月新增浏览/点赞。排序hotValue DESC,publishTime DESC,articleId DESC，所有候选必须PUBLIC，不增加浏览计数。使用独立article-rankings路径，不占用既有文章slug。

---

<a id="group-8"></a>

## 8. 分类与标签

<a id="api-47"></a>

### 8.1 分页查询公开分类

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/categories` |
| 接口描述 | 分页查询公开分类 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；涉及文章时必须满足 PUBLIC 条件 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `page` | Query | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | Query | integer | 否 | 默认 10，范围 1..100 |

#### 请求示例

```http
GET /api/v1/blog/categories?page=1&pageSize=10
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<CategoryPublic> | 本接口业务结果 |
| `data.page` | integer | 当前页码，数字 |
| `data.pageSize` | integer | 每页条数，数字 |
| `data.total` | string | 同过滤条件的总条数，字符串 |
| `data.totalPages` | string | 总页数，字符串 |
| `data.records` | array | 本页数据，无记录为 [] |
| `data.records[].categoryId` | ID | 分类 ID，可 null |
| `data.records[].categoryName` | string | 分类名称 |
| `data.records[].slug` | string | 稳定 URL 标识，区分大小写 |
| `data.records[].description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
| `data.records[].sortOrder` | integer | int32 升序排序值 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "categoryId": "1",
        "categoryName": "技术",
        "slug": "tech",
        "description": null,
        "sortOrder": 0
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

仅 status=1；sortOrder ASC,categoryId ASC。

---

<a id="api-48"></a>

### 8.2 分页查询后台分类

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/categories` |
| 接口描述 | 分页查询后台分类 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:category:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `page` | Query | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | Query | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | Query | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `status` | Query | integer | 否 | 可选，0/1；含义见该业务状态约定 |

#### 请求示例

```http
GET /api/v1/admin/categories?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<Category> | 本接口业务结果 |
| `data.page` | integer | 当前页码，数字 |
| `data.pageSize` | integer | 每页条数，数字 |
| `data.total` | string | 同过滤条件的总条数，字符串 |
| `data.totalPages` | string | 总页数，字符串 |
| `data.records` | array | 本页数据，无记录为 [] |
| `data.records[].categoryId` | ID | 分类 ID，可 null |
| `data.records[].categoryName` | string | 分类名称 |
| `data.records[].slug` | string | 稳定 URL 标识，区分大小写 |
| `data.records[].description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
| `data.records[].sortOrder` | integer | int32 升序排序值 |
| `data.records[].status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.records[].createTime` | Time | 创建 UTC 时间 |
| `data.records[].updateTime` | Time | 修改 UTC 时间 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "categoryId": "1",
        "categoryName": "技术",
        "slug": "tech",
        "description": null,
        "sortOrder": 0,
        "status": 1,
        "createTime": "2026-10-29T02:00:00.000Z",
        "updateTime": "2026-10-29T02:00:00.000Z"
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

sortOrder ASC,categoryId ASC；全局字典，不使用 SELF 本人过滤。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-49"></a>

### 8.3 查询分类详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/categories/{categoryId}` |
| 接口描述 | 查询分类详情 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:category:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `categoryId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
GET /api/v1/admin/categories/1
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Category | 本接口业务结果 |
| `data.categoryId` | ID | 分类 ID，可 null |
| `data.categoryName` | string | 分类名称 |
| `data.slug` | string | 稳定 URL 标识，区分大小写 |
| `data.description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
| `data.sortOrder` | integer | int32 升序排序值 |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "categoryId": "1",
    "categoryName": "技术",
    "slug": "tech",
    "description": null,
    "sortOrder": 0,
    "status": 1,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

后台(blog:category:query)

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-50"></a>

### 8.4 创建分类

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/categories` |
| 接口描述 | 创建分类 |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `blog:category:create`；按对应操作校验范围 |
| 成功状态 | 201 Created |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `categoryName` | JSON Body | string | 是 | 1..64，按库排序规则唯一 |
| `slug` | JSON Body | string | 是 | ASCII slug ≤100，区分大小写唯一 |
| `description` | JSON Body | string&#124;null | 否 | ≤255，默认 null |
| `sortOrder` | JSON Body | integer | 否 | int32，默认 0 |
| `status` | JSON Body | integer | 否 | 0/1，默认 1 |

#### 请求示例

```http
POST /api/v1/admin/categories
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "categoryName": "技术",
  "slug": "tech",
  "description": null,
  "sortOrder": 0,
  "status": 1
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Category | 本接口业务结果 |
| `data.categoryId` | ID | 分类 ID，可 null |
| `data.categoryName` | string | 分类名称 |
| `data.slug` | string | 稳定 URL 标识，区分大小写 |
| `data.description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
| `data.sortOrder` | integer | int32 升序排序值 |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |

#### 响应示例

```http
HTTP/1.1 201 Created
Content-Type: application/json; charset=UTF-8
Location: /api/v1/admin/categories/1
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "categoryId": "1",
    "categoryName": "技术",
    "slug": "tech",
    "description": null,
    "sortOrder": 0,
    "status": 1,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

后台(blog:category:create)

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-51"></a>

### 8.5 修改分类

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/categories/{categoryId}` |
| 接口描述 | 修改分类 |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `blog:category:update`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `categoryId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |
| `categoryName` | JSON Body | string | 是 | 1..64，按库排序规则唯一 |
| `slug` | JSON Body | string | 是 | ASCII slug ≤100，区分大小写唯一 |
| `description` | JSON Body | string&#124;null | 是，可 null | ≤255，默认 null |
| `sortOrder` | JSON Body | integer | 是 | int32，默认 0 |
| `status` | JSON Body | integer | 是 | 0/1，默认 1 |

#### 请求示例

```http
PUT /api/v1/admin/categories/1
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "categoryName": "技术",
  "slug": "tech",
  "description": null,
  "sortOrder": 0,
  "status": 1
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Category | 本接口业务结果 |
| `data.categoryId` | ID | 分类 ID，可 null |
| `data.categoryName` | string | 分类名称 |
| `data.slug` | string | 稳定 URL 标识，区分大小写 |
| `data.description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
| `data.sortOrder` | integer | int32 升序排序值 |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "categoryId": "1",
    "categoryName": "技术",
    "slug": "tech",
    "description": null,
    "sortOrder": 0,
    "status": 1,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

后台(blog:category:update)

先检查权限及资源范围，再在同一事务内验证聚合版本并修改；缺失返回 428，不匹配返回 412，不自动覆盖。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-52"></a>

### 8.6 删除分类

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/categories/{categoryId}` |
| 接口描述 | 删除分类 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:category:delete`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `categoryId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |

#### 请求示例

```http
DELETE /api/v1/admin/categories/1
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 无业务返回值，固定 null |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

文章 categoryId 置 null；关联变化须令文章聚合 X-Revision 失效。

先检查权限及资源范围，再在同一事务内验证聚合版本并修改；缺失返回 428，不匹配返回 412，不自动覆盖。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-53"></a>

### 8.7 分页查询公开标签

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/tags` |
| 接口描述 | 分页查询公开标签 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；涉及文章时必须满足 PUBLIC 条件 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `page` | Query | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | Query | integer | 否 | 默认 10，范围 1..100 |

#### 请求示例

```http
GET /api/v1/blog/tags?page=1&pageSize=10
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<TagPublic> | 本接口业务结果 |
| `data.page` | integer | 当前页码，数字 |
| `data.pageSize` | integer | 每页条数，数字 |
| `data.total` | string | 同过滤条件的总条数，字符串 |
| `data.totalPages` | string | 总页数，字符串 |
| `data.records` | array | 本页数据，无记录为 [] |
| `data.records[].tagId` | ID | 标签 ID |
| `data.records[].tagName` | string | 标签名称 |
| `data.records[].slug` | string | 稳定 URL 标识，区分大小写 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "tagId": "1",
        "tagName": "Java",
        "slug": "java"
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

标签无 status；tagId ASC。

---

<a id="api-54"></a>

### 8.8 分页查询后台标签

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/tags` |
| 接口描述 | 分页查询后台标签 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:tag:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `page` | Query | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | Query | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | Query | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |

#### 请求示例

```http
GET /api/v1/admin/tags?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<Tag> | 本接口业务结果 |
| `data.page` | integer | 当前页码，数字 |
| `data.pageSize` | integer | 每页条数，数字 |
| `data.total` | string | 同过滤条件的总条数，字符串 |
| `data.totalPages` | string | 总页数，字符串 |
| `data.records` | array | 本页数据，无记录为 [] |
| `data.records[].tagId` | ID | 标签 ID |
| `data.records[].tagName` | string | 标签名称 |
| `data.records[].slug` | string | 稳定 URL 标识，区分大小写 |
| `data.records[].createTime` | Time | 创建 UTC 时间 |
| `data.records[].updateTime` | Time | 修改 UTC 时间 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "tagId": "1",
        "tagName": "Java",
        "slug": "java",
        "createTime": "2026-10-29T02:00:00.000Z",
        "updateTime": "2026-10-29T02:00:00.000Z"
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

tagId ASC；全局字典，不使用 SELF 本人过滤。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-55"></a>

### 8.9 查询标签详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/tags/{tagId}` |
| 接口描述 | 查询标签详情 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:tag:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `tagId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
GET /api/v1/admin/tags/1
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Tag | 本接口业务结果 |
| `data.tagId` | ID | 标签 ID |
| `data.tagName` | string | 标签名称 |
| `data.slug` | string | 稳定 URL 标识，区分大小写 |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "tagId": "1",
    "tagName": "Java",
    "slug": "java",
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

后台(blog:tag:query)

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-56"></a>

### 8.10 创建标签

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/tags` |
| 接口描述 | 创建标签 |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `blog:tag:create`；按对应操作校验范围 |
| 成功状态 | 201 Created |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `tagName` | JSON Body | string | 是 | 1..64，按库排序规则唯一 |
| `slug` | JSON Body | string | 是 | ASCII slug ≤100，区分大小写唯一 |

#### 请求示例

```http
POST /api/v1/admin/tags
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "tagName": "Java",
  "slug": "java"
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Tag | 本接口业务结果 |
| `data.tagId` | ID | 标签 ID |
| `data.tagName` | string | 标签名称 |
| `data.slug` | string | 稳定 URL 标识，区分大小写 |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |

#### 响应示例

```http
HTTP/1.1 201 Created
Content-Type: application/json; charset=UTF-8
Location: /api/v1/admin/tags/1
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "tagId": "1",
    "tagName": "Java",
    "slug": "java",
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

后台(blog:tag:create)

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-57"></a>

### 8.11 修改标签

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/tags/{tagId}` |
| 接口描述 | 修改标签 |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `blog:tag:update`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `tagId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |
| `tagName` | JSON Body | string | 是 | 1..64，按库排序规则唯一 |
| `slug` | JSON Body | string | 是 | ASCII slug ≤100，区分大小写唯一 |

#### 请求示例

```http
PUT /api/v1/admin/tags/1
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "tagName": "Java",
  "slug": "java"
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Tag | 本接口业务结果 |
| `data.tagId` | ID | 标签 ID |
| `data.tagName` | string | 标签名称 |
| `data.slug` | string | 稳定 URL 标识，区分大小写 |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "tagId": "1",
    "tagName": "Java",
    "slug": "java",
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

后台(blog:tag:update)

先检查权限及资源范围，再在同一事务内验证聚合版本并修改；缺失返回 428，不匹配返回 412，不自动覆盖。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-58"></a>

### 8.12 删除标签

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/tags/{tagId}` |
| 接口描述 | 删除标签 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:tag:delete`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `tagId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |

#### 请求示例

```http
DELETE /api/v1/admin/tags/1
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 无业务返回值，固定 null |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

级联清理文章标签关联，保留文章；关联变化须令文章聚合 X-Revision 失效。

先检查权限及资源范围，再在同一事务内验证聚合版本并修改；缺失返回 428，不匹配返回 412，不自动覆盖。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---


<a id="group-9"></a>

## 9. 评论审核与点赞

<a id="api-59"></a>

### 9.1 分页查询文章公开评论

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/articles/{articleId}/comments` |
| 接口描述 | 分页查询文章公开评论 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；涉及文章时必须满足 PUBLIC 条件 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `articleId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `page` | Query | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | Query | integer | 否 | 默认 10，范围 1..50 |
| `parentId` | Query | ID | 否 | 可选，同篇文章的可见父评论；缺失仅根节点 |

#### 请求示例

```http
GET /api/v1/blog/articles/101/comments?page=1&pageSize=10
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<CommentPublic> | 本接口业务结果 |
| `data.page` | integer | 当前页码，数字 |
| `data.pageSize` | integer | 每页条数，数字 |
| `data.total` | string | 同过滤条件的总条数，字符串 |
| `data.totalPages` | string | 总页数，字符串 |
| `data.records` | array | 本页数据，无记录为 [] |
| `data.records[].commentId` | ID | 评论 ID |
| `data.records[].articleId` | ID | 文章 ID |
| `data.records[].parentId` | ID&#124;null | 父节点 ID，根为 null |
| `data.records[].content` | string | 纯文本评论；占位固定“该评论已删除” |
| `data.records[].status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.records[].createTime` | Time | 创建 UTC 时间 |
| `data.records[].updateTime` | Time | 修改 UTC 时间 |
| `data.records[].author` | object | 作者公开资料；删除占位评论为 null |
| `data.records[].author.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.records[].author.nickname` | string | 展示昵称 |
| `data.records[].author.avatar` | string&#124;null | 外部头像 URL，可 null |
| `data.records[].replyCount` | string | 当前可见直接回复数，字符串 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "commentId": "201",
        "articleId": "101",
        "parentId": null,
        "content": "感谢分享。",
        "status": 1,
        "createTime": "2026-10-29T02:00:00.000Z",
        "updateTime": "2026-10-29T02:00:00.000Z",
        "author": {
          "userId": "12",
          "nickname": "落叶",
          "avatar": null
        },
        "replyCount": "0"
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

每页最多 50；不传 parentId 为根节点，传入只查直接回复。祖先 0/2 隐藏整支；3 占位 content 固定且 author=null。

---

<a id="api-60"></a>

### 9.2 发表评论或回复

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/articles/{articleId}/comments` |
| 接口描述 | 发表评论或回复 |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `blog:comment:create`；目标文章须 PUBLIC |
| 成功状态 | 201 Created |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `articleId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `content` | JSON Body | string | 是 | trim 后 1..2000 字符纯文本 |
| `parentId` | JSON Body | ID&#124;null | 否 | 默认 null；非 null 须同篇文章、已通过且祖先可见 |

#### 请求示例

```http
POST /api/v1/blog/articles/101/comments
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "content": "感谢分享。",
  "parentId": null
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | CommentOwn | 本接口业务结果 |
| `data.commentId` | ID | 评论 ID |
| `data.articleId` | ID | 文章 ID |
| `data.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.parentId` | ID&#124;null | 父节点 ID，根为 null |
| `data.content` | string | 纯文本评论；占位固定“该评论已删除” |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |

#### 响应示例

```http
HTTP/1.1 201 Created
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "commentId": "201",
    "articleId": "101",
    "userId": "12",
    "parentId": null,
    "content": "感谢分享。",
    "status": 0,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

需要 blog:comment:create；文章 PUBLIC，站点和文章评论开关均开。审核策略由服务端决定 0/1，管理员回复也不自动免审。

---

<a id="api-61"></a>

### 9.3 分页查询本人评论

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/me/comments` |
| 接口描述 | 分页查询本人评论 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用，仅操作本人；身份从会话读取 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `page` | Query | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | Query | integer | 否 | 默认 10，范围 1..50 |
| `articleId` | Query | ID | 否 | 可选，文章筛选 |
| `status` | Query | integer | 否 | 可选，0/1/2/3；含义见该业务状态约定 |

#### 请求示例

```http
GET /api/v1/me/comments?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<CommentOwn> | 本接口业务结果 |
| `data.page` | integer | 当前页码，数字 |
| `data.pageSize` | integer | 每页条数，数字 |
| `data.total` | string | 同过滤条件的总条数，字符串 |
| `data.totalPages` | string | 总页数，字符串 |
| `data.records` | array | 本页数据，无记录为 [] |
| `data.records[].commentId` | ID | 评论 ID |
| `data.records[].articleId` | ID | 文章 ID |
| `data.records[].userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.records[].parentId` | ID&#124;null | 父节点 ID，根为 null |
| `data.records[].content` | string | 纯文本评论；占位固定“该评论已删除” |
| `data.records[].status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.records[].createTime` | Time | 创建 UTC 时间 |
| `data.records[].updateTime` | Time | 修改 UTC 时间 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "commentId": "201",
        "articleId": "101",
        "userId": "12",
        "parentId": null,
        "content": "感谢分享。",
        "status": 0,
        "createTime": "2026-10-29T02:00:00.000Z",
        "updateTime": "2026-10-29T02:00:00.000Z"
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

每页最多 50；createTime DESC,commentId DESC。下架文章的本人记录仍可管理，不返回文章正文。

---

<a id="api-62"></a>

### 9.4 删除本人评论

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/me/comments/{commentId}` |
| 接口描述 | 删除本人评论 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `blog:comment:deleteOwn`；仅本人 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `commentId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
DELETE /api/v1/me/comments/201
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 无业务返回值，固定 null |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

必须为本人，deleteOwn 不受 ALL 扩大；覆盖为占位并置 3，保留回复链，重复删除成功。

---

<a id="api-63"></a>

### 9.5 分页查询后台评论

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/comments` |
| 接口描述 | 分页查询后台评论 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:comment:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `page` | Query | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | Query | integer | 否 | 默认 10，范围 1..50 |
| `articleId` | Query | ID | 否 | 可选，文章筛选 |
| `userId` | Query | ID | 否 | 可选，评论者筛选，不扩大评论管理范围 |
| `status` | Query | integer | 否 | 可选，0/1/2/3；含义见该业务状态约定 |

#### 请求示例

```http
GET /api/v1/admin/comments?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<CommentAdmin> | 本接口业务结果 |
| `data.page` | integer | 当前页码，数字 |
| `data.pageSize` | integer | 每页条数，数字 |
| `data.total` | string | 同过滤条件的总条数，字符串 |
| `data.totalPages` | string | 总页数，字符串 |
| `data.records` | array | 本页数据，无记录为 [] |
| `data.records[].commentId` | ID | 评论 ID |
| `data.records[].articleId` | ID | 文章 ID |
| `data.records[].userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.records[].parentId` | ID&#124;null | 父节点 ID，根为 null |
| `data.records[].content` | string | 纯文本评论；占位固定“该评论已删除” |
| `data.records[].status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.records[].createTime` | Time | 创建 UTC 时间 |
| `data.records[].updateTime` | Time | 修改 UTC 时间 |
| `data.records[].author` | object | 作者公开资料；删除占位评论为 null |
| `data.records[].author.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.records[].author.nickname` | string | 展示昵称 |
| `data.records[].author.avatar` | string&#124;null | 外部头像 URL，可 null |
| `data.records[].articleTitle` | string | 所属文章标题 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "commentId": "201",
        "articleId": "101",
        "userId": "12",
        "parentId": null,
        "content": "感谢分享。",
        "status": 0,
        "createTime": "2026-10-29T02:00:00.000Z",
        "updateTime": "2026-10-29T02:00:00.000Z",
        "author": {
          "userId": "12",
          "nickname": "落叶",
          "avatar": null
        },
        "articleTitle": "第一片落叶"
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

每页最多 50；SELF 为本人文章下的评论，非“评论本人”；createTime DESC,commentId DESC。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-64"></a>

### 9.6 查询后台评论详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/comments/{commentId}` |
| 接口描述 | 查询后台评论详情 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:comment:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `commentId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
GET /api/v1/admin/comments/201
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | CommentAdmin | 本接口业务结果 |
| `data.commentId` | ID | 评论 ID |
| `data.articleId` | ID | 文章 ID |
| `data.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.parentId` | ID&#124;null | 父节点 ID，根为 null |
| `data.content` | string | 纯文本评论；占位固定“该评论已删除” |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `data.author` | object | 作者公开资料；删除占位评论为 null |
| `data.author.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.author.nickname` | string | 展示昵称 |
| `data.author.avatar` | string&#124;null | 外部头像 URL，可 null |
| `data.articleTitle` | string | 所属文章标题 |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "commentId": "201",
    "articleId": "101",
    "userId": "12",
    "parentId": null,
    "content": "感谢分享。",
    "status": 0,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z",
    "author": {
      "userId": "12",
      "nickname": "落叶",
      "avatar": null
    },
    "articleTitle": "第一片落叶"
  },
  "errors": []
}
```

#### 业务说明

可按权限查询待审/驳回内容；禁止通过前台列表混出私人内容。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-65"></a>

### 9.7 审核评论

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/comments/{commentId}/review` |
| 接口描述 | 审核评论 |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `blog:comment:review`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `commentId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |
| `status` | JSON Body | integer | 是 | 1 通过、2 驳回；3 占位为终态，不能复活 |

#### 请求示例

```http
PUT /api/v1/admin/comments/201/review
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "status": 1
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | CommentAdmin | 本接口业务结果 |
| `data.commentId` | ID | 评论 ID |
| `data.articleId` | ID | 文章 ID |
| `data.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.parentId` | ID&#124;null | 父节点 ID，根为 null |
| `data.content` | string | 纯文本评论；占位固定“该评论已删除” |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `data.author` | object | 作者公开资料；删除占位评论为 null |
| `data.author.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `data.author.nickname` | string | 展示昵称 |
| `data.author.avatar` | string&#124;null | 外部头像 URL，可 null |
| `data.articleTitle` | string | 所属文章标题 |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "commentId": "201",
    "articleId": "101",
    "userId": "12",
    "parentId": null,
    "content": "感谢分享。",
    "status": 1,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z",
    "author": {
      "userId": "12",
      "nickname": "落叶",
      "avatar": null
    },
    "articleTitle": "第一片落叶"
  },
  "errors": []
}
```

#### 业务说明

支持 0/1/2→1或2；3 为终态。父节点驳回会隐藏整支，恢复后重新计算。

先检查权限及资源范围，再在同一事务内验证聚合版本并修改；缺失返回 428，不匹配返回 412，不自动覆盖。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-66"></a>

### 9.8 后台管理删除评论

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/comments/{commentId}` |
| 接口描述 | 后台管理删除评论 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:comment:moderate`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `commentId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
DELETE /api/v1/admin/comments/201
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 无业务返回值，固定 null |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

占位删除幂等，不需 If-Revision；SELF 按文章作者校验；可处理非公开文章评论。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-67"></a>

### 9.9 查询本人点赞状态

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/articles/{articleId}/like` |
| 接口描述 | 查询本人点赞状态 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用，仅操作本人；目标文章须 PUBLIC |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `articleId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
GET /api/v1/blog/articles/101/like
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | LikeState | 本接口业务结果 |
| `data.liked` | boolean | 当前用户是否点赞 |
| `data.likeCount` | string | 当前点赞明细计数字符串 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "liked": false,
    "likeCount": "0"
  },
  "errors": []
}
```

#### 业务说明

须登录本人但不要求 like 权限；文章仍须 PUBLIC。

---

<a id="api-68"></a>

### 9.10 点赞文章

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/blog/articles/{articleId}/like` |
| 接口描述 | 点赞文章 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `blog:article:like`；目标文章须 PUBLIC |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `articleId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
PUT /api/v1/blog/articles/101/like
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | LikeState | 本接口业务结果 |
| `data.liked` | boolean | 当前用户是否点赞 |
| `data.likeCount` | string | 当前点赞明细计数字符串 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "liked": true,
    "likeCount": "1"
  },
  "errors": []
}
```

#### 业务说明

复合主键去重，重复 PUT 保持 liked=true，不是 toggle，不收 userId。

---

<a id="api-69"></a>

### 9.11 取消文章点赞

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/blog/articles/{articleId}/like` |
| 接口描述 | 取消文章点赞 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `blog:article:like`；目标文章须 PUBLIC |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `articleId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
DELETE /api/v1/blog/articles/101/like
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | LikeState | 本接口业务结果 |
| `data.liked` | boolean | 当前用户是否点赞 |
| `data.likeCount` | string | 当前点赞明细计数字符串 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "liked": false,
    "likeCount": "0"
  },
  "errors": []
}
```

#### 业务说明

不存在也成功且 liked=false；下架后不开放此路径，既有明细保留。

---


<a id="group-10"></a>

## 10. 友链与站点配置

<a id="api-70"></a>

### 10.1 分页查询公开友链

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/links` |
| 接口描述 | 分页查询公开友链 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；涉及文章时必须满足 PUBLIC 条件 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `page` | Query | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | Query | integer | 否 | 默认 10，范围 1..100 |

#### 请求示例

```http
GET /api/v1/blog/links?page=1&pageSize=10
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<LinkPublic> | 本接口业务结果 |
| `data.page` | integer | 当前页码，数字 |
| `data.pageSize` | integer | 每页条数，数字 |
| `data.total` | string | 同过滤条件的总条数，字符串 |
| `data.totalPages` | string | 总页数，字符串 |
| `data.records` | array | 本页数据，无记录为 [] |
| `data.records[].linkId` | ID | 友链 ID |
| `data.records[].linkName` | string | 友链名称 |
| `data.records[].url` | string | 外部 http(s) 地址 |
| `data.records[].avatar` | string&#124;null | 外部头像 URL，可 null |
| `data.records[].description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
| `data.records[].sortOrder` | integer | int32 升序排序值 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "linkId": "1",
        "linkName": "示例友站",
        "url": "https://example.com",
        "avatar": null,
        "description": null,
        "sortOrder": 0
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

仅 status=1，sortOrder ASC,linkId ASC。

---

<a id="api-71"></a>

### 10.2 分页查询后台友链

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/links` |
| 接口描述 | 分页查询后台友链 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:link:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `page` | Query | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | Query | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | Query | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `status` | Query | integer | 否 | 可选，0/1/2；含义见该业务状态约定 |

#### 请求示例

```http
GET /api/v1/admin/links?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<Link> | 本接口业务结果 |
| `data.page` | integer | 当前页码，数字 |
| `data.pageSize` | integer | 每页条数，数字 |
| `data.total` | string | 同过滤条件的总条数，字符串 |
| `data.totalPages` | string | 总页数，字符串 |
| `data.records` | array | 本页数据，无记录为 [] |
| `data.records[].linkId` | ID | 友链 ID |
| `data.records[].linkName` | string | 友链名称 |
| `data.records[].url` | string | 外部 http(s) 地址 |
| `data.records[].avatar` | string&#124;null | 外部头像 URL，可 null |
| `data.records[].description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
| `data.records[].sortOrder` | integer | int32 升序排序值 |
| `data.records[].status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.records[].createTime` | Time | 创建 UTC 时间 |
| `data.records[].updateTime` | Time | 修改 UTC 时间 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "linkId": "1",
        "linkName": "示例友站",
        "url": "https://example.com",
        "avatar": null,
        "description": null,
        "sortOrder": 0,
        "status": 1,
        "createTime": "2026-10-29T02:00:00.000Z",
        "updateTime": "2026-10-29T02:00:00.000Z"
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

sortOrder ASC,linkId ASC。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-72"></a>

### 10.3 查询友链详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/links/{linkId}` |
| 接口描述 | 查询友链详情 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:link:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `linkId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
GET /api/v1/admin/links/1
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Link | 本接口业务结果 |
| `data.linkId` | ID | 友链 ID |
| `data.linkName` | string | 友链名称 |
| `data.url` | string | 外部 http(s) 地址 |
| `data.avatar` | string&#124;null | 外部头像 URL，可 null |
| `data.description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
| `data.sortOrder` | integer | int32 升序排序值 |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "linkId": "1",
    "linkName": "示例友站",
    "url": "https://example.com",
    "avatar": null,
    "description": null,
    "sortOrder": 0,
    "status": 1,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

后台(blog:link:query)

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-73"></a>

### 10.4 创建友链

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/links` |
| 接口描述 | 创建友链 |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `blog:link:create`；按对应操作校验范围 |
| 成功状态 | 201 Created |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `linkName` | JSON Body | string | 是 | 1..100，不要求唯一 |
| `url` | JSON Body | string | 是 | http(s) URL ≤2048，不要求唯一 |
| `avatar` | JSON Body | string&#124;null | 否 | 仅外部 http(s)，默认 null |
| `description` | JSON Body | string&#124;null | 否 | ≤255，默认 null |
| `sortOrder` | JSON Body | integer | 否 | int32，默认 0 |
| `status` | JSON Body | integer | 否 | 0 待审、1 展示、2 驳回/下架，默认 0 |

#### 请求示例

```http
POST /api/v1/admin/links
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "linkName": "示例友站",
  "url": "https://example.com",
  "avatar": null,
  "description": null,
  "sortOrder": 0,
  "status": 0
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Link | 本接口业务结果 |
| `data.linkId` | ID | 友链 ID |
| `data.linkName` | string | 友链名称 |
| `data.url` | string | 外部 http(s) 地址 |
| `data.avatar` | string&#124;null | 外部头像 URL，可 null |
| `data.description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
| `data.sortOrder` | integer | int32 升序排序值 |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |

#### 响应示例

```http
HTTP/1.1 201 Created
Content-Type: application/json; charset=UTF-8
Location: /api/v1/admin/links/1
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "linkId": "1",
    "linkName": "示例友站",
    "url": "https://example.com",
    "avatar": null,
    "description": null,
    "sortOrder": 0,
    "status": 0,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

默认待审 status=0；URL 和名称均无唯一键，不承诺重复 URL 返回 409。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-74"></a>

### 10.5 修改或审核友链

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/links/{linkId}` |
| 接口描述 | 修改或审核友链 |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `blog:link:update`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `linkId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |
| `linkName` | JSON Body | string | 是 | 1..100，不要求唯一 |
| `url` | JSON Body | string | 是 | http(s) URL ≤2048，不要求唯一 |
| `avatar` | JSON Body | string&#124;null | 是，可 null | 仅外部 http(s)，默认 null |
| `description` | JSON Body | string&#124;null | 是，可 null | ≤255，默认 null |
| `sortOrder` | JSON Body | integer | 是 | int32，默认 0 |
| `status` | JSON Body | integer | 是 | 0 待审、1 展示、2 驳回/下架，默认 0 |

#### 请求示例

```http
PUT /api/v1/admin/links/1
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "linkName": "示例友站",
  "url": "https://example.com",
  "avatar": null,
  "description": null,
  "sortOrder": 0,
  "status": 0
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Link | 本接口业务结果 |
| `data.linkId` | ID | 友链 ID |
| `data.linkName` | string | 友链名称 |
| `data.url` | string | 外部 http(s) 地址 |
| `data.avatar` | string&#124;null | 外部头像 URL，可 null |
| `data.description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
| `data.sortOrder` | integer | int32 升序排序值 |
| `data.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "linkId": "1",
    "linkName": "示例友站",
    "url": "https://example.com",
    "avatar": null,
    "description": null,
    "sortOrder": 0,
    "status": 1,
    "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

status 同模型更新；仅保存 URL，不由服务器抓取。

先检查权限及资源范围，再在同一事务内验证聚合版本并修改；缺失返回 428，不匹配返回 412，不自动覆盖。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-75"></a>

### 10.6 删除友链

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/links/{linkId}` |
| 接口描述 | 删除友链 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:link:delete`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `linkId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |

#### 请求示例

```http
DELETE /api/v1/admin/links/1
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 无业务返回值，固定 null |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

物理删除。

先检查权限及资源范围，再在同一事务内验证聚合版本并修改；缺失返回 428，不匹配返回 412，不自动覆盖。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-76"></a>

### 10.7 查询公开站点配置

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/configs` |
| 接口描述 | 查询公开站点配置 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；涉及文章时必须满足 PUBLIC 条件 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

无 Path、Query 或请求体参数；GET/无模型写操作不发送请求体。

#### 请求示例

```http
GET /api/v1/blog/configs
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Config[] | 本接口业务结果 |
| `data[].configKey` | string | site、comment 或 message |
| `data[].configValue` | object | 严格白名单配置对象，具体形状见配置模型 |
| `data[].configValue.title` | string | 文章或站点标题 |
| `data[].configValue.description` | string | 说明，可 null；site 内 description 可为空串 |
| `data[].configValue.icp` | string | 备案文本，可空串 |
| `data[].configValue.publicSecurityRecord` | string | site公安备案文本，可空串 |
| `data[].configValue.startDate` | date&#124;null | site建站日期，未设置为null |
| `data[].configValue.enabled` | boolean | 仅comment/message对象的业务开关 |
| `data[].configValue.reviewRequired` | boolean | 仅comment/message对象，是否默认审核；不向公开接口输出 |
| `data[].description` | string | 说明，可 null；site 内 description 可为空串 |
| `data[].isPublic` | integer | 0 私有、1 可公开，公开接口还校验键白名单 |
| `data[].updateTime` | Time | 修改 UTC 时间 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": [
    {
      "configKey": "site",
      "configValue": {
        "title": "DriftingLeaves",
        "description": "记录与分享",
        "icp": "",
        "publicSecurityRecord": "",
        "startDate": null
      },
      "description": "站点信息",
      "isPublic": 1,
      "updateTime": "2026-10-29T02:00:00.000Z"
    }
  ],
  "errors": []
}
```

#### 业务说明

仅允许公开的 site 且 isPublic=1；服务端过滤，configKey ASC。

---

<a id="api-77"></a>

### 10.8 查询后台配置列表

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/configs` |
| 接口描述 | 查询后台配置列表 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:config:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
GET /api/v1/admin/configs
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Config[] | 本接口业务结果 |
| `data[].configKey` | string | site、comment 或 message |
| `data[].configValue` | object | 严格白名单配置对象，具体形状见配置模型 |
| `data[].configValue.title` | string | 文章或站点标题 |
| `data[].configValue.description` | string | 说明，可 null；site 内 description 可为空串 |
| `data[].configValue.icp` | string | 备案文本，可空串 |
| `data[].configValue.publicSecurityRecord` | string | site公安备案文本，可空串 |
| `data[].configValue.startDate` | date&#124;null | site建站日期，未设置为null |
| `data[].configValue.enabled` | boolean | 仅comment/message对象的业务开关 |
| `data[].configValue.reviewRequired` | boolean | 仅comment/message对象，是否默认审核；不向公开接口输出 |
| `data[].description` | string | 说明，可 null；site 内 description 可为空串 |
| `data[].isPublic` | integer | 0 私有、1 可公开，公开接口还校验键白名单 |
| `data[].updateTime` | Time | 修改 UTC 时间 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": [
    {
      "configKey": "site",
      "configValue": {
        "title": "DriftingLeaves",
        "description": "记录与分享",
        "icp": "",
        "publicSecurityRecord": "",
        "startDate": null
      },
      "description": "站点信息",
      "isPublic": 1,
      "updateTime": "2026-10-29T02:00:00.000Z"
    }
  ],
  "errors": []
}
```

#### 业务说明

只支持 site/comment/message，configKey ASC；不提供任意建键接口。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-78"></a>

### 10.9 查询配置详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/configs/{configKey}` |
| 接口描述 | 查询配置详情 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:config:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `configKey` | Path | string | 是 | 仅 site/comment/message，未知键 404 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
GET /api/v1/admin/configs/site
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Config | 本接口业务结果 |
| `data.configKey` | string | site、comment 或 message |
| `data.configValue` | object | 严格白名单配置对象，具体形状见配置模型 |
| `data.configValue.title` | string | 文章或站点标题 |
| `data.configValue.description` | string | 说明，可 null；site 内 description 可为空串 |
| `data.configValue.icp` | string | 备案文本，可空串 |
| `data.configValue.publicSecurityRecord` | string | site公安备案文本，可空串 |
| `data.configValue.startDate` | date&#124;null | site建站日期，未设置为null |
| `data.configValue.enabled` | boolean | 仅comment/message对象的业务开关 |
| `data.configValue.reviewRequired` | boolean | 仅comment/message对象，是否默认审核；不向公开接口输出 |
| `data.description` | string | 说明，可 null；site 内 description 可为空串 |
| `data.isPublic` | integer | 0 私有、1 可公开，公开接口还校验键白名单 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "configKey": "site",
    "configValue": {
      "title": "DriftingLeaves",
      "description": "记录与分享",
      "icp": "",
      "publicSecurityRecord": "",
      "startDate": null
    },
    "description": "站点信息",
    "isPublic": 1,
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

site/comment/message；未知键 404。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-79"></a>

### 10.10 修改配置

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/configs/{configKey}` |
| 接口描述 | 修改配置 |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `blog:config:update`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `configKey` | Path | string | 是 | 仅 site/comment/message，未知键 404 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |
| `configValue` | JSON Body | object | 是 | site: title/description/icp/publicSecurityRecord/startDate；comment或message: enabled/reviewRequired；完整白名单对象，UTF-8 ≤16KiB |
| `description` | JSON Body | string&#124;null | 是，可 null | ≤255 字符 |
| `isPublic` | JSON Body | integer | 是 | site 为 0/1；comment/message 必须 0 |

#### 请求示例

```http
PUT /api/v1/admin/configs/site
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "configValue": {
    "title": "DriftingLeaves",
    "description": "记录与分享",
    "icp": "",
    "publicSecurityRecord": "",
    "startDate": null
  },
  "description": "站点信息",
  "isPublic": 1
}
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Config | 本接口业务结果 |
| `data.configKey` | string | site、comment 或 message |
| `data.configValue` | object | 严格白名单配置对象，具体形状见配置模型 |
| `data.configValue.title` | string | 文章或站点标题 |
| `data.configValue.description` | string | 说明，可 null；site 内 description 可为空串 |
| `data.configValue.icp` | string | 备案文本，可空串 |
| `data.configValue.publicSecurityRecord` | string | site公安备案文本，可空串 |
| `data.configValue.startDate` | date&#124;null | site建站日期，未设置为null |
| `data.configValue.enabled` | boolean | 仅comment/message对象的业务开关 |
| `data.configValue.reviewRequired` | boolean | 仅comment/message对象，是否默认审核；不向公开接口输出 |
| `data.description` | string | 说明，可 null；site 内 description 可为空串 |
| `data.isPublic` | integer | 0 私有、1 可公开，公开接口还校验键白名单 |
| `data.updateTime` | Time | 修改 UTC 时间 |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
X-Revision: "<current-revision>"
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "configKey": "site",
    "configValue": {
      "title": "DriftingLeaves",
      "description": "记录与分享",
      "icp": "",
      "publicSecurityRecord": "",
      "startDate": null
    },
    "description": "站点信息",
    "isPublic": 1,
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

完整替换值；site={title,description,icp,publicSecurityRecord,startDate}，comment/message={enabled,reviewRequired}且isPublic必须0；未知内外字段400。

先检查权限及资源范围，再在同一事务内验证聚合版本并修改；缺失返回 428，不匹配返回 412，不自动覆盖。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---


<a id="group-11"></a>

## 11. 文件与附件

<a id="api-80"></a>

### 11.1 分页查询文件元数据

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/files` |
| 接口描述 | 分页查询文件元数据 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:file:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `page` | Query | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | Query | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | Query | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `uploaderId` | Query | ID | 否 | 可选，上传者筛选，不扩大 SELF |
| `mimeType` | Query | string | 否 | 可选 ≤100，按已确认 MIME 类型筛选 |

#### 请求示例

```http
GET /api/v1/admin/files?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<File> | 本接口业务结果 |
| `data.page` | integer | 当前页码，数字 |
| `data.pageSize` | integer | 每页条数，数字 |
| `data.total` | string | 同过滤条件的总条数，字符串 |
| `data.totalPages` | string | 总页数，字符串 |
| `data.records` | array | 本页数据，无记录为 [] |
| `data.records[].fileId` | ID | 文件 ID |
| `data.records[].uploaderId` | ID | 本站上传者 ID |
| `data.records[].originalName` | string | 安全化展示文件名，无路径 |
| `data.records[].mimeType` | string | 服务端识别的 image/png 或 image/jpeg |
| `data.records[].sizeBytes` | string | 文件实际字节数字符串 |
| `data.records[].storageType` | string | 首期固定 local |
| `data.records[].createTime` | Time | 创建 UTC 时间 |
| `data.records[].previewUrl` | string | 需 Bearer 的后台文件内容路径 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "fileId": "42",
        "uploaderId": "12",
        "originalName": "leaf.png",
        "mimeType": "image/png",
        "sizeBytes": "2048",
        "storageType": "local",
        "createTime": "2026-10-29T02:00:00.000Z",
        "previewUrl": "/api/v1/admin/files/42/content"
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

SELF 仅本人；createTime DESC,fileId DESC。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-81"></a>

### 11.2 查询文件元数据

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/files/{fileId}` |
| 接口描述 | 查询文件元数据 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:file:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `fileId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
GET /api/v1/admin/files/42
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | File | 本接口业务结果 |
| `data.fileId` | ID | 文件 ID |
| `data.uploaderId` | ID | 本站上传者 ID |
| `data.originalName` | string | 安全化展示文件名，无路径 |
| `data.mimeType` | string | 服务端识别的 image/png 或 image/jpeg |
| `data.sizeBytes` | string | 文件实际字节数字符串 |
| `data.storageType` | string | 首期固定 local |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.previewUrl` | string | 需 Bearer 的后台文件内容路径 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "fileId": "42",
    "uploaderId": "12",
    "originalName": "leaf.png",
    "mimeType": "image/png",
    "sizeBytes": "2048",
    "storageType": "local",
    "createTime": "2026-10-29T02:00:00.000Z",
    "previewUrl": "/api/v1/admin/files/42/content"
  },
  "errors": []
}
```

#### 业务说明

元数据不可修改，不返回 X-Revision；previewUrl 为需鉴权的后台路径。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-82"></a>

### 11.3 上传图片

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/files` |
| 接口描述 | 上传图片 |
| Content-Type | multipart/form-data |
| 访问要求 | 登录且启用 + `admin:access` + `blog:file:upload`；按对应操作校验范围 |
| 成功状态 | 201 Created |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `file` | multipart | binary | 是 | 唯一文件字段，单个 PNG/JPEG，非空且 ≤10MiB，不收路径/上传者 |

#### 请求示例

```http
POST /api/v1/admin/files
Authorization: Bearer <site-token>
Content-Type: multipart/form-data; boundary=example

--example
Content-Disposition: form-data; name="file"; filename="leaf.png"
Content-Type: image/png

<PNG binary>
--example--
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | File | 本接口业务结果 |
| `data.fileId` | ID | 文件 ID |
| `data.uploaderId` | ID | 本站上传者 ID |
| `data.originalName` | string | 安全化展示文件名，无路径 |
| `data.mimeType` | string | 服务端识别的 image/png 或 image/jpeg |
| `data.sizeBytes` | string | 文件实际字节数字符串 |
| `data.storageType` | string | 首期固定 local |
| `data.createTime` | Time | 创建 UTC 时间 |
| `data.previewUrl` | string | 需 Bearer 的后台文件内容路径 |

#### 响应示例

```http
HTTP/1.1 201 Created
Content-Type: application/json; charset=UTF-8
Location: /api/v1/admin/files/42
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "fileId": "42",
    "uploaderId": "12",
    "originalName": "leaf.png",
    "mimeType": "image/png",
    "sizeBytes": "2048",
    "storageType": "local",
    "createTime": "2026-10-29T02:00:00.000Z",
    "previewUrl": "/api/v1/admin/files/42/content"
  },
  "errors": []
}
```

#### 业务说明

单文件非空，PNG/JPEG ≤10MiB；multipart ≤11MiB，内容识别与扩展名一致；尺寸与存储补偿见第 13 节。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-83"></a>

### 11.4 预览后台原文件

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/files/{fileId}/content` |
| 接口描述 | 预览后台原文件 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:file:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `fileId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
GET /api/v1/admin/files/42/content
Authorization: Bearer <site-token>
```

#### 响应参数

| 字段/响应头 | 类型 | 说明 |
| --- | --- | --- |
| 响应体 | binary | PNG/JPEG 原始字节，不是 Result |
| `Content-Type` | string | 服务端确认的 image/png 或 image/jpeg |
| `Content-Length` | integer | 实际文件字节数 |
| `Content-Disposition` | string | inline；filename* 使用安全 UTF-8 编码 |
| `X-Content-Type-Options` | string | nosniff |
| `Cache-Control` | string | no-store；下架/撤销引用后重新鉴权 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: image/png
Content-Length: 2048
Content-Disposition: inline; filename*=UTF-8''leaf.png
X-Content-Type-Options: nosniff
Cache-Control: no-store

<PNG binary>
```

#### 业务说明

需要文件查询权限及其范围；前端 Bearer fetch 后使用临时 object URL，不把 Token 拼到 img URL。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-84"></a>

### 11.5 删除文件

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/files/{fileId}` |
| 接口描述 | 删除文件 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:file:delete`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `fileId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
DELETE /api/v1/admin/files/42
Authorization: Bearer <site-token>
```

#### 响应参数

通用 `code/msg/errors` 见第 0 节；以下为 `data` 的完整字段形状，值域及可空性以第 12 节模型为准。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 无业务返回值，固定 null |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

持文件锁检查所有文章引用，含草稿/私密/回收站；仍引用 409。存储删除失败保留元数据返回 503。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---

<a id="api-85"></a>

### 11.6 读取公开文章附件

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/articles/{articleId}/files/{fileId}/content` |
| 接口描述 | 读取公开文章附件 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；涉及文章时必须满足 PUBLIC 条件 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `articleId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `fileId` | Path | ID | 是 | 十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/blog/articles/101/files/42/content
```

#### 响应参数

| 字段/响应头 | 类型 | 说明 |
| --- | --- | --- |
| 响应体 | binary | PNG/JPEG 原始字节，不是 Result |
| `Content-Type` | string | 服务端确认的 image/png 或 image/jpeg |
| `Content-Length` | integer | 实际文件字节数 |
| `Content-Disposition` | string | inline；filename* 使用安全 UTF-8 编码 |
| `X-Content-Type-Options` | string | nosniff |
| `Cache-Control` | string | no-store；下架/撤销引用后重新鉴权 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: image/png
Content-Length: 2048
Content-Disposition: inline; filename*=UTF-8''leaf.png
X-Content-Type-Options: nosniff
Cache-Control: no-store

<PNG binary>
```

#### 业务说明

文章 PUBLIC 且正文/封面实际引用该文件；非公开即使携带 Token 也 404。

---

<a id="api-86"></a>

### 11.7 读取后台文章附件

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/articles/{articleId}/files/{fileId}/content` |
| 接口描述 | 读取后台文章附件 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:article:query` + `blog:file:query`；按对应操作校验范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无 If-Revision 要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `articleId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `fileId` | Path | ID | 是 | 十进制正整数 ID 字符串 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |

#### 请求示例

```http
GET /api/v1/admin/articles/101/files/42/content
Authorization: Bearer <site-token>
```

#### 响应参数

| 字段/响应头 | 类型 | 说明 |
| --- | --- | --- |
| 响应体 | binary | PNG/JPEG 原始字节，不是 Result |
| `Content-Type` | string | 服务端确认的 image/png 或 image/jpeg |
| `Content-Length` | integer | 实际文件字节数 |
| `Content-Disposition` | string | inline；filename* 使用安全 UTF-8 编码 |
| `X-Content-Type-Options` | string | nosniff |
| `Cache-Control` | string | no-store；下架/撤销引用后重新鉴权 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: image/png
Content-Length: 2048
Content-Disposition: inline; filename*=UTF-8''leaf.png
X-Content-Type-Options: nosniff
Cache-Control: no-store

<PNG binary>
```

#### 业务说明

同时检查文章 query 与文件 query 及各自范围；文章须实际引用。文章作者身份不能代替跨用户文件授权。

本接口还必须满足有效 `admin:access`；全局字典操作无“仅本人”子集。文章、文件及评论按当前操作权限计算 ALL/SELF；缺少权限返回 403，范围外资源统一 404。

---


<a id="models"></a>

## 12. 共享模型与状态

逐接口参数表列出实际绑定字段；本节补充可空性、默认值、联合响应及聚合模型。所有响应字段（包括可 null 字段）必须按模型存在，不因 null 全局省略。Page<T> 与分页输出规则见第 0 节。

### 12.1 本人、身份与用户模型

| 模型/字段 | 类型、必填与约束 |
| --- | --- |
| Register.username | 必填 S(64)，设计决定仅 `[A-Za-z0-9_]{3,64}`；全库不区分大小写唯一 |
| Register.password | 必填 string，8..64 字符且 UTF-8 最多 72 字节，不 trim/截断；BCrypt 随机盐，成本上线前校准 |
| Register.nickname | 必填 S(64) |
| Login | username:S(64)、password:string 均必填；密码同长度/字节边界；仅 username 登录，不以 email 登录 |
| ProfilePatch | nickname?:S(64)（若给不能 null）；email?:S(254)?（格式校验、空串转 null、唯一）；avatar?:URL?；intro?:S(500)?；至少一个字段。这里 `?` 在字段名表示可省略 |
| Me | userId:ID、username:string、nickname:string、email:string?、avatar:URL?、intro:string?、status:0或1、hasPassword:boolean、lastLoginTime:Time?、createTime:Time、updateTime:Time |
| OAuthBinding | source:S(32)、createTime:Time；只返回平台和绑定时间，不返回 uuid、oauthId、平台 Token |
| PasswordSet | newPassword:string 必填（同注册约束）；currentPassword:string 仅已有密码时必填，否则必须省略 |
| PasswordClear | currentPassword:string 必填，同密码约束 |
| Capabilities | roleCodes:string[]、permissionCodes:string[]、dataScopes:对象（权限码 → ALL或SELF）。只含当前有效授权，数组字典序；全局权限仍返回角色计算值，但无本人资源语义 |

注册不收 email/avatar/roleIds/status，服务端固定启用并分配 member；注册仅返回 Me，不自动登录。邮箱在本站仅是可选联系资料，未验证，不能当登录、找回密码或自动合并依据。系统不得返回 passwordHash/password_hash/salt，包括管理员接口。

`OAuthResult` 为联合响应：LOGIN 只有 operation/session，BIND 只有 operation/binding。OAuth 票据和第三方 access_token 不得当成本站 Bearer。

### 12.2 用户、角色与权限模型

| 模型 | 完整字段与约束 |
| --- | --- |
| UserCreate | username、password、nickname 必填，同 Register；其余可选 email:string?、avatar:URL?、intro:string?（约束同 ProfilePatch）、status:0或1（默认1）；不接收 roleIds |
| UserUpdate | nickname:S(64)、email:string?、avatar:URL?、intro:S(500)?、status:0或1 均必填；username/密码不可改 |
| UserAdmin | Me 全部字段 + roleIds:ID[]（包含已分配但禁用的角色）；不含 OAuth uuid 或 Token |
| UserRoles | roleIds:ID[] 必填，0..100 个、不重复、角色必须存在且启用；[] 清空；只能经此专用接口分配 |
| RoleWrite | roleCode:S(64)，`[a-z][a-z0-9_]{0,63}`；roleName:S(64)；description:S(255)?；dataScope:ALL或SELF；status:0或1。POST roleCode/roleName 必填，其余默认 null/SELF/1；PUT 除 roleCode 外均必填，roleCode 创建后不可修改 |
| Role | roleId:ID、roleCode/roleName/description/dataScope/status（同上）、createTime:Time、updateTime:Time、permissionIds:ID[]、menuIds:ID[]；关联 ID 升序，含已授权但禁用项 |
| RoleGrant | permissionIds:ID[]、menuIds:ID[] 均必填，不重复，各最多1000；完整替换，不是增量 |
| PermissionWrite | permissionCode:S(100)，除 `admin:access` 外匹配 `[a-z][a-z0-9]*:[a-z][A-Za-z0-9]*:[a-z][A-Za-z0-9]*`（允许 deleteOwn）；permissionName:S(64)；module:S(32)，`[a-z][a-z0-9_]{0,31}`；description:S(255)?；status:0或1。POST 前三项必填，其余默认 null/1；PUT 除 permissionCode 外均必填，编码不可改 |
| Permission | permissionId:ID + PermissionWrite 全字段 + createTime:Time、updateTime:Time |

UserCreate 分配空角色；需要角色时再调用 UserRoles，必须另有 assignRole 权限。公开注册分配 member 不受此后台流程影响。禁用用户即时撤销会话，重新启用不恢复旧 Token。roleCode、permissionCode、菜单编码、slug 等 ASCII 字段按区分大小写唯一键处理；同名 roleName 不唯一，不能作为授权依据。

### 12.3 菜单模型

`MenuWrite` 字段如下；POST 必填 parentId/menuCode/menuName/menuType，类型决定的字段也必填；其余默认见表。PUT 除 menuCode 外所有字段均必须出现；menuCode 创建后不可修改。

| 字段 | 类型/默认 | 约束 |
| --- | --- | --- |
| parentId | ID? | null 根节点；D 的父为 D 或 null，M 的父为 D 或 null，B 的父必须为 M |
| menuCode | S(64) | ASCII `[A-Za-z0-9:_-]+`，区分大小写唯一 |
| menuName | S(64) | 展示文本 |
| menuType | D/M/B | D目录、M页面、B按钮 |
| routeName | S(64)?，默认null | ASCII `[A-Za-z][A-Za-z0-9_]*`；D/M 非空且唯一，B 必须 null |
| path | S(255)?，默认null | D/M 必须为以 `/` 起始的内部前端路径，ASCII、无 `..`/反斜线/协议/查询串/fragment，唯一；B null |
| component | S(255)?，默认null | D/M 必填，ASCII 组件注册白名单键；B null，不接远程 URL 或代码 |
| icon | S(64)?，默认null | 前端图标白名单键 |
| permissionId | ID?，默认null | D 设计决定为 null；M/B 为已存在的权限；不存权限码副本 |
| sortOrder | int32，默认0 | 有符号排序值，越小越前 |
| isVisible | 0/1，默认1；B默认0 | B 必须0，隐藏不撤权 |
| keepAlive | 0/1，默认0 | 仅 M 可为1，其余0 |
| status | 0/1，默认1 | 0禁用，1启用 |

`Menu` = menuId:ID + MenuWrite 全部字段 + createTime/updateTime:Time。`MenuNode` = Menu + permissionCode:string? + children:MenuNode[]。permissionCode 从字典取，目录为 null。树接口按 sortOrder ASC,menuId ASC 逐层排序。初始 SQL 的 Layout 等组件键属于将来前端注册约定，当前未实现；后端/前端必须维护一致白名单。

当前后端组件白名单与 SQL 种子一致：`Layout`；`content/{article,category,tag,comment,link,file,message,music,rss}/index`；`system/{user,role,menu,permission,config,log}/index`；`home/{profile,social}/index`；`cv/{experience,skill}/index`；`statistics/{overview,visitor,view}/index`（花括号表示枚举展开，提交时使用单个完整键）。图标白名单为 `document/setting/home/chart/folder/tag/chat/link/picture/music/mail/user/lock/menu/key` 中的单个键，或 null。组件键注册约定不代表对应后续业务已实现；前端新增注册键时需同步后端白名单。路径校验同时检查原始 ASCII URI 与解码后的内部路径，不接受控制字符或编码后的父目录、反斜线、协议、查询串及 fragment。

### 12.4 文章模型与状态

**所有前台读取统一使用 PUBLIC 条件**：`status=1 AND visibility=1 AND deleted_at IS NULL AND publish_time IS NOT NULL AND publish_time<=服务端当前UTC时间`。详情、列表、搜索、分类/标签筛选、归档、评论列表、点赞和文件文章上下文均如此，持有管理员 Token 不放宽前台过滤。隐藏分类不改变文章发布状态；前台分类列表和按分类筛选不暴露隐藏分类，文章仍可通过公开列表/详情读到，category 返回 null。非公开文章通过后台详情预览。

| 模型/字段 | 类型/必填/约束 |
| --- | --- |
| ArticleWrite.title | S(200)，必填 |
| ArticleWrite.slug | S(200)，必填，同通用 slug；回收站仍占唯一键 |
| ArticleWrite.summary | S0(500)，POST可选默认空串；PUT必填，不能null |
| ArticleWrite.categoryId | ID?，POST默认null；PUT必填；非null必须存在（后台可选择隐藏分类） |
| ArticleWrite.coverUrl | URL?，POST默认null；PUT必填；本站文件按[文件与附件](#group-11)公开资源约束 |
| ArticleWrite.articleType | 1原创/2转载/3翻译，POST默认1；PUT必填 |
| ArticleWrite.originalUrl | URL?，POST默认null；PUT必填；type=2/3必须非空，type=1可null |
| ArticleWrite.visibility | 1公开/2私密，POST默认1；PUT必填 |
| ArticleWrite.isTop | 0/1，POST默认0；PUT必填 |
| ArticleWrite.allowComment | 0/1，POST默认1；PUT必填 |
| ArticleWrite.markdownContent | string 必填，可空串用于草稿；设计决定 UTF-8 最多1MiB，发布必须非空白 |
| ArticleWrite.tagIds | ID[]，POST默认[]；PUT必填；最多20个、不重复、全部存在 |
| ArticleAdminSummary | articleId/authorId:ID、author:{userId:ID,nickname:string}、category:Category?、title/slug/summary、coverUrl:URL?、articleType、originalUrl:URL?、status:0/1/2、visibility:1/2、isTop/allowComment:0/1、viewCount/likeCount/commentCount:string、publishTime/deletedAt:Time?、createTime/updateTime:Time、tags:Tag[] |
| ArticleAdminDetail | ArticleAdminSummary + categoryId:ID?、tagIds:ID[]、markdownContent:string、htmlContent:string（服务端已清理，缓存为空时现渲染） |
| ArticlePublic | articleId:ID、author:{userId:ID,nickname:string,avatar:URL?}、category:CategoryPublic?、tags:TagPublic[]、title/slug/summary、coverUrl:URL?、articleType、originalUrl:URL?、isTop/allowComment:0/1、viewCount/likeCount/commentCount:string、publishTime/createTime/updateTime:Time |
| ArticlePublicDetail | ArticlePublic + htmlContent:string、wordCount:string、readingMinutes:integer、prevArticle/nextArticle:ArticleNavigation?、relatedArticles:ArticleNavigation[]；派生规则见12.15，不返回Markdown源、作者邮箱、后台状态或deletedAt |
| ArticleState | `{status:1或2}`，必填；不接收publishTime |

tags/tagIds 均按 tagId ASC；commentCount 只统计 status=1 且祖先均为1/3的节点，排除待审/驳回/占位及被隐藏的后代；likeCount 按明细 COUNT，不维护表中不存在的副本。所有资源关联名称为当前资料，非历史快照。ArticlePublic 的 status 隐含已发布，不额外重复字段。

正文仅接收 Markdown，拒绝客户端 htmlContent。CommonMark 渲染后由 jsoup 白名单清理，存可选 html_content 缓存；输出时保证符合当前清理规则，缓存不可信时重新清理。保留基本段落/标题/表格/代码/任务列表，禁止脚本、事件属性、iframe、任意 style 和危险 URL；外链加 noopener/noreferrer。评论为纯文本。本站资源引用规则见[文件与附件](#group-11)；前端不可通过原始 Markdown/raw HTML 绕过此渲染链。

设计决定：POST 固定创建 `status=0` 草稿，作者由登录用户设置；PUT 不收 status/publishTime/deletedAt/计数/authorId。状态只经专用接口变更：草稿0→发布1、发布1→下架2、下架2→发布1；重复目标幂等，无“重新变草稿”。首次发布写当前UTC publishTime，后续发布/编辑不覆盖，禁止未来发布时间/客户端定时发布。发布校验正文、转载来源、关联和可公开资源。修改当前已发布文章的正文/标题/可见性等内容，还须 `blog:article:publish` 并满足其范围，防止利用编辑绕过发布控制。

回收只设 deletedAt，保留原状态/正文/标签/slug/互动；回收站禁止编辑和发布。恢复固定为 `status=2` 下架、deletedAt=null，保留首次publishTime（从未发布过仍null），不会因恢复自动公开；之后显式发布。只读后台详情可查看回收站。

### 12.5 分类与标签模型

| 模型 | 字段与默认值 |
| --- | --- |
| CategoryWrite | categoryName:S(64)、slug:S(100) 必填；description:S(255)? 默认null，sortOrder:int32 默认0，status:0/1 默认1；PUT所有字段必填 |
| Category | categoryId:ID + CategoryWrite全字段 + createTime/updateTime:Time |
| CategoryPublic | categoryId:ID、categoryName:string、slug:string、description:string?、sortOrder:int32 |
| TagWrite | tagName:S(64)、slug:S(100) 均必填；POST/PUT相同 |
| Tag | tagId:ID + TagWrite全字段 + createTime/updateTime:Time |
| TagPublic | tagId:ID、tagName:string、slug:string |

categoryName/tagName 各自按库排序规则唯一；slug 各自区分大小写唯一。分类展示不等于文章授权。标签无status，不凭空增加启停接口。

### 12.6 评论与点赞模型

| 模型 | 字段与约束 |
| --- | --- |
| CommentCreate | content:string 必填，trim 后1..2000字符纯文本；parentId:ID? 可选默认null；articleId仅取路径，userId取会话 |
| CommentPublic | commentId/articleId:ID、parentId:ID?、author:{userId:ID,nickname:string,avatar:URL?}?、content:string、status:1或3、replyCount:string、createTime/updateTime:Time |
| CommentOwn | commentId/articleId/userId:ID、parentId:ID?、content:string、status:0/1/2/3、createTime/updateTime:Time；不含其他用户私人信息 |
| CommentAdmin | CommentOwn + author:{userId:ID,nickname:string,avatar:URL?}、articleTitle:string；已删除用户不物理清除，作者关联仍存在 |
| CommentReview | `{status:1或2}` 必填；1通过，2驳回，无审核备注字段 |
| LikeState | `{liked:boolean,likeCount:string}` |

前台评论只读取 PUBLIC 文章。节点自身及所有祖先须为 status=1 或3；祖先0/2时整个分支不可见。status=3 时 content 固定为“该评论已删除”、author=null，不泄露原内容和作者，replyCount只算当前可见直接子节点。status=0/2 的内容仅本人列表和有相应范围的后台可见；前台公开列表即使带 Token 也不混入自己的待审评论。

设计决定：采用**逐层分页**，不一次返回无限回复树。GET comments 不传parentId时仅根节点；传parentId时仅其直接回复，返回相同 Page。parentId 必须是该文章的可见节点，其他情况404。前端逐层加载；回复任意已通过节点，保留真实parentId，不把深层回复扁平重挂。replyCount用于展开按钮。

创建须同时满足：账号/权限有效、文章 PUBLIC、站点comment.enabled=true、文章allowComment=1；关闭评论返回409/STATE_CONFLICT，非PUBLIC文章返回404。parentId非null时要求同篇文章、节点status=1且祖先可见；跨文章/不存在父节点404，已变成占位或不可回复状态409。占位节点可展示已有回复但不再接受新回复。已关闭评论仅禁止新增，不隐藏已有可见评论。待审或驳回评论无公开回复入口。parentId/articleId/userId创建后不可修改，故不能移动评论或构造环。

状态：创建由服务端读取 reviewRequired 决定0待审或1通过；管理员/作者回复同样走评论创建，不默认免审。后台可将0/1/2审核为1或2，同目标幂等；3为终态，不能审核复活。本人删除任意自身0/1/2状态或后台管理删除，均在事务内将content覆盖为固定占位并置3，不物理删除、不改回复链；重复删除200。审核驳回已通过父评论时隐藏整分支；恢复通过后重新计算可见回复。管理删除使用同一占位状态是本契约的决定，无额外“管理员删除”枚举。

### 12.7 友链和配置模型

| 模型 | 字段与校验 |
| --- | --- |
| LinkWrite | linkName:S(100)、url:URL 必填；avatar:URL? 默认null、description:S(255)? 默认null、sortOrder:int32 默认0、status:0待审核/1展示/2驳回或下架默认0；PUT全部必填 |
| Link | linkId:ID + LinkWrite全字段 + createTime/updateTime:Time |
| LinkPublic | linkId:ID、linkName:string、url:URL、avatar:URL?、description:string?、sortOrder:int32 |
| Config | configKey:string、configValue:object、description:string?、isPublic:0或1、updateTime:Time |
| ConfigWrite | configValue:object、description:S(255)?、isPublic:0或1，均必填；仅支持下表三种键及字段，不接受任意JSON结构 |

友链url无唯一键，不虚构“相同URL必定409”的规则。URL仅保存/展示，不由服务器探测抓取。链接名不唯一。配置JSON为对象且最多16KiB（UTF-8）；属性外层和内部均拒绝未知字段，防止借config_value扩展无依据的系统。

| configKey | configValue完整形状 | 公开规则 |
| --- | --- | --- |
| site | `{title:S(100),description:S0(500),icp:S0(100),publicSecurityRecord:S0(100),startDate:date?}`，五项必填；startDate为合法YYYY-MM-DD或null | isPublic可为0/1；初始为1 |
| comment | `{enabled:boolean,reviewRequired:boolean}`，两项必填 | isPublic必须0；初始true/true，不向前端公开审核策略 |
| message | `{enabled:boolean,reviewRequired:boolean}`，两项必填 | isPublic必须0；初始true/true，关闭后保留历史查询、本人删除和后台审核 |

不存数据库、Redis、SMTP、第三方App Secret或对象存储凭据；未知键404。不提供任意新增/删除配置键接口。comment/message.enabled关闭后保留历史内容；message开关规则见12.10。前台通过文章allowComment及新增失败反馈交互；服务端仍检查站点开关，不依赖前端按钮是否显示。

### 12.8 文件模型与读取响应

blog_file没有公开标志或文章外键，不能仅凭fileId认为可匿名访问。文件 HTTP 业务仍待实现，默认本地存储契约为 storageType=local、bucket=""。现已按明确需求提供可选 OSS 配置及客户端基础设施，默认关闭，尚未接入文件 HTTP 业务或提供多存储切换 API；启用基础设施不迁移历史文件，也不把私有附件改成公开直链。

| 模型 | 字段与约束 |
| --- | --- |
| File | fileId/uploaderId:ID、originalName:string（1..255）、mimeType:string（≤100）、sizeBytes:string、storageType:"local"、createTime:Time、previewUrl:string（后台内容接口，非公开直链） |
| Upload | multipart只有file，单个、非空；不接uploaderId、文件路径、bucket、objectKey、isPublic |

设计决定：首期只允许PNG/JPEG图片，单文件≤10MiB、单次multipart总大小≤11MiB；扩展名白名单`.png/.jpg/.jpeg`必须与服务端内容识别一致，不信Content-Type。使用JDK ImageIO先读尺寸再解码验证真实图片，拒绝不合法结构/异常尺寸（宽高各≤10000、像素总数≤2500万）。不支持WebP/PDF/SVG/HTML/脚本/压缩包，当前POM未配WebP编解码或PDF解析器，不预装额外依赖。图片不承诺压缩转码；以后有其他附件需求再明确类型及校验契约。

原文件名仅展示，移除路径分隔符/控制字符，结果空或>255报400；服务端生成ASCII随机object_key（≤512），normalize后的绝对路径必须仍位于上传根目录内，防目录穿越和符号链接越界。存储根目录不能被Nginx/静态资源映射公开。File模型不返回bucket/objectKey/磁盘路径。

成功内容响应为原始文件字节，非Result：`Content-Type`取服务端确认的image/png或image/jpeg、`Content-Length`取实际字节数、`X-Content-Type-Options:nosniff`、`Content-Disposition:inline; filename*=UTF-8''...`安全编码文件名。首期不支持Range/分片/断点上传，忽略Range返回完整200；文件响应`Cache-Control:no-store`，避免下架/私密后缓存绕过。失败仍返回对应HTTP的JSON Result。前端携带Bearer fetch后台内容并用临时object URL预览，不能把Token拼接进img URL。

### 12.9 主页与简历模型

新增Write/Create/Edit模型均为明确的字段白名单；本章写接口要求表中字段完整出现，可空字段用null清空。响应中的createTime/updateTime由数据库维护，不接收客户端值。Date使用合法YYYY-MM-DD，1000..9999年，与Time区分。

| 模型 | 完整字段与约束 |
| --- | --- |
| `HomeProfileWrite` | `userId`:ID（站点主人账号；必须存在且启用，仅后台管理者可选择）；`nickname`:S(64)（公开昵称）；`tagline`:S0(100)（个人标签或一句话介绍）；`description`:S(500)?（公开简介，null清空）；`avatar`:URL?（公开头像，外部HTTP(S)资源）；`website`:URL?（个人网站）；`publicEmail`:string&#124;null（≤254，合法联系邮箱；明确自愿公开，不自动读取登录邮箱）；`githubUrl`:URL?（GitHub主页）；`location`:S(100)?（自愿公开的所在地） |
| `HomeProfile` | `profileId`:ID（固定为1）；`userId`:ID（站点主人账号；必须存在且启用，仅后台管理者可选择）；`nickname`:S(64)（公开昵称）；`tagline`:S0(100)（个人标签或一句话介绍）；`description`:S(500)?（公开简介，null清空）；`avatar`:URL?（公开头像，外部HTTP(S)资源）；`website`:URL?（个人网站）；`publicEmail`:string&#124;null（≤254，合法联系邮箱；明确自愿公开，不自动读取登录邮箱）；`githubUrl`:URL?（GitHub主页）；`location`:S(100)?（自愿公开的所在地）；`createTime`:Time（创建时间，UTC）；`updateTime`:Time（最后更新时间，UTC） |
| `HomeProfilePublic` | `nickname`:S(64)（公开昵称）；`tagline`:S0(100)（个人标签或一句话介绍）；`description`:S(500)?（公开简介，null清空）；`avatar`:URL?（公开头像，外部HTTP(S)资源）；`website`:URL?（个人网站）；`publicEmail`:string&#124;null（≤254，合法联系邮箱；明确自愿公开，不自动读取登录邮箱）；`githubUrl`:URL?（GitHub主页）；`location`:S(100)?（自愿公开的所在地） |
| `SocialWrite` | `name`:S(64)（社交平台或链接名称）；`icon`:string&#124;null（≤2048；外部HTTP(S)图标URL或前端注册图标键，禁止任意HTML）；`url`:URL（外部HTTP(S)目标链接，邮箱链接可使用经过校验的mailto:邮箱）；`sortOrder`:int32（越小越靠前）；`status`:integer（0隐藏 1展示） |
| `Social` | `socialId`:ID（社交链接ID）；`name`:S(64)（社交平台或链接名称）；`icon`:string&#124;null（≤2048；外部HTTP(S)图标URL或前端注册图标键，禁止任意HTML）；`url`:URL（外部HTTP(S)目标链接，邮箱链接可使用经过校验的mailto:邮箱）；`sortOrder`:int32（越小越靠前）；`status`:integer（0隐藏 1展示）；`createTime`:Time（创建时间，UTC）；`updateTime`:Time（最后更新时间，UTC） |
| `SocialPublic` | `socialId`:ID（社交链接ID）；`name`:S(64)（社交平台或链接名称）；`icon`:string&#124;null（≤2048；外部HTTP(S)图标URL或前端注册图标键，禁止任意HTML）；`url`:URL（外部HTTP(S)目标链接，邮箱链接可使用经过校验的mailto:邮箱）；`sortOrder`:int32（越小越靠前） |
| `ExperienceWrite` | `experienceType`:integer（0教育 1工作/实习 2项目）；`title`:S(200)（学校、公司或项目名称）；`subtitle`:S(200)?（专业、职位或项目角色）；`logoUrl`:URL?（外部HTTP(S)图标URL）；`content`:string（非空Markdown，UTF-8最多65535字节，与MySQL TEXT容量一致）；`startDate`:date（合法YYYY-MM-DD，1000..9999年）；`endDate`:date&#124;null（结束日期不得早于开始；null表示至今）；`sortOrder`:int32（越小越靠前）；`status`:integer（0隐藏 1展示） |
| `Experience` | `experienceId`:ID（经历ID）；`experienceType`:integer（0教育 1工作/实习 2项目）；`title`:S(200)（学校、公司或项目名称）；`subtitle`:S(200)?（专业、职位或项目角色）；`logoUrl`:URL?（外部HTTP(S)图标URL）；`content`:string（非空Markdown，UTF-8最多65535字节，与MySQL TEXT容量一致）；`startDate`:date（合法YYYY-MM-DD，1000..9999年）；`endDate`:date&#124;null（结束日期不得早于开始；null表示至今）；`sortOrder`:int32（越小越靠前）；`status`:integer（0隐藏 1展示）；`createTime`:Time（创建时间，UTC）；`updateTime`:Time（最后更新时间，UTC） |
| `ExperiencePublic` | `experienceId`:ID（经历ID）；`experienceType`:integer（0教育 1工作/实习 2项目）；`title`:S(200)（学校、公司或项目名称）；`subtitle`:S(200)?（专业、职位或项目角色）；`logoUrl`:URL?（外部HTTP(S)图标URL）；`startDate`:date（合法YYYY-MM-DD，1000..9999年）；`endDate`:date&#124;null（结束日期不得早于开始；null表示至今）；`sortOrder`:int32（越小越靠前）；`htmlContent`:string（安全渲染HTML） |
| `SkillWrite` | `name`:S(64)（技能名称）；`description`:S(500)?（技能描述）；`icon`:string&#124;null（≤2048；外部HTTP(S)URL或注册图标键）；`sortOrder`:int32（越小越靠前）；`status`:integer（0隐藏 1展示） |
| `Skill` | `skillId`:ID（技能ID）；`name`:S(64)（技能名称）；`description`:S(500)?（技能描述）；`icon`:string&#124;null（≤2048；外部HTTP(S)URL或注册图标键）；`sortOrder`:int32（越小越靠前）；`status`:integer（0隐藏 1展示）；`createTime`:Time（创建时间，UTC）；`updateTime`:Time（最后更新时间，UTC） |
| `SkillPublic` | `skillId`:ID（技能ID）；`name`:S(64)（技能名称）；`description`:S(500)?（技能描述）；`icon`:string&#124;null（≤2048；外部HTTP(S)URL或注册图标键）；`sortOrder`:int32（越小越靠前） |

主页固定profileId=1，userId只供后台选择本站主人；公开响应没有userId或登录邮箱。未设置资料时公开/后台GET返回data=null，后台GET仍提供空资源X-Revision，PUT以该版本首次创建。社交、经历、技能为全站资源，需要ALL管理权限，不根据站点主人ID自动获得修改权。社交url是通用URL规则的明确例外：允许mailto:后接单一合法邮箱，拒绝额外参数、控制字符；其他展示URL仍只允许外部HTTP(S)。

### 12.10 留言模型、父链与悄悄话

| 模型 | 完整字段与约束 |
| --- | --- |
| `MessageCreate` | `parentId`:ID&#124;null（直接回复目标；根留言为null，回复创建后不可改）；`content`:S(2000)（去首尾空白后非空；Markdown保留原文内部空白）；`contentFormat`:integer（0纯文本 1Markdown）；`isSecret`:integer（0公开 1悄悄话；私密回复必须继承完整父链限制）；`notifyReply`:integer（0/1；见留言通知边界） |
| `MessageEdit` | `content`:S(2000)（去首尾空白后非空；Markdown保留原文内部空白）；`contentFormat`:integer（0纯文本 1Markdown）；`notifyReply`:integer（0/1；见留言通知边界） |
| `MessagePublic` | `messageId`:ID（留言ID）；`parentId`:ID&#124;null（直接回复目标；根留言为null）；`author`:object&#124;null（当前用户公开资料；删除占位时为null）；`content`:string（原文；删除占位固定为该留言已删除）；`htmlContent`:string（安全渲染HTML；纯文本转义并保留换行）；`contentFormat`:integer（0纯文本 1Markdown）；`isSecret`:integer（0公开 1悄悄话）；`status`:integer（0待审核 1通过 2驳回 3删除占位）；`editedAt`:Time&#124;null（最近作者编辑时间，未编辑为null）；`isOwn`:boolean（按当前会话计算，匿名恒为false）；`createTime`:Time（创建时间，UTC）；`updateTime`:Time（最后更新时间，UTC） |
| `MessagePrivate` | `messageId`:ID（留言ID）；`parentId`:ID&#124;null（直接回复目标；根留言为null）；`author`:object&#124;null（当前用户公开资料；删除占位时为null）；`content`:string（原文；删除占位固定为该留言已删除）；`htmlContent`:string（安全渲染HTML；纯文本转义并保留换行）；`contentFormat`:integer（0纯文本 1Markdown）；`isSecret`:integer（0公开 1悄悄话）；`status`:integer（0待审核 1通过 2驳回 3删除占位）；`editedAt`:Time&#124;null（最近作者编辑时间，未编辑为null）；`isOwn`:boolean（按当前会话计算，匿名恒为false）；`createTime`:Time（创建时间，UTC）；`updateTime`:Time（最后更新时间，UTC）；`notifyReply`:integer（0不通知 1请求回复通知；不是邮箱验证凭据） |

留言状态0待审核、1通过、2驳回、3删除占位，与评论一致。公开查询使用parentId逐层分页，每页1..50；默认根节点，不自动展开整棵树。父关系创建后不可改，父链不可有环；rootId、parentNickname和管理员标志不作为可写字段或数据库副本。author仅公开昵称/头像，不返回登录邮箱、IP、UA；删除占位author=null，content固定“该留言已删除”，htmlContent只含转义后的占位。isOwn只由有效会话与原user_id计算，不能作为服务端唯一授权依据。

悄悄话归属从父链推导：根私密留言的线程接收者是该根user_id；在公开线程新建私密分支时，接收者是最早私密节点的user_id。前台读取私密节点及其后代仅允许该接收者与节点作者本人，父链仍需逐层可见；管理者必须通过后台入口，满足admin:access及当前query/review/moderate权限的ALL范围后读取或操作，不因携带管理员Token放宽前台过滤。后台回复的user_id始终是实际操作人，不冒用原作者；原私密发起者可以沿同一私密祖先读取审核通过的后台回复。回复不能借isSecret=0放宽祖先私密限制；前台发起的回复统一继承私密祖先标记。本人待审/驳回记录可从本人列表读取，不代表匿名可见，不能借可见后代泄露隐藏祖先原文。

编辑只允许content/contentFormat/notifyReply，保留私密性和父关系，写editedAt并重新审核。删除本人或后台管理删除均用占位保留回复链，清除原内容并关闭通知；不存在物理级联整树删除或改挂父节点。审核不能复活删除占位，通过私密节点不将其改成公开。

notifyReply仅表示通知意愿，不能证明sys_user.email已验证。当前账号体系没有登录邮箱验证状态；只有该用户存在status=1且verifiedAt非空的邮件订阅时，才能使用其已确认邮箱发送不含私密原文的回复提醒，否则跳过，不把登录邮箱当收件地址。审核实际转为通过并提交后再发送；发送失败不回滚已发布留言，不声称邮件与MySQL同事务。新文章邮件仅向启用用户的已激活订阅发送，首次发布提交后触发；下架后重发、普通编辑不重复推送。

留言版本只包含userId、parentId、content、contentFormat、isSecret、notifyReply、status、editedAt；排除展示用户资料与审计时间。创建/编辑/后台回复先锁message配置，再按祖先至目标顺序锁留言，锁后重查完整可见性与版本；同一线程的审核/删除采用相同父链锁顺序。message.enabled=false拒绝新增/编辑/回复，保留历史阅读、本人删除及后台审核。

### 12.11 音乐模型

| 模型 | 完整字段与约束 |
| --- | --- |
| `MusicWrite` | `title`:S(200)（曲目标题）；`artist`:S(200)?（作者或歌手）；`durationSeconds`:integer&#124;null（0..4294967295秒，未知为null）；`coverUrl`:URL?（外部HTTP(S)封面）；`audioUrl`:URL（外部HTTP(S)音频URL；保存不触发服务端下载）；`lyricUrl`:URL?（歌词URL；无歌词时为null）；`lyricType`:string&#124;null（lrc/json/txt；必须与lyricUrl同时为空或同时非空）；`sortOrder`:int32（越小越靠前）；`status`:integer（0隐藏 1展示） |
| `Music` | `musicId`:ID（音乐ID）；`title`:S(200)（曲目标题）；`artist`:S(200)?（作者或歌手）；`durationSeconds`:integer&#124;null（0..4294967295秒，未知为null）；`coverUrl`:URL?（外部HTTP(S)封面）；`audioUrl`:URL（外部HTTP(S)音频URL；保存不触发服务端下载）；`lyricUrl`:URL?（歌词URL；无歌词时为null）；`lyricType`:string&#124;null（lrc/json/txt；必须与lyricUrl同时为空或同时非空）；`sortOrder`:int32（越小越靠前）；`status`:integer（0隐藏 1展示）；`createTime`:Time（创建时间，UTC）；`updateTime`:Time（最后更新时间，UTC） |
| `MusicPublic` | `musicId`:ID（音乐ID）；`title`:S(200)（曲目标题）；`artist`:S(200)?（作者或歌手）；`durationSeconds`:integer&#124;null（0..4294967295秒，未知为null）；`coverUrl`:URL?（外部HTTP(S)封面）；`audioUrl`:URL（外部HTTP(S)音频URL；保存不触发服务端下载）；`lyricUrl`:URL?（歌词URL；无歌词时为null）；`lyricType`:string&#124;null（lrc/json/txt；必须与lyricUrl同时为空或同时非空）；`sortOrder`:int32（越小越靠前） |

lyricUrl与lyricType必须同时为null或同时非null，类型只允许lrc/json/txt；是否有歌词由URL判断。播放列表只返回status=1，sortOrder ASC,musicId ASC。所有媒体URL为外部HTTP(S)资源，不接受本站附件API路径、磁盘路径或任意脚本；服务器不下载或代理探测URL。现有文件接口仍只上传PNG/JPEG，新增曲目不意味着支持音频/歌词本地上传。删除曲目只删除元数据。

### 12.12 邮件订阅与票据模型

| 模型 | 完整字段与约束 |
| --- | --- |
| `Subscription` | `subscriptionId`:ID（订阅ID）；`email`:string（订阅邮箱，≤254；服务端trim并转小写）；`status`:integer（0待验证 1已订阅 2已退订）；`verifiedAt`:Time&#124;null（当前邮箱验证成功时间）；`subscribeTime`:Time&#124;null（最近激活时间）；`unsubscribeTime`:Time&#124;null（最近退订时间）；`createTime`:Time（创建时间，UTC）；`updateTime`:Time（最后更新时间，UTC） |
| `SubscriptionAdmin` | `userId`:ID（所属本站用户）；`subscriptionId`:ID（订阅ID）；`email`:string（订阅邮箱，≤254；服务端trim并转小写）；`status`:integer（0待验证 1已订阅 2已退订）；`verifiedAt`:Time&#124;null（当前邮箱验证成功时间）；`subscribeTime`:Time&#124;null（最近激活时间）；`unsubscribeTime`:Time&#124;null（最近退订时间）；`createTime`:Time（创建时间，UTC）；`updateTime`:Time（最后更新时间，UTC） |

status为0待验证、1已订阅、2已退订。每用户一条、每规范化邮箱一条，昵称读取用户公开资料，不复制旧nickname列。待验证或换邮箱时verifiedAt/subscribeTime/unsubscribeTime清空；激活必须有当前邮箱验证时间和订阅时间，unsubscribeTime=null；退订写时间但可保留原验证记录。任何请求都不能写userId/verifiedAt/审计时间，后台状态接口也不能更换邮箱或跳过验证。

确认链接使用固定前端完成页 `#confirmTicket=<opaque>`，前端读fragment后立即清除，携带本站Bearer向确认接口提交JSON票据。票据24小时有效，Redis中只绑定本次申请的用户、订阅ID、邮箱与申请版本。申请版本是Redis生成的随机标识，按用户保存当前有效值，重发时原子替换，不能仅靠毫秒updateTime区分申请；过期、复用、版本或身份不符统一400/SUBSCRIPTION_TICKET_INVALID，不泄露具体原因。锁用户及订阅后原子消费票据，再提交数据库；数据库失败时需要重新申请，不用额外表伪造跨Redis/MySQL事务。申请/重发按账号与可信IP限流，SMTP或Redis失败503，待验证数据可保留，不能回复已激活。相同已激活邮箱的PUT不再发确认邮件。

邮件退订票据30天有效，Redis绑定订阅ID、邮箱、verifiedAt、本轮subscribeTime，只允许退订该记录；有效票据可重复调用保持幂等。邮件链接由固定前端页面读取fragment并让用户确认，再POST退订；GET不修改订阅。重新申请/换邮箱/再激活或删除记录后，旧轮次票据失效。服务端不向本人状态接口或后台列表返回票据，不在访问日志中记录票据。实名登录只用于本站身份，邮箱确认不创建会话、不改变登录邮箱或角色。XML Feed独立于邮箱订阅，公开阅读不需要票据。

订阅列表和详情暴露邮箱，仅ALL后台管理或本人可读；不保留旧无分页全量订阅邮箱接口。订阅写操作锁当前用户与对应订阅，唯一冲突整笔409，不把相同邮箱自动转移到另一账号。X-Revision覆盖userId/email/status/verifiedAt/subscribeTime/unsubscribeTime；重复相同状态不覆盖时间。

### 12.13 访问统计、报表与时间口径

| 模型 | 完整字段与约束 |
| --- | --- |
| `TrackingResult` | `recorded`:boolean（数据库成功记录本次浏览后为true，不返回访客会话） |
| `Visitor` | `visitorId`:ID（统计访客ID，不是登录身份）；`fingerprint`:string（ASCII统计标识，不可作为鉴权凭据）；`ipAddress`:string&#124;null（最近IP，仅后台可见）；`userAgent`:string&#124;null（≤1024，最近User-Agent）；`country`:string&#124;null（国家）；`province`:string&#124;null（省份）；`city`:string&#124;null（城市）；`isBlocked`:integer（数据库封禁标记0/1；实际生效还需检查blockedUntil）；`blockedUntil`:Time&#124;null（封禁结束，已封禁且null表示永久）；`firstVisitTime`:Time（首次访问时间）；`lastVisitTime`:Time（最近访问时间）；`createTime`:Time（创建时间，UTC）；`updateTime`:Time（最后更新时间，UTC） |
| `PageView` | `viewId`:ID（浏览记录ID）；`visitorId`:ID&#124;null（统计访客，清理后可空）；`userId`:ID&#124;null（采集时服务端确认的本站用户，匿名或清理后为空）；`articleId`:ID&#124;null（通过公开文章与规范路径核验的文章ID）；`siteCode`:string（home/blog/cv）；`pagePath`:S(2048)（不含敏感查询参数的规范路径）；`pageTitle`:S(200)?（页面标题）；`referer`:URL?（已脱敏的来源URL）；`ipAddress`:string&#124;null（仅后台可见）；`userAgent`:string&#124;null（≤1024）；`viewTime`:Time（采集时间） |
| `PublicStatistics` | `articleCount`:string（PUBLIC文章数量）；`categoryCount`:string（可展示且至少含一篇PUBLIC文章的分类数）；`tagCount`:string（至少关联一篇PUBLIC文章的标签数）；`pageViewCount`:string（blog站点保留浏览明细条数；不等于累计文章阅读数）；`todayPageViewCount`:string（UTC当日blog浏览明细数）；`visitorCount`:string（blog浏览明细中不同非NULL visitorId数量） |
| `AdminStatistics` | `pageViewCount`:string（筛选日期内保留浏览明细条数）；`visitorCount`:string（筛选日期内不同非NULL visitorId；不按IP或账号去重）；`newVisitorCount`:string（firstVisitTime在日期区间且符合站点访问条件的访客数）；`articleCount`:string（当前未回收文章数，不受访问日期筛选）；`publishedArticleCount`:string（当前PUBLIC文章数）；`commentCount`:string（当前非删除占位评论数，含待审/驳回）；`messageCount`:string（当前非删除占位留言数，含私密/待审/驳回）；`pendingCommentCount`:string（当前status=0的评论数）；`pendingMessageCount`:string（当前status=0的留言数） |
| `ViewTrend` | `date`:date（UTC自然日，区间内逐日补零）；`pageViewCount`:string（该日浏览明细条数）；`visitorCount`:string（该日不同非NULL visitorId数） |
| `VisitorTrend` | `date`:date（UTC自然日）；`newVisitorCount`:string（当天首次访问的访客数）；`cumulativeNewVisitorCount`:string（仅所选区间内从begin累计的新访客数；不是全历史总量） |
| `ProvinceMetric` | `province`:string&#124;null（最新访客省份；null表示未知）；`visitorCount`:string（区间内访问的不同非NULL visitorId数） |
| `ArticleViewMetric` | `articleId`:ID（未回收文章ID）；`title`:string（当前标题）；`slug`:string（当前slug）；`pageViewCount`:string（日期范围内文章浏览明细数，不是view_count累计值） |

analytics/page-views是公开采集入口，不颁发Token或可信身份。fingerprint是浏览器随机统计标识，可伪造；不用于账号封禁、内容归属或恢复旧内容。siteCode只能home/blog/cv，由服务端与配置的Origin/站点规范路径核验；articleId必须属于PUBLIC文章并与页面slug一致，未关联文章时为null。userId仅来自有效Bearer，客户端不能上传IP/UA/访问时间。pagePath与referer存储前剔除query及fragment，不能写入凭据。事务写入访客及一次浏览后返回recorded=true；采集失败不影响前端公开内容读取。

PV是保留的page_view条数；UV是所选区间内不同非null visitorId数量，不能把IP、session或sys_user计数当成同一指标。删除访客将关联置null，会使UV减少，PV不变；删除浏览明细改变基于明细的报表；不反向扣减blog_article.view_count。文章详情成功读取每次原子增加view_count，独立采集POST只写明细，避免一页计两次累计阅读。封禁统计标识仅约束采集入口，不承诺阻止伪造者阅读网站。

报表begin/end为UTC自然日，必须同时出现或同时缺失；缺失默认UTC今天，显式区间含两端且最多367天，end=9999-12-31使用仅上限不外溢的条件。明细查询begin/end为UTC Time，半开区间[begin,end)，两者都传时begin<end。province为访客最新所在地，不是历史地理快照。趋势返回数组对象，不沿用旧逗号拼接数列；无数据日期补字符串0。newVisitorCount按first_visit_time；cumulativeNewVisitorCount仅在当前选区间累加。所有BIGINT/计数为十进制字符串。

统计时间段只影响访问数据；后台总览的文章、评论、留言及待审计数为当前快照。公开统计只汇总PUBLIC内容和blog访问数据，不公开草稿、私密文章、未审核互动或访客原始字段。后台文章区间排行来自page_view，不同于热门榜按候选文章累计阅读/点赞排序。

### 12.14 操作日志模型

| 模型 | 完整字段与约束 |
| --- | --- |
| `OperationLog` | `logId`:ID（操作日志ID）；`userId`:ID&#124;null（操作人；物理清理后为空）；`operationType`:string（≤32，ASCII操作类型）；`operationTarget`:string（≤100，ASCII模块或资源）；`targetId`:ID&#124;null（目标数字ID，其他情况为空）；`permissionCode`:string&#124;null（本次检查的权限码快照）；`result`:integer（0失败 1成功）；`errorMessage`:string&#124;null（≤500，脱敏错误摘要）；`ipAddress`:string&#124;null（操作IP）；`operationTime`:Time（操作时间） |
| `OperationLogDetail` | `logId`:ID（操作日志ID）；`userId`:ID&#124;null（操作人；物理清理后为空）；`operationType`:string（≤32，ASCII操作类型）；`operationTarget`:string（≤100，ASCII模块或资源）；`targetId`:ID&#124;null（目标数字ID，其他情况为空）；`permissionCode`:string&#124;null（本次检查的权限码快照）；`result`:integer（0失败 1成功）；`errorMessage`:string&#124;null（≤500，脱敏错误摘要）；`ipAddress`:string&#124;null（操作IP）；`operationTime`:Time（操作时间）；`operationData`:object&#124;null（白名单脱敏摘要；UTF-8≤16KiB，不是完整请求体） |

仅服务端写日志，记录实际会话操作人、模块、动作、目标、权限码快照和结果；不接受客户端创建、修改日志。列表不返回大段摘要，详情operationData为白名单脱敏JSON对象或null。不得保存密码、授权头、OAuth code/state/ticket、RSS票据、密钥、原始SQL或异常堆栈。user_id外键SET NULL保证账号物理清理后保留日志；日志不承担授权凭据作用。

日志为不可编辑记录，查询和删除不需要X-Revision；清理需sys:log:delete+ALL，删除行为追加一条新的审计，不递归记录写日志本身。业务成功日志随数据库变更同事务写入；业务失败回滚后另行写脱敏失败日志，不能用已回滚事务保存失败记录。当前后台用户、角色、权限、菜单的13个写方法已实际写入审计：成功日志写入失败使业务回滚，回滚后由独立虚拟线程事务记录失败；operationData固定为null，错误只记固定白名单码。尚未进入业务写方法的参数绑定/校验/HTTP权限拒绝不产生该业务审计。失败投递为进程内异步任务，数据库或进程故障不提供跨宕机可靠投递保证，写入失败输出固定错误；第20章查询/清理API仍待实现。

### 12.15 文章导航、推荐与热门榜

| 模型 | 完整字段与约束 |
| --- | --- |
| `HotArticle` | `articleId`:ID（PUBLIC文章ID）；`title`:string（标题）；`slug`:string（URL标识）；`summary`:string（摘要）；`coverUrl`:URL?（外部公开封面）；`viewCount`:string（文章累计阅读数）；`likeCount`:string（当前点赞明细数）；`hotValue`:string（按metric选择的累计阅读数或点赞数）；`publishTime`:Time（首次发布时间） |

ArticlePublicDetail在原有字段上增加wordCount:string、readingMinutes:integer（最少1）、prevArticle/nextArticle:ArticleNavigation|null、relatedArticles:ArticleNavigation[]。ArticleNavigation仅含articleId:ID、title:string、slug:string；不返回私密或后台信息。字数按Markdown解析后的可见正文统计：每个汉字计1、每个连续英文字母/数字词计1，忽略标记、URL目标和代码块；readingMinutes=max(1,ceil(wordCount/300))，都是读取时派生，不新增SQL列或可写计数。

上一篇按(publishTime,articleId)早于当前文章的最近PUBLIC文章，下一篇取晚于当前文章的最近PUBLIC文章；时间相同用articleId稳定排序，无结果null。相关文章取相同且可展示category的PUBLIC文章，排除自身，publishTime DESC,articleId DESC，最多6篇；未分类或分类隐藏时[]。这些派生字段不加入文章X-Revision，不增加候选文章浏览计数。

第7.11节用period=site/month、metric=view/like表达旧站4榜。month是本月首次发布的候选文章按累计数排序，保留旧SQL的真实含义；不是时间段内新增热度。时间段访问排行另见第19章。搜索扩展为标题、摘要和Markdown原文的字面包含，不强制引入中文全文索引或旧异步计数缓存。

<a id="business"></a>

## 13. OAuth、业务事务与一致性

<a id="oauth-flow"></a>

### 13.1 OAuth 浏览器闭环

仅支持服务端启用白名单中、可通过 GET 回调的普通 OAuth 平台；source 路径使用 QQ/GITHUB/GITEE 等大写值（不是承诺三者已接入），未知/未启用平台统一404。任意第三方授权 URL、redirectUri、returnUrl、userId、uuid 不能由客户端指定。每平台仅一个应用；身份始终为服务端验证所得 `source + AuthUser.uuid`，区分大小写；不能以昵称或邮箱合并。

1. 前端以 `fetch(...,{credentials:"include"})` 调 authorize。后端设置/读取随机 `__Host-dl-oauth-browser` Cookie：Secure、HttpOnly、SameSite=Lax、Path=/、无 Domain，最多存活 10 分钟，仅标识发起浏览器。前后端需同站部署，推荐同源 `/api` 反代；BIND 同时携带 Authorization。后端校验请求 Origin 为配置前端。
2. 服务端生成不可预测、至少 128 bit 随机 state。Redis 存有效期 300 秒的流程：state 摘要、浏览器标识摘要、source、operation、发起时间，BIND 还包括从 Token 取得的 userId 和原会话标识摘要。一个浏览器允许多个流程，逐 state 区分。返回 authorizationUrl，LOGIN 可整页跳转；BIND 必须由原页面打开授权弹窗（不使用 iframe），原页面保留内存 Token；URL 带 state。
3. 平台回调本后端固定地址 `/api/v1/auth/oauth/{source}/callback`。请求包含 state 和平台成功参数（如 code；QQ 等由选定适配器处理），或 error/error_description。处理器先校验 source、Cookie 和 Redis 流程中记录的 operation/目标会话；平台回调不携带本站 Bearer，BIND 的原会话由服务端保存的会话引用查询验证；用 Redis 原子 compare-and-consume 将本站流程从未消费改为已消费，匹配后只能有一个请求成功。浏览器不匹配不消费合法流程；重放/过期返回 OAUTH_STATE_INVALID，不能换码或改库。
4. **不得把 JustAuth 的存在性检查当成一次性消费。** `AuthStateCache.cache` timeout 单位毫秒，适配 Redis TTL 不得误当秒。本站流程与 JustAuth 所需 state 缓存分键：先原子消费本站流程，保留该次处理需要的 JustAuth 校验值，调用 JustAuth 保持 state 校验开启，并在本次成功/失败结束时删除该缓存值。失败也不恢复已消费流程，用户重新发起。并发请求在调用平台前即被本站消费门禁拒绝。
5. JustAuth 验证平台授权并获取身份后再处理业务。AuthUser.uuid必须非空白且≤255字符，否则502；除拒绝非法值外原样保留，不trim、改大小写或重选身份策略。LOGIN：已有绑定取本站启用用户；没有绑定则生成唯一 username、校验初始化昵称/头像、password_hash=NULL、email=NULL，在同一事务创建用户+绑定+member。昵称过长截为合法展示长度、空昵称用“用户”；不合法头像丢弃为 null。账号 username 用服务端随机标识并处理唯一键冲突。并发相同身份注册冲突整笔回滚，再按 source+uuid 查询唯一赢家并检查启用状态；不能覆盖其归属。
6. BIND：回调再次检查原会话仍有效、用户相同且发起认证仍在 5 分钟窗口内；不在回调中新建其他用户或切换账号。锁用户后写绑定。同一用户相同 source+uuid 已绑定可作为成功；属于他人或同用户同平台另一个 uuid 为 OAUTH_BIND_CONFLICT，绝不转移绑定。
7. 成功后只生成 60 秒、一次性的随机 ticket，在 Redis 绑定同浏览器、operation、结果 userId（BIND 还绑定原会话）。303 跳到**后端配置的固定前端页面** `/oauth/complete#ticket=<opaque>`。回调不把本站 Token、平台 code 或第三方 Token放在跳转 URL；设置 no-store、Referrer-Policy:no-referrer。
8. LOGIN 完成页读 fragment 后立即 history.replaceState 移除 ticket，以 credentials 调 exchange。BIND 完成页通过 postMessage 将 ticket 发给发起弹窗的原页面（精确 targetOrigin）；原页面校验 event.origin、event.source 为刚开的弹窗和对应流程后，携带原内存 Token 与 credentials 调 exchange。弹窗不接收本站 Token、不在 URL 或存储中传 Token。后端原子校验并消费 ticket，再检查用户启用；LOGIN 此时在用户行锁内重查该 source+uuid 仍绑定同一用户，再调用 Sa-Token 创建本站 Session，并更新最近登录时间，认证时间记为已验证第三方身份的时间（不能借延迟交换刷新）；BIND 必须带原 Token 且返回绑定结果。重放/过期/浏览器不符统一 OAUTH_STATE_INVALID。网络响应丢失需重新发起，不能再兑换。

失败回调：存在已验证且刚消费的本站流程时，303 固定 `/oauth/complete#error=<本文错误码>`；不得透传平台 error_description 或任意跳转地址。无可信 state/浏览器关系时直接以对应 HTTP JSON 错误结束，不重定向。前端仅显示通用失败并允许重试；无固定前端地址时禁止启用该平台。平台取消同样消费流程，结果为 OAUTH_DENIED。绑定已经提交而结果 ticket 丢失时，通过 GET /me/oauth-bindings 确认，不回滚已完成绑定。

解绑/清除密码必须锁定同一 sys_user 行，串行检查跨表登录方式：有效 BCrypt 密码或至少一个仍被后端白名单启用、应用凭据配置完整的平台绑定。此处检查配置可用性，不要求实时探测平台在线，不能只数 oauth 行。服务配置停用平台前检查受影响的纯第三方用户并安排其他登录方式；平台短时故障不作为自动解绑理由。首期不提供管理员绕过最后登录方式保护的接口。

BIND 必须保留发起页面：若原页面关闭、刷新、原 Token 丢失或会话过期，则不允许仅凭浏览器 Cookie 恢复本站身份；重新登录后通过绑定列表确认结果，再按需要重新发起。完成页的 postMessage 只传本次票据和白名单结果，不监听任意窗口发来的登录 Token。LOGIN 整页跳转返回后可创建新内存会话。

### 13.2 授权、菜单与状态变化

新建角色默认无菜单、无权限；分配给用户不隐式授予权限。角色删除级联清理三类授权关联，已使用的自定义角色仍允许删除但撤销受影响用户授权；内置角色禁止删除（STATE_CONFLICT）。删除菜单正引用的权限返回 RESOURCE_IN_USE，不静默移除门禁；未被菜单引用的自定义权限删除时清理 role_permission 并失效缓存。已用于后端鉴权的固定权限禁止删除，未知字典记录不能让后端按任意 URL 动态放行。

授权写入按同一事务完整替换 role_permission 和 role_menu；menuIds 自动补齐祖先并以最终集合返回。选择的菜单及祖先需存在、启用；M/B 的 permissionId 必须在本次显式 permissionIds 中且权限启用，不能因为用户自己有权限就给该角色漏配。permissionIds 允许没有菜单的独立接口权限。保存不自动扩大权限集合；未满足返回 VALIDATION_ERROR。角色禁用后保留关联，恢复启用重新计算。

菜单写操作统一在一个数据库事务中锁定菜单结构检查点（同[鉴权规则](#auth-rules)固定角色行，先于节点锁），检查自引用、移到后代、父类型和全部后代受类型变更影响的约束，防止并发移动产生环。有子节点时删除返回 RESOURCE_IN_USE；叶子删除级联 role_menu。移动后的角色授权不自动获得新祖先，没有完整祖先授权的分支不可见，需显式重新保存角色 grants。修改页面/按钮门禁后重新计算菜单，不能把新权限默默授予旧角色。

`GET /me/menus`：先判断启用账号和有效 admin:access；取所有有效角色所授权节点并集，M/B 再与用户有效权限并集交集；逐祖先检查授权/启用状态，剔除无可访问 M 的空 D。允许不同有效角色贡献菜单和权限。isVisible=0 的已授权路由仍返回；B 返回节点供操作能力使用，但前端不得生成路由。禁用父节点截断整支；禁用菜单不影响独立接口授权。前端只解析注册组件，不动态执行数据库内容。

### 13.3 文章过滤、查询与状态规则

keyword按标题、摘要和Markdown原文字面包含；仅传keyword时关联正文，不要求MySQL全文索引。查询参数化并转义LIKE的百分号、下划线和反斜线，不把输入当SQL或通配符。后台排序固定；热门榜、导航、推荐及派生阅读统计见第7.11和12.15节。month 必须为合法年/月，年份1000..9999并转换UTC半开区间；9999-12只设下界，避免生成超出DATETIME范围的上界；不接受任意日期SQL。前台按隐藏/不存在分类筛选返回空页，按不存在标签筛选返回空页。

<a id="file-consistency"></a>

### 13.4 文件引用、访问控制与存储一致性

私密/草稿/下架/回收站附件不得通过前台路径读取，即使持有Token也404。通过后台文章上下文读取时同时满足文章和文件权限范围；只允许文章作者身份不等于已获跨用户文件权限。上传者本人的后台原文件预览与文章公开状态无关。文件表没有独立可变资源，删除不要求If-Revision，但必须执行[文件存储一致性](#file-consistency)引用检查及锁。

设计决定：本站附件引用仅支持Markdown链接/图片和文章coverUrl，规范路径为 `/api/v1/blog/articles/{articleId}/files/{fileId}/content`；必须是当前文章articleId，不能指向另一篇文章的上下文。创建草稿后取得articleId，再上传/编辑引用；POST新文章不能附带未知articleId的本站文件URL。后端解析Markdown语法树（含清理后保留的HTML链接/图片）和coverUrl确认引用集合，不以文本随意包含数字作为引用证据。相同路径的绝对本站URL归一成规范相对路径；其余本站文件路径/编码绕过/存储直链拒绝，外部URL不映射为内部文件。

文章写入时，每个引用必须存在，写作者须有`blog:file:query`及对应文件范围；不能通过引用他人私密文件让其公开。发布时再检查所有引用可用。前台HTML保留上述公开上下文；后台详情HTML将这些路径改为相应`/admin/articles/.../content`，前端需Bearer fetch后替换临时object URL，原始Markdown不改写。一个文件被多篇文章引用时，只要某篇PUBLIC且确实引用，就可从该篇上下文公开读取；**需要私密隔离时必须另传一份文件，不能在公开文章复用同一fileId**。修改/撤销引用或下架后立即按当前状态鉴权。

头像、友链图标、配置、主页与简历图标、音乐元数据均不接受本站附件API路径（包括等价绝对URL），仅用外部http(s)资源；不新增文件用途/公开状态字段。不能把私密附件URL粘贴进头像或公开配置实现匿名访问。

先写临时文件并完成校验，再原子移动为随机最终存储键，最后插入元数据并提交；任一步失败不得返回File成功结果，数据库写失败时删除刚写文件，补偿失败记录运维错误以清理孤立对象。没有成功元数据的存储对象永不通过API读取。

删除在持有文件行锁时查所有文章（含私密、草稿、回收站）的Markdown/cover引用，仍有引用返回409。扫描应按批次解析现有文章元数据/正文表，不增文件引用表；数据量增长导致扫描不可接受时再单独演进引用模型，不能只扫描公开HTML缓存。所有文章创建/编辑/删除引用同时锁定涉及文件行，保证引用新增和文件删除互斥；多文件按fileId升序，先锁文件再锁文章，复读文章并校验X-Revision。实现不得只“先查一次再删”而允许并发插入引用。

无引用才删除存储对象，确认删除成功/对象已不存在后删除元数据；存储失败保留元数据返回503，可再次DELETE。存储已删而数据库提交失败时元数据暂保留，读取返回503并记录异常，重试删除允许完成清理；不伪称跨文件系统/MySQL事务原子。上传记录存在但对象缺失同样不返回空文件成功。

<a id="transactions"></a>

### 13.5 必须共同提交的业务

| 操作 | 原子边界、锁与失败行为 |
| --- | --- |
| 注册/第三方首次注册 | 用户+member，第三方还含oauth绑定；唯一键冲突回滚整笔；平台身份冲突按[认证与第三方授权](#group-1)重新查赢家 |
| 密码/绑定/解绑 | 所有方式变更先锁sys_user再读密码和绑定；清除/解绑保证至少一种方式；不写占位哈希/默认密码；需要撤销的操作先完成Redis失效再提交，失败处理同[会话生命周期](#auth-rules) |
| 用户/角色/权限/授权 | 先固定管理员保护锁，再按ID升序锁所有受影响用户、目标角色/权限，检查最后管理员；关联集合替换同事务，先撤销会话/清缓存再提交；部分成功不允许 |
| 菜单结构 | 同固定保护锁后检查整棵影响树，保存结构/父节点与约束；角色菜单+权限经grants共同保存 |
| 文章保存 | 文件引用锁→文章锁→X-Revision/范围→分类标签校验→元数据+正文+标签；发布检查publish权限和首次时间；任一关联不存在整笔400，不部分保存 |
| 分类/标签删除 | 单事务执行外键约定的SET NULL/CASCADE；并发文章写入遇到失效关联返回400，不产生正文孤儿；引用变化令聚合X-Revision失效 |
| 回收/恢复/发布/评论/点赞 | 同一文章锁定后才检查状态和写入，避免“检查公开后同时被下架”仍写入；评论先锁配置再锁文章及评论/父节点，文件引用检查/变更先锁文件再锁文章；多目标按ID升序 |
| 评论写入与开关 | 先锁comment配置行，再锁文章和目标/父评论；以锁内最新enabled/reviewRequired/allowComment决策；review/moderate按相同锁顺序。因此配置关闭与并发新增有确定提交顺序 |
| 点赞 | 文章锁+复合主键插入/删除；COUNT读已提交明细，无Redis点赞副本；并发其他用户变化时响应计数仅代表本次读取时刻 |
| 上传/删除 | 按[文件与附件](#group-11)顺序与补偿；存储非数据库事务资源，不使用虚构的分布式事务 |
| 主页/社交/简历/音乐 | ALL范围与指定权限；可变资源按详情X-Revision锁定后完整替换，时间由数据库维护 |
| 留言 | message配置锁→祖先链→目标；锁后校验父链可见性/私密性/版本；新增、编辑、审核与占位规则见12.10 |
| 邮件订阅 | 用户锁→订阅锁；邮箱/用户唯一约束，确认票据与状态匹配；Redis消费和MySQL无共同事务，失败边界见12.12 |
| 访问采集 | 同事务写访客及一次浏览明细；已登录userId从服务端确认；不修改文章累计view_count |
| 操作日志 | 成功日志随业务数据库事务提交；失败日志在回滚后单独记录，不将敏感请求体落库 |

设计决定：业务写事务使用READ COMMITTED，锁定后按当前已提交数据读取关联集合/引用，不沿用MySQL默认可重复读事务中锁定前建立的旧快照。所有锁定前的读取仅用于定位，不作为最终授权/状态依据；进入事务后重新检查范围和状态。文件删除持有文件锁后以当前已提交数据扫描引用，引用写入均需同一文件锁，因此不能漏掉等待前已提交的新引用。数据库死锁/锁超时回滚整笔，返回503（客户端有条件重新获取详情后重试），不部分提交。系统禁止吞掉完整性异常后继续事务。唯一约束冲突用明确字段错误映射409，不把数据库原始约束信息回显。

X-Revision生成和检查需覆盖正文、tag关系、user角色、role权限及菜单集合。授权/菜单读取必须在有效权限快照内完成；内容作者和上传者从会话写入。HTML、文件路径、回调地址和配置白名单是输入信任边界，不能只靠前端表单限制。

### 13.6 数据表与接口覆盖

| 表 | 契约入口 |
| --- | --- |
| sys_user | 注册、登录、本人资料/密码、后台用户；无物理删除 |
| sys_user_oauth | OAuth 登录、绑定、列表和解绑；无客户端 uuid 写入口 |
| sys_role / sys_user_role | 角色 CRUD、用户 roles 整体替换 |
| sys_permission / sys_role_permission | 权限字典、角色 grants 整体替换 |
| sys_menu / sys_role_menu | 菜单结构、角色 grants、本人可访问菜单树 |
| blog_article / blog_article_content / blog_article_tag | 文章聚合创建/编辑、发布/下架、回收/恢复；正文与标签不独立 CRUD |
| blog_category / blog_tag | 分类标签前后台查询与后台维护 |
| blog_comment | 创建/回复、逐层公开分页、本人管理、后台审核与占位删除 |
| blog_article_like | 本人点赞状态与 PUT/DELETE 幂等操作 |
| blog_friend_link | 后台维护与公开展示 |
| blog_file | 上传、元数据、原文件与文章上下文读取、删除补偿 |
| blog_site_config | site/comment/message 白名单对象；公开接口只返回允许公开项 |
| home_profile / home_social_link | 第15章公开展示与后台维护，展示邮箱不来自登录资料 |
| cv_experience / cv_skill | 第15章简历经历和技能，隐藏/展示与物理删除 |
| blog_message | 第16章逐层公开/本人/后台查询、回复、审核和占位删除 |
| blog_music | 第17章公开播放列表与后台元数据维护 |
| blog_rss_subscription | 第18章本人申请、邮箱确认、退订及后台状态管理；XML不依赖本表 |
| blog_visitor / blog_page_view | 第19章匿名采集、访客与浏览维护、公开/后台报表 |
| sys_operation_log | 第20章服务端审计、后台查询和显式清理 |

<a id="errors"></a>

## 14. 错误码与实施边界

### 14.1 HTTP 与业务错误

| HTTP | code | 适用场景 |
| --- | --- | --- |
| 200 | OK | 查询、更新、删除、幂等重放成功；除XML与文件成功响应外使用Result |
| 201 | OK | 创建新业务记录（含注册、文章、评论、上传）；返回模型；后台创建设置对应详情Location，注册/评论通过响应ID及本人接口定位，不设置Location |
| 400 | VALIDATION_ERROR | 字段、枚举、分页、查询组合或 JSON 非法 |
| 400 | OAUTH_STATE_INVALID | state/浏览器/操作不匹配、过期或已消费；不泄露哪一项失败 |
| 400 | OAUTH_DENIED | 用户在平台取消授权 |
| 400 | SUBSCRIPTION_TICKET_INVALID | 订阅确认或邮件退订票据无效、过期、复用或与当前用户/邮箱/轮次不符；统一错误，不泄露匹配项 |
| 401 | UNAUTHENTICATED | Token 缺失、失效、过期或被注销 |
| 401 | AUTH_FAILED | 密码登录名不存在、密码错误或无本站密码，统一文案“账号或密码错误” |
| 403 | ACCOUNT_DISABLED | 已确认凭据身份后发现本站账号禁用 |
| 403 | VISITOR_BLOCKED | 仅访问采集接口限制此统计标识；不代表本站账号被禁用，不阻止公开阅读 |
| 403 | FORBIDDEN | 权限不足；全局操作拒绝 |
| 403 | REAUTH_REQUIRED | 安全操作需最近 5 分钟内重新登录 |
| 404 | NOT_FOUND | 不存在、非公开文章、SELF 不可访问的他人资源；不泄露目标存在性 |
| 409 | UNIQUE_CONFLICT | username/email/slug/编码/路由唯一键冲突；已回滚 |
| 409 | OAUTH_BIND_CONFLICT | 身份已属他人或本站用户同平台已绑定其他身份 |
| 409 | LAST_LOGIN_METHOD | 清除密码/解绑会失去最后一种可用登录方式 |
| 409 | LAST_ADMIN | 会使最后一个有效管理员失效 |
| 409 | STATE_CONFLICT | 当前状态不支持操作，如回收站编辑、审核占位评论、父评论已不可回复 |
| 409 | RESOURCE_IN_USE | 菜单有子节点、权限被菜单引用、文件仍有引用等 |
| 412 | REVISION_CONFLICT | If-Revision 与当前资源不符，重新获取后合并 |
| 413 | PAYLOAD_TOO_LARGE | 请求体/正文/文件超限 |
| 415 | UNSUPPORTED_MEDIA_TYPE | 上传类型或接口 Content-Type 不支持 |
| 428 | PRECONDITION_REQUIRED | 要求并发校验的写操作缺少 If-Revision |
| 429 | RATE_LIMITED | 超出限流；携带 Retry-After 秒数 |
| 502 | OAUTH_UPSTREAM_ERROR | 平台换码/取身份失败；错误信息不带平台 Token |
| 503 | SERVICE_UNAVAILABLE | Redis、数据库或文件存储不可用，安全校验失败时禁止放行 |
| 500 | INTERNAL_ERROR | 未预期异常，不返回 SQL、堆栈、凭据或内部路径 |

框架产生的 405 等错误也包装为 Result，使用 `METHOD_NOT_ALLOWED`；反向代理在进入应用前拒绝的请求不保证 JSON。失败例：

```json
{"code":"VALIDATION_ERROR","msg":"请求参数不合法","data":null,"errors":[{"field":"title","message":"长度应为1至200字符"}]}
```

### 14.2 鉴权失败示例

缺失、过期或已撤销 Token 的受保护请求统一 401；有效登录但缺少业务权限返回 403；有权限而资源不属于本次 SELF 范围返回 404。OAuth 流程中的可信失败按固定回跳规则处理，不能把此规则推广到普通 API。

```http
HTTP/1.1 403 Forbidden
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "FORBIDDEN",
  "msg": "无权执行该操作",
  "data": null,
  "errors": []
}
```

<a id="delivery"></a>

### 14.3 依据与待落实事项

依据：[建库 SQL](../sql/drifting_leaves_v2.sql)、[数据库说明](../sql/README.md)、[Maven 规划](后端Maven依赖与模块规划.md)。本地协作规范位于 `Backend/AGENTS.md` 和 `Front/AGENTS.md`，不纳入版本控制。版本与模块依赖以当前 POM 为准；历史规划中的空目录说明属于早期快照。参考 assessment 文档仅借鉴编排，不沿用其 JWT、原值 Authorization、数字响应码、业务路由或开发白名单。

部署前确定实际前后端 origin、固定 OAuth 完成页、平台 App ID/Secret/回调白名单、组件/图标与 HTML 白名单、上传目录、限流阈值及 BCrypt 硬件成本。首期要求同站部署，推荐同源反代；跨站 Cookie 衔接须另行验证，不能直接关闭 state 校验。

后续验收覆盖 Sa-Token 过期/注销/撤权，按权限 ALL/SELF，OAuth LOGIN/BIND 弹窗与原会话、state/ticket 一次性及重放，纯第三方无密码与最后登录方式，最后管理员，文章可见性/聚合版本，评论审核与占位，点赞幂等，文件私密访问、引用竞态与存储失败。

第 1..6 章已实现，并通过真实隔离 MySQL/Redis 与 MVC 链验证；用户、角色及权限字典管理已落实双权限、聚合版本、最后管理员保护及会话撤销回滚，角色权限与菜单共同替换并补齐祖先，权限禁用保留关联及门禁、删除遵守菜单引用限制。菜单管理落实完整树与本人树职责、结构锁防环、类型及后代约束、前端键白名单、完整替换及业务版本；菜单变化不静默扩大角色授权或撤销独立权限与会话。平台网络使用边界替身，尚未进行真实平台 App 回调及浏览器弹窗联调。第 7..11、15..20 章及第7.11节均为待实现契约，不提供成功空壳，其管理写入和资源SQL授权仍随对应业务实现。配置、命令和测试边界见 [后端运行说明](后端运行说明.md)。

<a id="group-15"></a>

## 15. 主页展示与在线简历

本章全部待实现。主页、博客侧栏和简历共用一份站点主人资料；公开读取仅返回展示字段。后台使用现有home/cv权限码和ALL范围；不根据站点主人的userId自动赋权。

<a id="api-88"></a>

### 15.1 查询站点主人公开资料

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/home/profile` |
| 接口描述 | 查询站点主人公开资料（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 公开；仅按本接口声明的能力与可见范围访问 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

无Path、Query或请求体参数。

#### 请求示例

```http
GET /api/v1/home/profile
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | HomeProfilePublic&#124;null | 未配置的主页资料或不存在的本人订阅可为null；非空完整形状如下 |
| `data.nickname` | S(64) | 公开昵称 |
| `data.tagline` | S0(100) | 个人标签或一句话介绍 |
| `data.description` | S(500)? | 公开简介，null清空 |
| `data.avatar` | URL? | 公开头像，外部HTTP(S)资源 |
| `data.website` | URL? | 个人网站 |
| `data.publicEmail` | string&#124;null | ≤254，合法联系邮箱；明确自愿公开，不自动读取登录邮箱 |
| `data.githubUrl` | URL? | GitHub主页 |
| `data.location` | S(100)? | 自愿公开的所在地 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "nickname": "落叶",
    "tagline": "记录与分享",
    "description": null,
    "avatar": null,
    "website": "https://example.test",
    "publicEmail": null,
    "githubUrl": null,
    "location": null
  },
  "errors": []
}
```

#### 业务说明

固定profileId=1。展示资料与登录资料独立，只有publicEmail可公开；禁止从sys_user.email自动补全。头像和URL遵守公开资源约束。 未配置时200、data=null；博客和CV复用本入口。

---

<a id="api-89"></a>

### 15.2 查询后台主页资料

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/home/profile` |
| 接口描述 | 查询后台主页资料（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `home:profile:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 返回X-Revision，读取不要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |

#### 请求示例

```http
GET /api/v1/admin/home/profile
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | HomeProfile&#124;null | 未配置的主页资料或不存在的本人订阅可为null；非空完整形状如下 |
| `data.profileId` | ID | 固定为1 |
| `data.userId` | ID | 站点主人账号；必须存在且启用，仅后台管理者可选择 |
| `data.nickname` | S(64) | 公开昵称 |
| `data.tagline` | S0(100) | 个人标签或一句话介绍 |
| `data.description` | S(500)? | 公开简介，null清空 |
| `data.avatar` | URL? | 公开头像，外部HTTP(S)资源 |
| `data.website` | URL? | 个人网站 |
| `data.publicEmail` | string&#124;null | ≤254，合法联系邮箱；明确自愿公开，不自动读取登录邮箱 |
| `data.githubUrl` | URL? | GitHub主页 |
| `data.location` | S(100)? | 自愿公开的所在地 |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "profileId": "1",
    "userId": "12",
    "nickname": "落叶",
    "tagline": "记录与分享",
    "description": null,
    "avatar": null,
    "website": "https://example.test",
    "publicEmail": null,
    "githubUrl": null,
    "location": null,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

固定profileId=1。展示资料与登录资料独立，只有publicEmail可公开；禁止从sys_user.email自动补全。头像和URL遵守公开资源约束。 未配置时data=null，仍返回代表空资源的X-Revision。

---

<a id="api-90"></a>

### 15.3 设置主页资料

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/home/profile` |
| 接口描述 | 设置主页资料（待实现） |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `home:profile:update` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `If-Revision` | Header | string | 是 | 原样携带详情X-Revision；缺失428、不匹配412 |
| `userId` | JSON Body | ID | 是 | 站点主人账号；必须存在且启用，仅后台管理者可选择 |
| `nickname` | JSON Body | S(64) | 是 | 公开昵称 |
| `tagline` | JSON Body | S0(100) | 是 | 个人标签或一句话介绍 |
| `description` | JSON Body | S(500)? | 是，可null | 公开简介，null清空 |
| `avatar` | JSON Body | URL? | 是，可null | 公开头像，外部HTTP(S)资源 |
| `website` | JSON Body | URL? | 是，可null | 个人网站 |
| `publicEmail` | JSON Body | string&#124;null | 是，可null | ≤254，合法联系邮箱；明确自愿公开，不自动读取登录邮箱 |
| `githubUrl` | JSON Body | URL? | 是，可null | GitHub主页 |
| `location` | JSON Body | S(100)? | 是，可null | 自愿公开的所在地 |

#### 请求示例

```http
PUT /api/v1/admin/home/profile
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "userId": "12",
  "nickname": "落叶",
  "tagline": "记录与分享",
  "description": null,
  "avatar": null,
  "website": "https://example.test",
  "publicEmail": null,
  "githubUrl": null,
  "location": null
}
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | HomeProfile | 本接口业务结果 |
| `data.profileId` | ID | 固定为1 |
| `data.userId` | ID | 站点主人账号；必须存在且启用，仅后台管理者可选择 |
| `data.nickname` | S(64) | 公开昵称 |
| `data.tagline` | S0(100) | 个人标签或一句话介绍 |
| `data.description` | S(500)? | 公开简介，null清空 |
| `data.avatar` | URL? | 公开头像，外部HTTP(S)资源 |
| `data.website` | URL? | 个人网站 |
| `data.publicEmail` | string&#124;null | ≤254，合法联系邮箱；明确自愿公开，不自动读取登录邮箱 |
| `data.githubUrl` | URL? | GitHub主页 |
| `data.location` | S(100)? | 自愿公开的所在地 |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "profileId": "1",
    "userId": "12",
    "nickname": "落叶",
    "tagline": "记录与分享",
    "description": null,
    "avatar": null,
    "website": "https://example.test",
    "publicEmail": null,
    "githubUrl": null,
    "location": null,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

固定profileId=1。展示资料与登录资料独立，只有publicEmail可公开；禁止从sys_user.email自动补全。头像和URL遵守公开资源约束。 首次设置也通过本PUT，先GET取得空资源版本；同事务检查所选用户、创建或完整替换，始终200。全站资源；授权角色仅有SELF范围时返回403，不能通过登录用户过滤制造虚构的本人数据。 先验证身份、权限与范围，再在同一事务锁定资源并校验If-Revision；重复目标也不能跳过版本校验。 请求体仅接受上表字段，列出的可空字段也必须显式出现；拒绝未知字段、错误类型和客户端审计字段。

---

<a id="api-91"></a>

### 15.4 查询公开社交链接

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/home/social-links` |
| 接口描述 | 查询公开社交链接（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 公开；仅按本接口声明的能力与可见范围访问 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

无Path、Query或请求体参数。

#### 请求示例

```http
GET /api/v1/home/social-links
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | SocialPublic[] | 本接口业务结果 |
| `data[].socialId` | ID | 社交链接ID |
| `data[].name` | S(64) | 社交平台或链接名称 |
| `data[].icon` | string&#124;null | ≤2048；外部HTTP(S)图标URL或前端注册图标键，禁止任意HTML |
| `data[].url` | URL | 外部HTTP(S)目标链接，邮箱链接可使用经过校验的mailto:邮箱 |
| `data[].sortOrder` | int32 | 越小越靠前 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": [
    {
      "socialId": "21",
      "name": "GitHub",
      "icon": "github",
      "url": "https://github.com/example",
      "sortOrder": 0
    }
  ],
  "errors": []
}
```

#### 业务说明

仅status=1，sortOrder ASC,socialId ASC；无数据[]。mailto仅接受单一合法邮箱，不接受额外查询参数或控制字符。

---

<a id="api-92"></a>

### 15.5 查询后台社交链接

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/home/social-links` |
| 接口描述 | 查询后台社交链接（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `home:social:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `page` | Query | integer | 否 | 默认1，1..100000 |
| `pageSize` | Query | integer | 否 | 默认10，1..100 |
| `keyword` | Query | S(100) | 否 | 名称/标题字面包含，转义LIKE通配符 |
| `status` | Query | integer | 否 | 可选0/1；缺失返回全部状态 |

#### 请求示例

```http
GET /api/v1/admin/home/social-links?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<Social> | 本接口业务结果 |
| `data.page` | integer | 当前页码 |
| `data.pageSize` | integer | 每页条数 |
| `data.total` | string | 同过滤条件的总条数 |
| `data.totalPages` | string | 总页数 |
| `data.records` | array | 无记录为[] |
| `data.records[].socialId` | ID | 社交链接ID |
| `data.records[].name` | S(64) | 社交平台或链接名称 |
| `data.records[].icon` | string&#124;null | ≤2048；外部HTTP(S)图标URL或前端注册图标键，禁止任意HTML |
| `data.records[].url` | URL | 外部HTTP(S)目标链接，邮箱链接可使用经过校验的mailto:邮箱 |
| `data.records[].sortOrder` | int32 | 越小越靠前 |
| `data.records[].status` | integer | 0隐藏 1展示 |
| `data.records[].createTime` | Time | 创建时间，UTC |
| `data.records[].updateTime` | Time | 最后更新时间，UTC |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "socialId": "21",
        "name": "GitHub",
        "icon": "github",
        "url": "https://github.com/example",
        "sortOrder": 0,
        "status": 1,
        "createTime": "2026-10-30T02:00:00.000Z",
        "updateTime": "2026-10-30T02:00:00.000Z"
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

全站资源；授权角色仅有SELF范围时返回403，不能通过登录用户过滤制造虚构的本人数据。 排序sortOrder ASC,socialId ASC；筛选AND组合。

---

<a id="api-93"></a>

### 15.6 查询社交链接详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/home/social-links/{socialId}` |
| 接口描述 | 查询社交链接详情（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `home:social:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 返回X-Revision，读取不要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `socialId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |

#### 请求示例

```http
GET /api/v1/admin/home/social-links/21
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Social | 本接口业务结果 |
| `data.socialId` | ID | 社交链接ID |
| `data.name` | S(64) | 社交平台或链接名称 |
| `data.icon` | string&#124;null | ≤2048；外部HTTP(S)图标URL或前端注册图标键，禁止任意HTML |
| `data.url` | URL | 外部HTTP(S)目标链接，邮箱链接可使用经过校验的mailto:邮箱 |
| `data.sortOrder` | int32 | 越小越靠前 |
| `data.status` | integer | 0隐藏 1展示 |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "socialId": "21",
    "name": "GitHub",
    "icon": "github",
    "url": "https://github.com/example",
    "sortOrder": 0,
    "status": 1,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

后台全局资源，必须检查本次权限的ALL范围；目标不存在404。

---

<a id="api-94"></a>

### 15.7 创建社交链接

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/home/social-links` |
| 接口描述 | 创建社交链接（待实现） |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `home:social:create` + 该操作的ALL范围 |
| 成功状态 | 201 Created |
| 并发校验 | 返回X-Revision，读取不要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `name` | JSON Body | S(64) | 是 | 社交平台或链接名称 |
| `icon` | JSON Body | string&#124;null | 是，可null | ≤2048；外部HTTP(S)图标URL或前端注册图标键，禁止任意HTML |
| `url` | JSON Body | URL | 是 | 外部HTTP(S)目标链接，邮箱链接可使用经过校验的mailto:邮箱 |
| `sortOrder` | JSON Body | int32 | 是 | 越小越靠前 |
| `status` | JSON Body | integer | 是 | 0隐藏 1展示 |

#### 请求示例

```http
POST /api/v1/admin/home/social-links
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "name": "GitHub",
  "icon": "github",
  "url": "https://github.com/example",
  "sortOrder": 0,
  "status": 1
}
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Social | 本接口业务结果 |
| `data.socialId` | ID | 社交链接ID |
| `data.name` | S(64) | 社交平台或链接名称 |
| `data.icon` | string&#124;null | ≤2048；外部HTTP(S)图标URL或前端注册图标键，禁止任意HTML |
| `data.url` | URL | 外部HTTP(S)目标链接，邮箱链接可使用经过校验的mailto:邮箱 |
| `data.sortOrder` | int32 | 越小越靠前 |
| `data.status` | integer | 0隐藏 1展示 |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 201 Created
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
Location: /api/v1/admin/home/social-links/21
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "socialId": "21",
    "name": "GitHub",
    "icon": "github",
    "url": "https://github.com/example",
    "sortOrder": 0,
    "status": 1,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

名称与URL没有唯一键，不承诺重复时409。全站资源；授权角色仅有SELF范围时返回403，不能通过登录用户过滤制造虚构的本人数据。 请求体仅接受上表字段，列出的可空字段也必须显式出现；拒绝未知字段、错误类型和客户端审计字段。

---

<a id="api-95"></a>

### 15.8 修改社交链接

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/home/social-links/{socialId}` |
| 接口描述 | 修改社交链接（待实现） |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `home:social:update` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `socialId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `If-Revision` | Header | string | 是 | 原样携带详情X-Revision；缺失428、不匹配412 |
| `name` | JSON Body | S(64) | 是 | 社交平台或链接名称 |
| `icon` | JSON Body | string&#124;null | 是，可null | ≤2048；外部HTTP(S)图标URL或前端注册图标键，禁止任意HTML |
| `url` | JSON Body | URL | 是 | 外部HTTP(S)目标链接，邮箱链接可使用经过校验的mailto:邮箱 |
| `sortOrder` | JSON Body | int32 | 是 | 越小越靠前 |
| `status` | JSON Body | integer | 是 | 0隐藏 1展示 |

#### 请求示例

```http
PUT /api/v1/admin/home/social-links/21
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "name": "GitHub",
  "icon": "github",
  "url": "https://github.com/example",
  "sortOrder": 0,
  "status": 1
}
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Social | 本接口业务结果 |
| `data.socialId` | ID | 社交链接ID |
| `data.name` | S(64) | 社交平台或链接名称 |
| `data.icon` | string&#124;null | ≤2048；外部HTTP(S)图标URL或前端注册图标键，禁止任意HTML |
| `data.url` | URL | 外部HTTP(S)目标链接，邮箱链接可使用经过校验的mailto:邮箱 |
| `data.sortOrder` | int32 | 越小越靠前 |
| `data.status` | integer | 0隐藏 1展示 |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "socialId": "21",
    "name": "GitHub",
    "icon": "github",
    "url": "https://github.com/example",
    "sortOrder": 0,
    "status": 1,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

完整替换；隐藏后公开入口立即不返回。全站资源；授权角色仅有SELF范围时返回403，不能通过登录用户过滤制造虚构的本人数据。 先验证身份、权限与范围，再在同一事务锁定资源并校验If-Revision；重复目标也不能跳过版本校验。 请求体仅接受上表字段，列出的可空字段也必须显式出现；拒绝未知字段、错误类型和客户端审计字段。

---

<a id="api-96"></a>

### 15.9 删除社交链接

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/home/social-links/{socialId}` |
| 接口描述 | 删除社交链接（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `home:social:delete` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `socialId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `If-Revision` | Header | string | 是 | 原样携带详情X-Revision；缺失428、不匹配412 |

#### 请求示例

```http
DELETE /api/v1/admin/home/social-links/21
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 本接口业务结果 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

物理删除。全站资源；授权角色仅有SELF范围时返回403，不能通过登录用户过滤制造虚构的本人数据。 先验证身份、权限与范围，再在同一事务锁定资源并校验If-Revision；重复目标也不能跳过版本校验。

---

<a id="api-97"></a>

### 15.10 查询公开简历经历

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/cv/experiences` |
| 接口描述 | 查询公开简历经历（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 公开；仅按本接口声明的能力与可见范围访问 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `page` | Query | integer | 否 | 默认1，1..100000 |
| `pageSize` | Query | integer | 否 | 默认10，1..100 |
| `experienceType` | Query | integer | 否 | 0教育 1工作/实习 2项目 |

#### 请求示例

```http
GET /api/v1/cv/experiences?page=1&pageSize=10
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<ExperiencePublic> | 本接口业务结果 |
| `data.page` | integer | 当前页码 |
| `data.pageSize` | integer | 每页条数 |
| `data.total` | string | 同过滤条件的总条数 |
| `data.totalPages` | string | 总页数 |
| `data.records` | array | 无记录为[] |
| `data.records[].experienceId` | ID | 经历ID |
| `data.records[].experienceType` | integer | 0教育 1工作/实习 2项目 |
| `data.records[].title` | S(200) | 学校、公司或项目名称 |
| `data.records[].subtitle` | S(200)? | 专业、职位或项目角色 |
| `data.records[].logoUrl` | URL? | 外部HTTP(S)图标URL |
| `data.records[].startDate` | date | 合法YYYY-MM-DD，1000..9999年 |
| `data.records[].endDate` | date&#124;null | 结束日期不得早于开始；null表示至今 |
| `data.records[].sortOrder` | int32 | 越小越靠前 |
| `data.records[].htmlContent` | string | 安全渲染HTML |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "experienceId": "31",
        "experienceType": 0,
        "title": "示例大学",
        "subtitle": "计算机科学",
        "logoUrl": null,
        "startDate": "2022-09-01",
        "endDate": null,
        "sortOrder": 0,
        "htmlContent": "<p>学习与项目实践</p>"
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

仅status=1；sortOrder ASC,startDate DESC,experienceId DESC。不返回原始Markdown。

---

<a id="api-98"></a>

### 15.11 查询后台简历经历

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/cv/experiences` |
| 接口描述 | 查询后台简历经历（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `cv:experience:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `page` | Query | integer | 否 | 默认1，1..100000 |
| `pageSize` | Query | integer | 否 | 默认10，1..100 |
| `experienceType` | Query | integer | 否 | 0/1/2 |
| `keyword` | Query | S(100) | 否 | 名称/标题字面包含，转义LIKE通配符 |
| `status` | Query | integer | 否 | 可选0/1；缺失返回全部状态 |

#### 请求示例

```http
GET /api/v1/admin/cv/experiences?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<Experience> | 本接口业务结果 |
| `data.page` | integer | 当前页码 |
| `data.pageSize` | integer | 每页条数 |
| `data.total` | string | 同过滤条件的总条数 |
| `data.totalPages` | string | 总页数 |
| `data.records` | array | 无记录为[] |
| `data.records[].experienceId` | ID | 经历ID |
| `data.records[].experienceType` | integer | 0教育 1工作/实习 2项目 |
| `data.records[].title` | S(200) | 学校、公司或项目名称 |
| `data.records[].subtitle` | S(200)? | 专业、职位或项目角色 |
| `data.records[].logoUrl` | URL? | 外部HTTP(S)图标URL |
| `data.records[].content` | string | 非空Markdown，UTF-8最多65535字节，与MySQL TEXT容量一致 |
| `data.records[].startDate` | date | 合法YYYY-MM-DD，1000..9999年 |
| `data.records[].endDate` | date&#124;null | 结束日期不得早于开始；null表示至今 |
| `data.records[].sortOrder` | int32 | 越小越靠前 |
| `data.records[].status` | integer | 0隐藏 1展示 |
| `data.records[].createTime` | Time | 创建时间，UTC |
| `data.records[].updateTime` | Time | 最后更新时间，UTC |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "experienceId": "31",
        "experienceType": 0,
        "title": "示例大学",
        "subtitle": "计算机科学",
        "logoUrl": null,
        "content": "学习与项目实践",
        "startDate": "2022-09-01",
        "endDate": null,
        "sortOrder": 0,
        "status": 1,
        "createTime": "2026-10-30T02:00:00.000Z",
        "updateTime": "2026-10-30T02:00:00.000Z"
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

全站资源；授权角色仅有SELF范围时返回403，不能通过登录用户过滤制造虚构的本人数据。 按公开入口同样规则排序，但包含隐藏记录。

---

<a id="api-99"></a>

### 15.12 查询简历经历详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/cv/experiences/{experienceId}` |
| 接口描述 | 查询简历经历详情（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `cv:experience:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 返回X-Revision，读取不要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `experienceId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |

#### 请求示例

```http
GET /api/v1/admin/cv/experiences/31
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Experience | 本接口业务结果 |
| `data.experienceId` | ID | 经历ID |
| `data.experienceType` | integer | 0教育 1工作/实习 2项目 |
| `data.title` | S(200) | 学校、公司或项目名称 |
| `data.subtitle` | S(200)? | 专业、职位或项目角色 |
| `data.logoUrl` | URL? | 外部HTTP(S)图标URL |
| `data.content` | string | 非空Markdown，UTF-8最多65535字节，与MySQL TEXT容量一致 |
| `data.startDate` | date | 合法YYYY-MM-DD，1000..9999年 |
| `data.endDate` | date&#124;null | 结束日期不得早于开始；null表示至今 |
| `data.sortOrder` | int32 | 越小越靠前 |
| `data.status` | integer | 0隐藏 1展示 |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "experienceId": "31",
    "experienceType": 0,
    "title": "示例大学",
    "subtitle": "计算机科学",
    "logoUrl": null,
    "content": "学习与项目实践",
    "startDate": "2022-09-01",
    "endDate": null,
    "sortOrder": 0,
    "status": 1,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

后台全局资源，必须检查本次权限的ALL范围；目标不存在404。

---

<a id="api-100"></a>

### 15.13 创建简历经历

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/cv/experiences` |
| 接口描述 | 创建简历经历（待实现） |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `cv:experience:create` + 该操作的ALL范围 |
| 成功状态 | 201 Created |
| 并发校验 | 返回X-Revision，读取不要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `experienceType` | JSON Body | integer | 是 | 0教育 1工作/实习 2项目 |
| `title` | JSON Body | S(200) | 是 | 学校、公司或项目名称 |
| `subtitle` | JSON Body | S(200)? | 是，可null | 专业、职位或项目角色 |
| `logoUrl` | JSON Body | URL? | 是，可null | 外部HTTP(S)图标URL |
| `content` | JSON Body | string | 是 | 非空Markdown，UTF-8最多65535字节，与MySQL TEXT容量一致 |
| `startDate` | JSON Body | date | 是 | 合法YYYY-MM-DD，1000..9999年 |
| `endDate` | JSON Body | date&#124;null | 是，可null | 结束日期不得早于开始；null表示至今 |
| `sortOrder` | JSON Body | int32 | 是 | 越小越靠前 |
| `status` | JSON Body | integer | 是 | 0隐藏 1展示 |

#### 请求示例

```http
POST /api/v1/admin/cv/experiences
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "experienceType": 0,
  "title": "示例大学",
  "subtitle": "计算机科学",
  "logoUrl": null,
  "content": "学习与项目实践",
  "startDate": "2022-09-01",
  "endDate": null,
  "sortOrder": 0,
  "status": 1
}
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Experience | 本接口业务结果 |
| `data.experienceId` | ID | 经历ID |
| `data.experienceType` | integer | 0教育 1工作/实习 2项目 |
| `data.title` | S(200) | 学校、公司或项目名称 |
| `data.subtitle` | S(200)? | 专业、职位或项目角色 |
| `data.logoUrl` | URL? | 外部HTTP(S)图标URL |
| `data.content` | string | 非空Markdown，UTF-8最多65535字节，与MySQL TEXT容量一致 |
| `data.startDate` | date | 合法YYYY-MM-DD，1000..9999年 |
| `data.endDate` | date&#124;null | 结束日期不得早于开始；null表示至今 |
| `data.sortOrder` | int32 | 越小越靠前 |
| `data.status` | integer | 0隐藏 1展示 |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 201 Created
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
Location: /api/v1/admin/cv/experiences/31
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "experienceId": "31",
    "experienceType": 0,
    "title": "示例大学",
    "subtitle": "计算机科学",
    "logoUrl": null,
    "content": "学习与项目实践",
    "startDate": "2022-09-01",
    "endDate": null,
    "sortOrder": 0,
    "status": 1,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

endDate=null表示至今；非空不得早于startDate。内容使用CommonMark及现有HTML清理规则。 请求体仅接受上表字段，列出的可空字段也必须显式出现；拒绝未知字段、错误类型和客户端审计字段。

---

<a id="api-101"></a>

### 15.14 修改简历经历

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/cv/experiences/{experienceId}` |
| 接口描述 | 修改简历经历（待实现） |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `cv:experience:update` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `experienceId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `If-Revision` | Header | string | 是 | 原样携带详情X-Revision；缺失428、不匹配412 |
| `experienceType` | JSON Body | integer | 是 | 0教育 1工作/实习 2项目 |
| `title` | JSON Body | S(200) | 是 | 学校、公司或项目名称 |
| `subtitle` | JSON Body | S(200)? | 是，可null | 专业、职位或项目角色 |
| `logoUrl` | JSON Body | URL? | 是，可null | 外部HTTP(S)图标URL |
| `content` | JSON Body | string | 是 | 非空Markdown，UTF-8最多65535字节，与MySQL TEXT容量一致 |
| `startDate` | JSON Body | date | 是 | 合法YYYY-MM-DD，1000..9999年 |
| `endDate` | JSON Body | date&#124;null | 是，可null | 结束日期不得早于开始；null表示至今 |
| `sortOrder` | JSON Body | int32 | 是 | 越小越靠前 |
| `status` | JSON Body | integer | 是 | 0隐藏 1展示 |

#### 请求示例

```http
PUT /api/v1/admin/cv/experiences/31
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "experienceType": 0,
  "title": "示例大学",
  "subtitle": "计算机科学",
  "logoUrl": null,
  "content": "学习与项目实践",
  "startDate": "2022-09-01",
  "endDate": null,
  "sortOrder": 0,
  "status": 1
}
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Experience | 本接口业务结果 |
| `data.experienceId` | ID | 经历ID |
| `data.experienceType` | integer | 0教育 1工作/实习 2项目 |
| `data.title` | S(200) | 学校、公司或项目名称 |
| `data.subtitle` | S(200)? | 专业、职位或项目角色 |
| `data.logoUrl` | URL? | 外部HTTP(S)图标URL |
| `data.content` | string | 非空Markdown，UTF-8最多65535字节，与MySQL TEXT容量一致 |
| `data.startDate` | date | 合法YYYY-MM-DD，1000..9999年 |
| `data.endDate` | date&#124;null | 结束日期不得早于开始；null表示至今 |
| `data.sortOrder` | int32 | 越小越靠前 |
| `data.status` | integer | 0隐藏 1展示 |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "experienceId": "31",
    "experienceType": 0,
    "title": "示例大学",
    "subtitle": "计算机科学",
    "logoUrl": null,
    "content": "学习与项目实践",
    "startDate": "2022-09-01",
    "endDate": null,
    "sortOrder": 0,
    "status": 1,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

完整替换，允许切换经历类型；仅有SELF范围拒绝。 先验证身份、权限与范围，再在同一事务锁定资源并校验If-Revision；重复目标也不能跳过版本校验。 请求体仅接受上表字段，列出的可空字段也必须显式出现；拒绝未知字段、错误类型和客户端审计字段。

---

<a id="api-102"></a>

### 15.15 删除简历经历

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/cv/experiences/{experienceId}` |
| 接口描述 | 删除简历经历（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `cv:experience:delete` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `experienceId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `If-Revision` | Header | string | 是 | 原样携带详情X-Revision；缺失428、不匹配412 |

#### 请求示例

```http
DELETE /api/v1/admin/cv/experiences/31
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 本接口业务结果 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

物理删除，不新增简历回收站。 先验证身份、权限与范围，再在同一事务锁定资源并校验If-Revision；重复目标也不能跳过版本校验。

---

<a id="api-103"></a>

### 15.16 查询公开简历技能

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/cv/skills` |
| 接口描述 | 查询公开简历技能（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 公开；仅按本接口声明的能力与可见范围访问 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `page` | Query | integer | 否 | 默认1，1..100000 |
| `pageSize` | Query | integer | 否 | 默认10，1..100 |

#### 请求示例

```http
GET /api/v1/cv/skills?page=1&pageSize=10
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<SkillPublic> | 本接口业务结果 |
| `data.page` | integer | 当前页码 |
| `data.pageSize` | integer | 每页条数 |
| `data.total` | string | 同过滤条件的总条数 |
| `data.totalPages` | string | 总页数 |
| `data.records` | array | 无记录为[] |
| `data.records[].skillId` | ID | 技能ID |
| `data.records[].name` | S(64) | 技能名称 |
| `data.records[].description` | S(500)? | 技能描述 |
| `data.records[].icon` | string&#124;null | ≤2048；外部HTTP(S)URL或注册图标键 |
| `data.records[].sortOrder` | int32 | 越小越靠前 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "skillId": "41",
        "name": "Java",
        "description": "后端开发",
        "icon": null,
        "sortOrder": 0
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

仅status=1；sortOrder ASC,skillId ASC。description用于熟练度等文字说明，没有数据库未定义的评分列。

---

<a id="api-104"></a>

### 15.17 查询后台简历技能

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/cv/skills` |
| 接口描述 | 查询后台简历技能（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `cv:skill:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `page` | Query | integer | 否 | 默认1，1..100000 |
| `pageSize` | Query | integer | 否 | 默认10，1..100 |
| `keyword` | Query | S(100) | 否 | 名称/标题字面包含，转义LIKE通配符 |
| `status` | Query | integer | 否 | 可选0/1；缺失返回全部状态 |

#### 请求示例

```http
GET /api/v1/admin/cv/skills?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<Skill> | 本接口业务结果 |
| `data.page` | integer | 当前页码 |
| `data.pageSize` | integer | 每页条数 |
| `data.total` | string | 同过滤条件的总条数 |
| `data.totalPages` | string | 总页数 |
| `data.records` | array | 无记录为[] |
| `data.records[].skillId` | ID | 技能ID |
| `data.records[].name` | S(64) | 技能名称 |
| `data.records[].description` | S(500)? | 技能描述 |
| `data.records[].icon` | string&#124;null | ≤2048；外部HTTP(S)URL或注册图标键 |
| `data.records[].sortOrder` | int32 | 越小越靠前 |
| `data.records[].status` | integer | 0隐藏 1展示 |
| `data.records[].createTime` | Time | 创建时间，UTC |
| `data.records[].updateTime` | Time | 最后更新时间，UTC |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "skillId": "41",
        "name": "Java",
        "description": "后端开发",
        "icon": null,
        "sortOrder": 0,
        "status": 1,
        "createTime": "2026-10-30T02:00:00.000Z",
        "updateTime": "2026-10-30T02:00:00.000Z"
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

全站资源；授权角色仅有SELF范围时返回403，不能通过登录用户过滤制造虚构的本人数据。 sortOrder ASC,skillId ASC。

---

<a id="api-105"></a>

### 15.18 查询技能详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/cv/skills/{skillId}` |
| 接口描述 | 查询技能详情（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `cv:skill:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 返回X-Revision，读取不要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `skillId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |

#### 请求示例

```http
GET /api/v1/admin/cv/skills/41
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Skill | 本接口业务结果 |
| `data.skillId` | ID | 技能ID |
| `data.name` | S(64) | 技能名称 |
| `data.description` | S(500)? | 技能描述 |
| `data.icon` | string&#124;null | ≤2048；外部HTTP(S)URL或注册图标键 |
| `data.sortOrder` | int32 | 越小越靠前 |
| `data.status` | integer | 0隐藏 1展示 |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "skillId": "41",
    "name": "Java",
    "description": "后端开发",
    "icon": null,
    "sortOrder": 0,
    "status": 1,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

后台全局资源，必须检查本次权限的ALL范围；目标不存在404。

---

<a id="api-106"></a>

### 15.19 创建简历技能

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/cv/skills` |
| 接口描述 | 创建简历技能（待实现） |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `cv:skill:create` + 该操作的ALL范围 |
| 成功状态 | 201 Created |
| 并发校验 | 返回X-Revision，读取不要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `name` | JSON Body | S(64) | 是 | 技能名称 |
| `description` | JSON Body | S(500)? | 是，可null | 技能描述 |
| `icon` | JSON Body | string&#124;null | 是，可null | ≤2048；外部HTTP(S)URL或注册图标键 |
| `sortOrder` | JSON Body | int32 | 是 | 越小越靠前 |
| `status` | JSON Body | integer | 是 | 0隐藏 1展示 |

#### 请求示例

```http
POST /api/v1/admin/cv/skills
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "name": "Java",
  "description": "后端开发",
  "icon": null,
  "sortOrder": 0,
  "status": 1
}
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Skill | 本接口业务结果 |
| `data.skillId` | ID | 技能ID |
| `data.name` | S(64) | 技能名称 |
| `data.description` | S(500)? | 技能描述 |
| `data.icon` | string&#124;null | ≤2048；外部HTTP(S)URL或注册图标键 |
| `data.sortOrder` | int32 | 越小越靠前 |
| `data.status` | integer | 0隐藏 1展示 |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 201 Created
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
Location: /api/v1/admin/cv/skills/41
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "skillId": "41",
    "name": "Java",
    "description": "后端开发",
    "icon": null,
    "sortOrder": 0,
    "status": 1,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

后台全局资源，必须检查本次权限的ALL范围；目标不存在404。 请求体仅接受上表字段，列出的可空字段也必须显式出现；拒绝未知字段、错误类型和客户端审计字段。

---

<a id="api-107"></a>

### 15.20 修改简历技能

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/cv/skills/{skillId}` |
| 接口描述 | 修改简历技能（待实现） |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `cv:skill:update` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `skillId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `If-Revision` | Header | string | 是 | 原样携带详情X-Revision；缺失428、不匹配412 |
| `name` | JSON Body | S(64) | 是 | 技能名称 |
| `description` | JSON Body | S(500)? | 是，可null | 技能描述 |
| `icon` | JSON Body | string&#124;null | 是，可null | ≤2048；外部HTTP(S)URL或注册图标键 |
| `sortOrder` | JSON Body | int32 | 是 | 越小越靠前 |
| `status` | JSON Body | integer | 是 | 0隐藏 1展示 |

#### 请求示例

```http
PUT /api/v1/admin/cv/skills/41
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "name": "Java",
  "description": "后端开发",
  "icon": null,
  "sortOrder": 0,
  "status": 1
}
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Skill | 本接口业务结果 |
| `data.skillId` | ID | 技能ID |
| `data.name` | S(64) | 技能名称 |
| `data.description` | S(500)? | 技能描述 |
| `data.icon` | string&#124;null | ≤2048；外部HTTP(S)URL或注册图标键 |
| `data.sortOrder` | int32 | 越小越靠前 |
| `data.status` | integer | 0隐藏 1展示 |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "skillId": "41",
    "name": "Java",
    "description": "后端开发",
    "icon": null,
    "sortOrder": 0,
    "status": 1,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

完整替换。图标仅白名单键或外部HTTP(S)资源，不接受任意SVG/HTML。 先验证身份、权限与范围，再在同一事务锁定资源并校验If-Revision；重复目标也不能跳过版本校验。 请求体仅接受上表字段，列出的可空字段也必须显式出现；拒绝未知字段、错误类型和客户端审计字段。

---

<a id="api-108"></a>

### 15.21 删除简历技能

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/cv/skills/{skillId}` |
| 接口描述 | 删除简历技能（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `cv:skill:delete` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `skillId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `If-Revision` | Header | string | 是 | 原样携带详情X-Revision；缺失428、不匹配412 |

#### 请求示例

```http
DELETE /api/v1/admin/cv/skills/41
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 本接口业务结果 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

物理删除。 先验证身份、权限与范围，再在同一事务锁定资源并校验If-Revision；重复目标也不能跳过版本校验。

---

<a id="group-16"></a>

## 16. 留言板、本人留言与审核

本章全部待实现，使用blog_message，独立于文章评论。公开列表沿用第9章按parentId逐层分页，返回平面Page而非一次展开整棵线程；父链隐藏与悄悄话规则见第12.10节。本人接口只操作会话userId，ALL不能扩大归属。

<a id="api-109"></a>

### 16.1 分页读取可见留言或回复

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/messages` |
| 接口描述 | 分页读取可见留言或回复（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 公开；可选Bearer，携带时必须校验有效会话和账号状态 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 否 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `page` | Query | integer | 否 | 默认1，1..100000 |
| `pageSize` | Query | integer | 否 | 默认10，1..50 |
| `parentId` | Query | ID | 否 | 缺失仅根节点；提供时仅该节点的直接回复 |

#### 请求示例

```http
GET /api/v1/blog/messages?page=1&pageSize=10
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<MessagePublic> | 本接口业务结果 |
| `data.page` | integer | 当前页码 |
| `data.pageSize` | integer | 每页条数 |
| `data.total` | string | 同过滤条件的总条数 |
| `data.totalPages` | string | 总页数 |
| `data.records` | array | 无记录为[] |
| `data.records[].messageId` | ID | 留言ID |
| `data.records[].parentId` | ID&#124;null | 直接回复目标；根留言为null |
| `data.records[].author` | object&#124;null | 当前用户公开资料；删除占位时为null |
| `data.records[].author.userId` | ID | 本站用户ID |
| `data.records[].author.nickname` | string | 当前昵称 |
| `data.records[].author.avatar` | URL&#124;null | 当前公开头像 |
| `data.records[].content` | string | 原文；删除占位固定为该留言已删除 |
| `data.records[].htmlContent` | string | 安全渲染HTML；纯文本转义并保留换行 |
| `data.records[].contentFormat` | integer | 0纯文本 1Markdown |
| `data.records[].isSecret` | integer | 0公开 1悄悄话 |
| `data.records[].status` | integer | 0待审核 1通过 2驳回 3删除占位 |
| `data.records[].editedAt` | Time&#124;null | 最近作者编辑时间，未编辑为null |
| `data.records[].isOwn` | boolean | 按当前会话计算，匿名恒为false |
| `data.records[].createTime` | Time | 创建时间，UTC |
| `data.records[].updateTime` | Time | 最后更新时间，UTC |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "messageId": "301",
        "parentId": null,
        "author": {
          "userId": "12",
          "nickname": "落叶",
          "avatar": null
        },
        "content": "你好，欢迎交流。",
        "htmlContent": "<p>你好，欢迎交流。</p>",
        "contentFormat": 0,
        "isSecret": 0,
        "status": 1,
        "editedAt": null,
        "isOwn": false,
        "createTime": "2026-10-30T02:00:00.000Z",
        "updateTime": "2026-10-30T02:00:00.000Z"
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

匿名只读取审核通过的公开节点和可见删除占位；合法会话可额外读取本人待审/驳回节点及当前私密线程允许的内容。完整祖先链必须可见，total只计当前层可见节点。createTime ASC,messageId ASC。不可见父节点404。

---

<a id="api-110"></a>

### 16.2 发表或回复留言

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/messages` |
| 接口描述 | 发表或回复留言（待实现） |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `blog:message:create`；始终限制本人归属 |
| 成功状态 | 201 Created |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `parentId` | JSON Body | ID&#124;null | 是，可null | 直接回复目标；根留言为null，回复创建后不可改 |
| `content` | JSON Body | S(2000) | 是 | 去首尾空白后非空；Markdown保留原文内部空白 |
| `contentFormat` | JSON Body | integer | 是 | 0纯文本 1Markdown |
| `isSecret` | JSON Body | integer | 是 | 0公开 1悄悄话；私密回复必须继承完整父链限制 |
| `notifyReply` | JSON Body | integer | 是 | 0/1；见留言通知边界 |

#### 请求示例

```http
POST /api/v1/blog/messages
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "parentId": null,
  "content": "你好，欢迎交流。",
  "contentFormat": 0,
  "isSecret": 0,
  "notifyReply": 0
}
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | MessagePrivate | 本接口业务结果 |
| `data.messageId` | ID | 留言ID |
| `data.parentId` | ID&#124;null | 直接回复目标；根留言为null |
| `data.author` | object&#124;null | 当前用户公开资料；删除占位时为null |
| `data.author.userId` | ID | 本站用户ID |
| `data.author.nickname` | string | 当前昵称 |
| `data.author.avatar` | URL&#124;null | 当前公开头像 |
| `data.content` | string | 原文；删除占位固定为该留言已删除 |
| `data.htmlContent` | string | 安全渲染HTML；纯文本转义并保留换行 |
| `data.contentFormat` | integer | 0纯文本 1Markdown |
| `data.isSecret` | integer | 0公开 1悄悄话 |
| `data.status` | integer | 0待审核 1通过 2驳回 3删除占位 |
| `data.editedAt` | Time&#124;null | 最近作者编辑时间，未编辑为null |
| `data.isOwn` | boolean | 按当前会话计算，匿名恒为false |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| `data.notifyReply` | integer | 0不通知 1请求回复通知；不是邮箱验证凭据 |

#### 响应示例

```http
HTTP/1.1 201 Created
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "messageId": "301",
    "parentId": null,
    "author": {
      "userId": "12",
      "nickname": "落叶",
      "avatar": null
    },
    "content": "你好，欢迎交流。",
    "htmlContent": "<p>你好，欢迎交流。</p>",
    "contentFormat": 0,
    "isSecret": 0,
    "status": 0,
    "editedAt": null,
    "isOwn": true,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z",
    "notifyReply": 0
  },
  "errors": []
}
```

#### 业务说明

作者固定为当前用户。先锁message配置，再锁父链，检查enabled和父链可见性；按reviewRequired确定0/1，普通管理员经此前台入口也不自动免审。客户端不能写作者、昵称、rootId、status、isAdminReply或HTML。 请求体仅接受上表字段，列出的可空字段也必须显式出现；拒绝未知字段、错误类型和客户端审计字段。 创建成功不设置Location，通过响应ID及本人/后台详情入口定位。

---

<a id="api-111"></a>

### 16.3 分页查询本人留言

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/me/messages` |
| 接口描述 | 分页查询本人留言（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用；仅当前会话本人 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `page` | Query | integer | 否 | 默认1，1..100000 |
| `pageSize` | Query | integer | 否 | 默认10，1..100 |
| `status` | Query | integer | 否 | 0/1/2/3 |

#### 请求示例

```http
GET /api/v1/me/messages?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<MessagePrivate> | 本接口业务结果 |
| `data.page` | integer | 当前页码 |
| `data.pageSize` | integer | 每页条数 |
| `data.total` | string | 同过滤条件的总条数 |
| `data.totalPages` | string | 总页数 |
| `data.records` | array | 无记录为[] |
| `data.records[].messageId` | ID | 留言ID |
| `data.records[].parentId` | ID&#124;null | 直接回复目标；根留言为null |
| `data.records[].author` | object&#124;null | 当前用户公开资料；删除占位时为null |
| `data.records[].author.userId` | ID | 本站用户ID |
| `data.records[].author.nickname` | string | 当前昵称 |
| `data.records[].author.avatar` | URL&#124;null | 当前公开头像 |
| `data.records[].content` | string | 原文；删除占位固定为该留言已删除 |
| `data.records[].htmlContent` | string | 安全渲染HTML；纯文本转义并保留换行 |
| `data.records[].contentFormat` | integer | 0纯文本 1Markdown |
| `data.records[].isSecret` | integer | 0公开 1悄悄话 |
| `data.records[].status` | integer | 0待审核 1通过 2驳回 3删除占位 |
| `data.records[].editedAt` | Time&#124;null | 最近作者编辑时间，未编辑为null |
| `data.records[].isOwn` | boolean | 按当前会话计算，匿名恒为false |
| `data.records[].createTime` | Time | 创建时间，UTC |
| `data.records[].updateTime` | Time | 最后更新时间，UTC |
| `data.records[].notifyReply` | integer | 0不通知 1请求回复通知；不是邮箱验证凭据 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "messageId": "301",
        "parentId": null,
        "author": {
          "userId": "12",
          "nickname": "落叶",
          "avatar": null
        },
        "content": "你好，欢迎交流。",
        "htmlContent": "<p>你好，欢迎交流。</p>",
        "contentFormat": 0,
        "isSecret": 0,
        "status": 1,
        "editedAt": null,
        "isOwn": true,
        "createTime": "2026-10-30T02:00:00.000Z",
        "updateTime": "2026-10-30T02:00:00.000Z",
        "notifyReply": 0
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

仅user_id=当前用户，包括待审、驳回、私密与删除占位；createTime DESC,messageId DESC。无额外后台查询权限要求。

---

<a id="api-112"></a>

### 16.4 查询本人留言详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/me/messages/{messageId}` |
| 接口描述 | 查询本人留言详情（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用；仅当前会话本人 |
| 成功状态 | 200 OK |
| 并发校验 | 返回X-Revision，读取不要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `messageId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |

#### 请求示例

```http
GET /api/v1/me/messages/301
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | MessagePrivate | 本接口业务结果 |
| `data.messageId` | ID | 留言ID |
| `data.parentId` | ID&#124;null | 直接回复目标；根留言为null |
| `data.author` | object&#124;null | 当前用户公开资料；删除占位时为null |
| `data.author.userId` | ID | 本站用户ID |
| `data.author.nickname` | string | 当前昵称 |
| `data.author.avatar` | URL&#124;null | 当前公开头像 |
| `data.content` | string | 原文；删除占位固定为该留言已删除 |
| `data.htmlContent` | string | 安全渲染HTML；纯文本转义并保留换行 |
| `data.contentFormat` | integer | 0纯文本 1Markdown |
| `data.isSecret` | integer | 0公开 1悄悄话 |
| `data.status` | integer | 0待审核 1通过 2驳回 3删除占位 |
| `data.editedAt` | Time&#124;null | 最近作者编辑时间，未编辑为null |
| `data.isOwn` | boolean | 按当前会话计算，匿名恒为false |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| `data.notifyReply` | integer | 0不通知 1请求回复通知；不是邮箱验证凭据 |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "messageId": "301",
    "parentId": null,
    "author": {
      "userId": "12",
      "nickname": "落叶",
      "avatar": null
    },
    "content": "你好，欢迎交流。",
    "htmlContent": "<p>你好，欢迎交流。</p>",
    "contentFormat": 0,
    "isSecret": 0,
    "status": 1,
    "editedAt": null,
    "isOwn": true,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z",
    "notifyReply": 0
  },
  "errors": []
}
```

#### 业务说明

他人留言一律404；用于编辑前取得X-Revision。查询删除占位仅返回占位，不还原已清除内容。

---

<a id="api-113"></a>

### 16.5 编辑本人留言

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/me/messages/{messageId}` |
| 接口描述 | 编辑本人留言（待实现） |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `blog:message:updateOwn`；始终限制本人归属 |
| 成功状态 | 200 OK |
| 并发校验 | 要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `messageId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `If-Revision` | Header | string | 是 | 原样携带详情X-Revision；缺失428、不匹配412 |
| `content` | JSON Body | S(2000) | 是 | 去首尾空白后非空；Markdown保留原文内部空白 |
| `contentFormat` | JSON Body | integer | 是 | 0纯文本 1Markdown |
| `notifyReply` | JSON Body | integer | 是 | 0/1；见留言通知边界 |

#### 请求示例

```http
PUT /api/v1/me/messages/301
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "content": "你好，欢迎交流。",
  "contentFormat": 0,
  "notifyReply": 0
}
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | MessagePrivate | 本接口业务结果 |
| `data.messageId` | ID | 留言ID |
| `data.parentId` | ID&#124;null | 直接回复目标；根留言为null |
| `data.author` | object&#124;null | 当前用户公开资料；删除占位时为null |
| `data.author.userId` | ID | 本站用户ID |
| `data.author.nickname` | string | 当前昵称 |
| `data.author.avatar` | URL&#124;null | 当前公开头像 |
| `data.content` | string | 原文；删除占位固定为该留言已删除 |
| `data.htmlContent` | string | 安全渲染HTML；纯文本转义并保留换行 |
| `data.contentFormat` | integer | 0纯文本 1Markdown |
| `data.isSecret` | integer | 0公开 1悄悄话 |
| `data.status` | integer | 0待审核 1通过 2驳回 3删除占位 |
| `data.editedAt` | Time&#124;null | 最近作者编辑时间，未编辑为null |
| `data.isOwn` | boolean | 按当前会话计算，匿名恒为false |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| `data.notifyReply` | integer | 0不通知 1请求回复通知；不是邮箱验证凭据 |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "messageId": "301",
    "parentId": null,
    "author": {
      "userId": "12",
      "nickname": "落叶",
      "avatar": null
    },
    "content": "你好，欢迎交流。",
    "htmlContent": "<p>你好，欢迎交流。</p>",
    "contentFormat": 0,
    "isSecret": 0,
    "status": 0,
    "editedAt": "2026-10-30T02:00:00.000Z",
    "isOwn": true,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z",
    "notifyReply": 0
  },
  "errors": []
}
```

#### 业务说明

完整替换可编辑的content/contentFormat/notifyReply，不接受parentId、isSecret或status；私密范围和父关系创建后不可放宽。编辑写editedAt并重新审核；删除占位409。message.enabled=false时拒绝编辑，保留查询和删除。 先验证身份、权限与范围，再在同一事务锁定资源并校验If-Revision；重复目标也不能跳过版本校验。 请求体仅接受上表字段，列出的可空字段也必须显式出现；拒绝未知字段、错误类型和客户端审计字段。

---

<a id="api-114"></a>

### 16.6 删除本人留言

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/me/messages/{messageId}` |
| 接口描述 | 删除本人留言（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `blog:message:deleteOwn`；始终限制本人归属 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `messageId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |

#### 请求示例

```http
DELETE /api/v1/me/messages/301
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 本接口业务结果 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

无If-Revision，与本人评论删除一致；核对归属后清空原文为该留言已删除、contentFormat=0、notifyReply=0、status=3，保留回复链和私密范围。重复删除幂等，他人404。

---

<a id="api-115"></a>

### 16.7 分页查询后台留言

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/messages` |
| 接口描述 | 分页查询后台留言（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:message:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `page` | Query | integer | 否 | 默认1，1..100000 |
| `pageSize` | Query | integer | 否 | 默认10，1..100 |
| `status` | Query | integer | 否 | 0/1/2/3 |
| `userId` | Query | ID | 否 | 作者筛选 |
| `isSecret` | Query | integer | 否 | 0/1 |
| `begin` | Query | Time | 否 | createTime含下界 |
| `end` | Query | Time | 否 | createTime不含上界 |

#### 请求示例

```http
GET /api/v1/admin/messages?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<MessagePrivate> | 本接口业务结果 |
| `data.page` | integer | 当前页码 |
| `data.pageSize` | integer | 每页条数 |
| `data.total` | string | 同过滤条件的总条数 |
| `data.totalPages` | string | 总页数 |
| `data.records` | array | 无记录为[] |
| `data.records[].messageId` | ID | 留言ID |
| `data.records[].parentId` | ID&#124;null | 直接回复目标；根留言为null |
| `data.records[].author` | object&#124;null | 当前用户公开资料；删除占位时为null |
| `data.records[].author.userId` | ID | 本站用户ID |
| `data.records[].author.nickname` | string | 当前昵称 |
| `data.records[].author.avatar` | URL&#124;null | 当前公开头像 |
| `data.records[].content` | string | 原文；删除占位固定为该留言已删除 |
| `data.records[].htmlContent` | string | 安全渲染HTML；纯文本转义并保留换行 |
| `data.records[].contentFormat` | integer | 0纯文本 1Markdown |
| `data.records[].isSecret` | integer | 0公开 1悄悄话 |
| `data.records[].status` | integer | 0待审核 1通过 2驳回 3删除占位 |
| `data.records[].editedAt` | Time&#124;null | 最近作者编辑时间，未编辑为null |
| `data.records[].isOwn` | boolean | 按当前会话计算，匿名恒为false |
| `data.records[].createTime` | Time | 创建时间，UTC |
| `data.records[].updateTime` | Time | 最后更新时间，UTC |
| `data.records[].notifyReply` | integer | 0不通知 1请求回复通知；不是邮箱验证凭据 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "messageId": "301",
        "parentId": null,
        "author": {
          "userId": "12",
          "nickname": "落叶",
          "avatar": null
        },
        "content": "你好，欢迎交流。",
        "htmlContent": "<p>你好，欢迎交流。</p>",
        "contentFormat": 0,
        "isSecret": 0,
        "status": 1,
        "editedAt": null,
        "isOwn": false,
        "createTime": "2026-10-30T02:00:00.000Z",
        "updateTime": "2026-10-30T02:00:00.000Z",
        "notifyReply": 0
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

全站资源；授权角色仅有SELF范围时返回403，不能通过登录用户过滤制造虚构的本人数据。 筛选AND组合；begin<end；createTime DESC,messageId DESC。后台可读私密内容，不向前台公开后台投影。

---

<a id="api-116"></a>

### 16.8 查询后台留言详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/messages/{messageId}` |
| 接口描述 | 查询后台留言详情（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:message:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 返回X-Revision，读取不要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `messageId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |

#### 请求示例

```http
GET /api/v1/admin/messages/301
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | MessagePrivate | 本接口业务结果 |
| `data.messageId` | ID | 留言ID |
| `data.parentId` | ID&#124;null | 直接回复目标；根留言为null |
| `data.author` | object&#124;null | 当前用户公开资料；删除占位时为null |
| `data.author.userId` | ID | 本站用户ID |
| `data.author.nickname` | string | 当前昵称 |
| `data.author.avatar` | URL&#124;null | 当前公开头像 |
| `data.content` | string | 原文；删除占位固定为该留言已删除 |
| `data.htmlContent` | string | 安全渲染HTML；纯文本转义并保留换行 |
| `data.contentFormat` | integer | 0纯文本 1Markdown |
| `data.isSecret` | integer | 0公开 1悄悄话 |
| `data.status` | integer | 0待审核 1通过 2驳回 3删除占位 |
| `data.editedAt` | Time&#124;null | 最近作者编辑时间，未编辑为null |
| `data.isOwn` | boolean | 按当前会话计算，匿名恒为false |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| `data.notifyReply` | integer | 0不通知 1请求回复通知；不是邮箱验证凭据 |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "messageId": "301",
    "parentId": null,
    "author": {
      "userId": "12",
      "nickname": "落叶",
      "avatar": null
    },
    "content": "你好，欢迎交流。",
    "htmlContent": "<p>你好，欢迎交流。</p>",
    "contentFormat": 0,
    "isSecret": 0,
    "status": 1,
    "editedAt": null,
    "isOwn": false,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z",
    "notifyReply": 0
  },
  "errors": []
}
```

#### 业务说明

后台全局资源，必须检查本次权限的ALL范围；目标不存在404。

---

<a id="api-117"></a>

### 16.9 审核留言

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/messages/{messageId}/review` |
| 接口描述 | 审核留言（待实现） |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `blog:message:review` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `messageId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `If-Revision` | Header | string | 是 | 原样携带详情X-Revision；缺失428、不匹配412 |
| `status` | JSON Body | integer | 是 | 1通过 2驳回 |

#### 请求示例

```http
PUT /api/v1/admin/messages/301/review
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "status": 1
}
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | MessagePrivate | 本接口业务结果 |
| `data.messageId` | ID | 留言ID |
| `data.parentId` | ID&#124;null | 直接回复目标；根留言为null |
| `data.author` | object&#124;null | 当前用户公开资料；删除占位时为null |
| `data.author.userId` | ID | 本站用户ID |
| `data.author.nickname` | string | 当前昵称 |
| `data.author.avatar` | URL&#124;null | 当前公开头像 |
| `data.content` | string | 原文；删除占位固定为该留言已删除 |
| `data.htmlContent` | string | 安全渲染HTML；纯文本转义并保留换行 |
| `data.contentFormat` | integer | 0纯文本 1Markdown |
| `data.isSecret` | integer | 0公开 1悄悄话 |
| `data.status` | integer | 0待审核 1通过 2驳回 3删除占位 |
| `data.editedAt` | Time&#124;null | 最近作者编辑时间，未编辑为null |
| `data.isOwn` | boolean | 按当前会话计算，匿名恒为false |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| `data.notifyReply` | integer | 0不通知 1请求回复通知；不是邮箱验证凭据 |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "messageId": "301",
    "parentId": null,
    "author": {
      "userId": "12",
      "nickname": "落叶",
      "avatar": null
    },
    "content": "你好，欢迎交流。",
    "htmlContent": "<p>你好，欢迎交流。</p>",
    "contentFormat": 0,
    "isSecret": 0,
    "status": 1,
    "editedAt": null,
    "isOwn": false,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z",
    "notifyReply": 0
  },
  "errors": []
}
```

#### 业务说明

先检查ALL与版本，删除占位不可审核。父链待审或驳回时不允许通过子节点；重复目标幂等。通过审核不会公开悄悄话。通知仅在实际转为通过且事务提交后触发。 先验证身份、权限与范围，再在同一事务锁定资源并校验If-Revision；重复目标也不能跳过版本校验。 请求体仅接受上表字段，列出的可空字段也必须显式出现；拒绝未知字段、错误类型和客户端审计字段。

---

<a id="api-118"></a>

### 16.10 后台删除留言

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/messages/{messageId}` |
| 接口描述 | 后台删除留言（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:message:moderate` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `messageId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |

#### 请求示例

```http
DELETE /api/v1/admin/messages/301
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 本接口业务结果 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

与本人删除同样占位并清除原文；不物理删除整棵线程、不改挂回复。重复删除幂等；需要ALL。

---

<a id="api-119"></a>

### 16.11 后台回复留言

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/messages/{messageId}/replies` |
| 接口描述 | 后台回复留言（待实现） |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `blog:message:review` + 该操作的ALL范围 |
| 成功状态 | 201 Created |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `messageId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `content` | JSON Body | S(2000) | 是 | 去首尾空白后非空；Markdown保留原文内部空白 |
| `contentFormat` | JSON Body | integer | 是 | 0纯文本 1Markdown |
| `notifyReply` | JSON Body | integer | 是 | 0/1；见留言通知边界 |

#### 请求示例

```http
POST /api/v1/admin/messages/301/replies
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "content": "你好，欢迎交流。",
  "contentFormat": 0,
  "notifyReply": 0
}
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | MessagePrivate | 本接口业务结果 |
| `data.messageId` | ID | 留言ID |
| `data.parentId` | ID&#124;null | 直接回复目标；根留言为null |
| `data.author` | object&#124;null | 当前用户公开资料；删除占位时为null |
| `data.author.userId` | ID | 本站用户ID |
| `data.author.nickname` | string | 当前昵称 |
| `data.author.avatar` | URL&#124;null | 当前公开头像 |
| `data.content` | string | 原文；删除占位固定为该留言已删除 |
| `data.htmlContent` | string | 安全渲染HTML；纯文本转义并保留换行 |
| `data.contentFormat` | integer | 0纯文本 1Markdown |
| `data.isSecret` | integer | 0公开 1悄悄话 |
| `data.status` | integer | 0待审核 1通过 2驳回 3删除占位 |
| `data.editedAt` | Time&#124;null | 最近作者编辑时间，未编辑为null |
| `data.isOwn` | boolean | 按当前会话计算，匿名恒为false |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| `data.notifyReply` | integer | 0不通知 1请求回复通知；不是邮箱验证凭据 |

#### 响应示例

```http
HTTP/1.1 201 Created
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "messageId": "301",
    "parentId": null,
    "author": {
      "userId": "12",
      "nickname": "落叶",
      "avatar": null
    },
    "content": "你好，欢迎交流。",
    "htmlContent": "<p>你好，欢迎交流。</p>",
    "contentFormat": 0,
    "isSecret": 0,
    "status": 1,
    "editedAt": null,
    "isOwn": true,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z",
    "notifyReply": 0
  },
  "errors": []
}
```

#### 业务说明

使用现有审核权限+ALL，没有新增未定义权限码；作者为当前后台用户，父节点固定Path，继承整条私密范围，初始status=1。父链必须允许展示，待审/驳回/占位目标409；message.enabled=false时拒绝。不接受isAdminReply标志，展示管理员身份时实时核验有效角色。 请求体仅接受上表字段，列出的可空字段也必须显式出现；拒绝未知字段、错误类型和客户端审计字段。 创建成功不设置Location，通过响应ID及本人/后台详情入口定位。

---

<a id="group-17"></a>

## 17. 背景音乐与播放列表

本章全部待实现，使用blog_music。保留旧站曲目、封面、歌词、排序与隐藏功能。音频和歌词使用外部HTTP(S)URL，服务端只保存元数据；现有图片上传接口不因此开放音频或歌词上传。

<a id="api-120"></a>

### 17.1 分页查询公开播放列表

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/music` |
| 接口描述 | 分页查询公开播放列表（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 公开；仅按本接口声明的能力与可见范围访问 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `page` | Query | integer | 否 | 默认1，1..100000 |
| `pageSize` | Query | integer | 否 | 默认10，1..100 |

#### 请求示例

```http
GET /api/v1/blog/music?page=1&pageSize=10
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<MusicPublic> | 本接口业务结果 |
| `data.page` | integer | 当前页码 |
| `data.pageSize` | integer | 每页条数 |
| `data.total` | string | 同过滤条件的总条数 |
| `data.totalPages` | string | 总页数 |
| `data.records` | array | 无记录为[] |
| `data.records[].musicId` | ID | 音乐ID |
| `data.records[].title` | S(200) | 曲目标题 |
| `data.records[].artist` | S(200)? | 作者或歌手 |
| `data.records[].durationSeconds` | integer&#124;null | 0..4294967295秒，未知为null |
| `data.records[].coverUrl` | URL? | 外部HTTP(S)封面 |
| `data.records[].audioUrl` | URL | 外部HTTP(S)音频URL；保存不触发服务端下载 |
| `data.records[].lyricUrl` | URL? | 歌词URL；无歌词时为null |
| `data.records[].lyricType` | string&#124;null | lrc/json/txt；必须与lyricUrl同时为空或同时非空 |
| `data.records[].sortOrder` | int32 | 越小越靠前 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "musicId": "51",
        "title": "秋日",
        "artist": null,
        "durationSeconds": 180,
        "coverUrl": null,
        "audioUrl": "https://example.test/audio.mp3",
        "lyricUrl": null,
        "lyricType": null,
        "sortOrder": 0
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

仅status=1，sortOrder ASC,musicId ASC；不由服务端代理或抓取音频。是否有歌词由lyricUrl判断，不重复存储hasLyric。

---

<a id="api-121"></a>

### 17.2 分页查询后台音乐

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/music` |
| 接口描述 | 分页查询后台音乐（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:music:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `page` | Query | integer | 否 | 默认1，1..100000 |
| `pageSize` | Query | integer | 否 | 默认10，1..100 |
| `keyword` | Query | S(100) | 否 | 标题或作者字面包含 |
| `status` | Query | integer | 否 | 0/1，可选 |

#### 请求示例

```http
GET /api/v1/admin/music?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<Music> | 本接口业务结果 |
| `data.page` | integer | 当前页码 |
| `data.pageSize` | integer | 每页条数 |
| `data.total` | string | 同过滤条件的总条数 |
| `data.totalPages` | string | 总页数 |
| `data.records` | array | 无记录为[] |
| `data.records[].musicId` | ID | 音乐ID |
| `data.records[].title` | S(200) | 曲目标题 |
| `data.records[].artist` | S(200)? | 作者或歌手 |
| `data.records[].durationSeconds` | integer&#124;null | 0..4294967295秒，未知为null |
| `data.records[].coverUrl` | URL? | 外部HTTP(S)封面 |
| `data.records[].audioUrl` | URL | 外部HTTP(S)音频URL；保存不触发服务端下载 |
| `data.records[].lyricUrl` | URL? | 歌词URL；无歌词时为null |
| `data.records[].lyricType` | string&#124;null | lrc/json/txt；必须与lyricUrl同时为空或同时非空 |
| `data.records[].sortOrder` | int32 | 越小越靠前 |
| `data.records[].status` | integer | 0隐藏 1展示 |
| `data.records[].createTime` | Time | 创建时间，UTC |
| `data.records[].updateTime` | Time | 最后更新时间，UTC |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "musicId": "51",
        "title": "秋日",
        "artist": null,
        "durationSeconds": 180,
        "coverUrl": null,
        "audioUrl": "https://example.test/audio.mp3",
        "lyricUrl": null,
        "lyricType": null,
        "sortOrder": 0,
        "status": 1,
        "createTime": "2026-10-30T02:00:00.000Z",
        "updateTime": "2026-10-30T02:00:00.000Z"
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

keyword匹配title或artist，筛选AND组合；sortOrder ASC,musicId ASC。全站资源，需ALL。

---

<a id="api-122"></a>

### 17.3 查询音乐详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/music/{musicId}` |
| 接口描述 | 查询音乐详情（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:music:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 返回X-Revision，读取不要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `musicId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |

#### 请求示例

```http
GET /api/v1/admin/music/51
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Music | 本接口业务结果 |
| `data.musicId` | ID | 音乐ID |
| `data.title` | S(200) | 曲目标题 |
| `data.artist` | S(200)? | 作者或歌手 |
| `data.durationSeconds` | integer&#124;null | 0..4294967295秒，未知为null |
| `data.coverUrl` | URL? | 外部HTTP(S)封面 |
| `data.audioUrl` | URL | 外部HTTP(S)音频URL；保存不触发服务端下载 |
| `data.lyricUrl` | URL? | 歌词URL；无歌词时为null |
| `data.lyricType` | string&#124;null | lrc/json/txt；必须与lyricUrl同时为空或同时非空 |
| `data.sortOrder` | int32 | 越小越靠前 |
| `data.status` | integer | 0隐藏 1展示 |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "musicId": "51",
    "title": "秋日",
    "artist": null,
    "durationSeconds": 180,
    "coverUrl": null,
    "audioUrl": "https://example.test/audio.mp3",
    "lyricUrl": null,
    "lyricType": null,
    "sortOrder": 0,
    "status": 1,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

后台全局资源，必须检查本次权限的ALL范围；目标不存在404。

---

<a id="api-123"></a>

### 17.4 创建音乐

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/music` |
| 接口描述 | 创建音乐（待实现） |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `blog:music:create` + 该操作的ALL范围 |
| 成功状态 | 201 Created |
| 并发校验 | 返回X-Revision，读取不要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `title` | JSON Body | S(200) | 是 | 曲目标题 |
| `artist` | JSON Body | S(200)? | 是，可null | 作者或歌手 |
| `durationSeconds` | JSON Body | integer&#124;null | 是，可null | 0..4294967295秒，未知为null |
| `coverUrl` | JSON Body | URL? | 是，可null | 外部HTTP(S)封面 |
| `audioUrl` | JSON Body | URL | 是 | 外部HTTP(S)音频URL；保存不触发服务端下载 |
| `lyricUrl` | JSON Body | URL? | 是，可null | 歌词URL；无歌词时为null |
| `lyricType` | JSON Body | string&#124;null | 是，可null | lrc/json/txt；必须与lyricUrl同时为空或同时非空 |
| `sortOrder` | JSON Body | int32 | 是 | 越小越靠前 |
| `status` | JSON Body | integer | 是 | 0隐藏 1展示 |

#### 请求示例

```http
POST /api/v1/admin/music
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "title": "秋日",
  "artist": null,
  "durationSeconds": 180,
  "coverUrl": null,
  "audioUrl": "https://example.test/audio.mp3",
  "lyricUrl": null,
  "lyricType": null,
  "sortOrder": 0,
  "status": 1
}
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Music | 本接口业务结果 |
| `data.musicId` | ID | 音乐ID |
| `data.title` | S(200) | 曲目标题 |
| `data.artist` | S(200)? | 作者或歌手 |
| `data.durationSeconds` | integer&#124;null | 0..4294967295秒，未知为null |
| `data.coverUrl` | URL? | 外部HTTP(S)封面 |
| `data.audioUrl` | URL | 外部HTTP(S)音频URL；保存不触发服务端下载 |
| `data.lyricUrl` | URL? | 歌词URL；无歌词时为null |
| `data.lyricType` | string&#124;null | lrc/json/txt；必须与lyricUrl同时为空或同时非空 |
| `data.sortOrder` | int32 | 越小越靠前 |
| `data.status` | integer | 0隐藏 1展示 |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 201 Created
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
Location: /api/v1/admin/music/51
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "musicId": "51",
    "title": "秋日",
    "artist": null,
    "durationSeconds": 180,
    "coverUrl": null,
    "audioUrl": "https://example.test/audio.mp3",
    "lyricUrl": null,
    "lyricType": null,
    "sortOrder": 0,
    "status": 1,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

lyricUrl与lyricType同时为空或同时非空；durationSeconds可为null。使用独立上传服务的外部资源时，运营者自行确保URL可公开播放，不接受本站私密附件路径。 请求体仅接受上表字段，列出的可空字段也必须显式出现；拒绝未知字段、错误类型和客户端审计字段。

---

<a id="api-124"></a>

### 17.5 修改音乐

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/music/{musicId}` |
| 接口描述 | 修改音乐（待实现） |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `blog:music:update` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `musicId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `If-Revision` | Header | string | 是 | 原样携带详情X-Revision；缺失428、不匹配412 |
| `title` | JSON Body | S(200) | 是 | 曲目标题 |
| `artist` | JSON Body | S(200)? | 是，可null | 作者或歌手 |
| `durationSeconds` | JSON Body | integer&#124;null | 是，可null | 0..4294967295秒，未知为null |
| `coverUrl` | JSON Body | URL? | 是，可null | 外部HTTP(S)封面 |
| `audioUrl` | JSON Body | URL | 是 | 外部HTTP(S)音频URL；保存不触发服务端下载 |
| `lyricUrl` | JSON Body | URL? | 是，可null | 歌词URL；无歌词时为null |
| `lyricType` | JSON Body | string&#124;null | 是，可null | lrc/json/txt；必须与lyricUrl同时为空或同时非空 |
| `sortOrder` | JSON Body | int32 | 是 | 越小越靠前 |
| `status` | JSON Body | integer | 是 | 0隐藏 1展示 |

#### 请求示例

```http
PUT /api/v1/admin/music/51
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "title": "秋日",
  "artist": null,
  "durationSeconds": 180,
  "coverUrl": null,
  "audioUrl": "https://example.test/audio.mp3",
  "lyricUrl": null,
  "lyricType": null,
  "sortOrder": 0,
  "status": 1
}
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Music | 本接口业务结果 |
| `data.musicId` | ID | 音乐ID |
| `data.title` | S(200) | 曲目标题 |
| `data.artist` | S(200)? | 作者或歌手 |
| `data.durationSeconds` | integer&#124;null | 0..4294967295秒，未知为null |
| `data.coverUrl` | URL? | 外部HTTP(S)封面 |
| `data.audioUrl` | URL | 外部HTTP(S)音频URL；保存不触发服务端下载 |
| `data.lyricUrl` | URL? | 歌词URL；无歌词时为null |
| `data.lyricType` | string&#124;null | lrc/json/txt；必须与lyricUrl同时为空或同时非空 |
| `data.sortOrder` | int32 | 越小越靠前 |
| `data.status` | integer | 0隐藏 1展示 |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "musicId": "51",
    "title": "秋日",
    "artist": null,
    "durationSeconds": 180,
    "coverUrl": null,
    "audioUrl": "https://example.test/audio.mp3",
    "lyricUrl": null,
    "lyricType": null,
    "sortOrder": 0,
    "status": 1,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

完整替换；隐藏后立即从公开列表消失，不等于删除外部存储对象。 先验证身份、权限与范围，再在同一事务锁定资源并校验If-Revision；重复目标也不能跳过版本校验。 请求体仅接受上表字段，列出的可空字段也必须显式出现；拒绝未知字段、错误类型和客户端审计字段。

---

<a id="api-125"></a>

### 17.6 删除音乐

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/music/{musicId}` |
| 接口描述 | 删除音乐（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:music:delete` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `musicId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `If-Revision` | Header | string | 是 | 原样携带详情X-Revision；缺失428、不匹配412 |

#### 请求示例

```http
DELETE /api/v1/admin/music/51
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 本接口业务结果 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

仅删除数据库曲目元数据，不删除外部音频、封面或歌词。 先验证身份、权限与范围，再在同一事务锁定资源并校验If-Revision；重复目标也不能跳过版本校验。

---

<a id="group-18"></a>

## 18. 邮件订阅、RSS Feed 与站点地图

本章全部待实现。XML Feed为公开文章输出，不需要邮箱；邮件推送需本站账号及独立邮箱确认。订阅邮箱不自动绑定为登录邮箱，不复刻旧visitorToken、JWT退订凭据或仅凭邮箱退订的入口。

<a id="api-126"></a>

### 18.1 查询本人邮件订阅

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/me/rss-subscription` |
| 接口描述 | 查询本人邮件订阅（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用；仅当前会话本人 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |

#### 请求示例

```http
GET /api/v1/me/rss-subscription
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Subscription&#124;null | 未配置的主页资料或不存在的本人订阅可为null；非空完整形状如下 |
| `data.subscriptionId` | ID | 订阅ID |
| `data.email` | string | 订阅邮箱，≤254；服务端trim并转小写 |
| `data.status` | integer | 0待验证 1已订阅 2已退订 |
| `data.verifiedAt` | Time&#124;null | 当前邮箱验证成功时间 |
| `data.subscribeTime` | Time&#124;null | 最近激活时间 |
| `data.unsubscribeTime` | Time&#124;null | 最近退订时间 |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "subscriptionId": "401",
    "email": "leaf@example.test",
    "status": 0,
    "verifiedAt": null,
    "subscribeTime": null,
    "unsubscribeTime": null,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

仅当前会话用户；未创建时data=null。返回真实状态，不把待验证视为已订阅；不返回确认或退订票据。

---

<a id="api-127"></a>

### 18.2 申请或修改本人邮件订阅

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/me/rss-subscription` |
| 接口描述 | 申请或修改本人邮件订阅（待实现） |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `blog:rss:subscribe`；始终限制本人归属 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `email` | JSON Body | string | 是 | 合法邮箱≤254；trim并转小写 |

#### 请求示例

```http
PUT /api/v1/me/rss-subscription
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "email": "leaf@example.test"
}
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Subscription | 本接口业务结果 |
| `data.subscriptionId` | ID | 订阅ID |
| `data.email` | string | 订阅邮箱，≤254；服务端trim并转小写 |
| `data.status` | integer | 0待验证 1已订阅 2已退订 |
| `data.verifiedAt` | Time&#124;null | 当前邮箱验证成功时间 |
| `data.subscribeTime` | Time&#124;null | 最近激活时间 |
| `data.unsubscribeTime` | Time&#124;null | 最近退订时间 |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "subscriptionId": "401",
    "email": "leaf@example.test",
    "status": 0,
    "verifiedAt": null,
    "subscribeTime": null,
    "unsubscribeTime": null,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

首次或换邮箱写status=0、verifiedAt/subscribeTime/unsubscribeTime=null，旧邮箱推送立即停止；同一已激活邮箱幂等，不重复发信。待验证邮箱重试会重发确认邮件，限流返回429。确认票据24小时有效，绑定用户、subscriptionId、当前email及本次申请版本。SMTP/Redis失败503，已提交待验证记录可保留，通过重发恢复；不返回已激活成功。账号与邮箱唯一冲突409，不挪用其他用户订阅。 请求体仅接受上表字段，列出的可空字段也必须显式出现；拒绝未知字段、错误类型和客户端审计字段。

---

<a id="api-128"></a>

### 18.3 确认本人订阅邮箱

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/me/rss-subscription/confirm` |
| 接口描述 | 确认本人订阅邮箱（待实现） |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `blog:rss:subscribe`；始终限制本人归属 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `ticket` | JSON Body | S(512) | 是 | 从确认邮件完成页读取的不透明票据；不得放日志 |

#### 请求示例

```http
POST /api/v1/me/rss-subscription/confirm
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "ticket": "confirmation-ticket-example"
}
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Subscription | 本接口业务结果 |
| `data.subscriptionId` | ID | 订阅ID |
| `data.email` | string | 订阅邮箱，≤254；服务端trim并转小写 |
| `data.status` | integer | 0待验证 1已订阅 2已退订 |
| `data.verifiedAt` | Time&#124;null | 当前邮箱验证成功时间 |
| `data.subscribeTime` | Time&#124;null | 最近激活时间 |
| `data.unsubscribeTime` | Time&#124;null | 最近退订时间 |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "subscriptionId": "401",
    "email": "leaf@example.test",
    "status": 1,
    "verifiedAt": "2026-10-30T02:00:00.000Z",
    "subscribeTime": "2026-10-30T02:00:00.000Z",
    "unsubscribeTime": null,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

需已登录的同一本站用户；可在另一个浏览器登录同一账号确认，不依赖旧访客存储。锁用户及订阅，验证当前pending状态、邮箱和申请版本，Redis原子消费后设置verifiedAt、subscribeTime、status=1。过期/复用/错账号或已被新申请替换统一400/SUBSCRIPTION_TICKET_INVALID。数据库失败回滚，已消费票据需重新申请；不把Redis与MySQL描述为共同事务。 请求体仅接受上表字段，列出的可空字段也必须显式出现；拒绝未知字段、错误类型和客户端审计字段。

---

<a id="api-129"></a>

### 18.4 取消本人邮件订阅

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/me/rss-subscription` |
| 接口描述 | 取消本人邮件订阅（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `blog:rss:unsubscribe`；始终限制本人归属 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |

#### 请求示例

```http
DELETE /api/v1/me/rss-subscription
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 本接口业务结果 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

身份只取会话，无If-Revision。将本人记录置status=2并写unsubscribeTime；不存在或已退订幂等200。保留已验证邮箱信息，重订需走申请及确认。后台ALL不扩大到他人。

---

<a id="api-130"></a>

### 18.5 通过邮件链接退订

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/rss-subscriptions/unsubscribe` |
| 接口描述 | 通过邮件链接退订（待实现） |
| Content-Type | application/json |
| 访问要求 | 公开；仅按本接口声明的能力与可见范围访问 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `ticket` | JSON Body | S(512) | 是 | 仅限本订阅退订的不可猜票据 |

#### 请求示例

```http
POST /api/v1/blog/rss-subscriptions/unsubscribe
Content-Type: application/json

{
  "ticket": "unsubscribe-ticket-example"
}
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 本接口业务结果 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

这是显式公开的票据能力接口；不需要Bearer，也不颁发会话。票据存Redis，有效30天，绑定subscriptionId、email、verifiedAt和本轮subscribeTime；只能将该订阅改为退订。相同有效票据重复退订200，邮箱或订阅轮次变化后400/SUBSCRIPTION_TICKET_INVALID。邮件链接指向固定前端完成页#unsubscribeTicket=...，页面清除fragment并由用户确认后POST；邮件扫描器GET不改变状态。不接受email/userId作为替代凭据。 请求体仅接受上表字段，列出的可空字段也必须显式出现；拒绝未知字段、错误类型和客户端审计字段。

---

<a id="api-131"></a>

### 18.6 分页查询后台邮件订阅

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/rss-subscriptions` |
| 接口描述 | 分页查询后台邮件订阅（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:rss:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `page` | Query | integer | 否 | 默认1，1..100000 |
| `pageSize` | Query | integer | 否 | 默认10，1..100 |
| `email` | Query | S(254) | 否 | 邮箱字面包含 |
| `userId` | Query | ID | 否 | 所属用户 |
| `status` | Query | integer | 否 | 0待验证 1已订阅 2已退订 |

#### 请求示例

```http
GET /api/v1/admin/rss-subscriptions?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<SubscriptionAdmin> | 本接口业务结果 |
| `data.page` | integer | 当前页码 |
| `data.pageSize` | integer | 每页条数 |
| `data.total` | string | 同过滤条件的总条数 |
| `data.totalPages` | string | 总页数 |
| `data.records` | array | 无记录为[] |
| `data.records[].userId` | ID | 所属本站用户 |
| `data.records[].subscriptionId` | ID | 订阅ID |
| `data.records[].email` | string | 订阅邮箱，≤254；服务端trim并转小写 |
| `data.records[].status` | integer | 0待验证 1已订阅 2已退订 |
| `data.records[].verifiedAt` | Time&#124;null | 当前邮箱验证成功时间 |
| `data.records[].subscribeTime` | Time&#124;null | 最近激活时间 |
| `data.records[].unsubscribeTime` | Time&#124;null | 最近退订时间 |
| `data.records[].createTime` | Time | 创建时间，UTC |
| `data.records[].updateTime` | Time | 最后更新时间，UTC |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "userId": "12",
        "subscriptionId": "401",
        "email": "leaf@example.test",
        "status": 0,
        "verifiedAt": null,
        "subscribeTime": null,
        "unsubscribeTime": null,
        "createTime": "2026-10-30T02:00:00.000Z",
        "updateTime": "2026-10-30T02:00:00.000Z"
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

需要ALL；createTime DESC,subscriptionId DESC。无未分页的全量邮箱导出接口。

---

<a id="api-132"></a>

### 18.7 查询邮件订阅详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/rss-subscriptions/{subscriptionId}` |
| 接口描述 | 查询邮件订阅详情（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:rss:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 返回X-Revision，读取不要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `subscriptionId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |

#### 请求示例

```http
GET /api/v1/admin/rss-subscriptions/401
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | SubscriptionAdmin | 本接口业务结果 |
| `data.userId` | ID | 所属本站用户 |
| `data.subscriptionId` | ID | 订阅ID |
| `data.email` | string | 订阅邮箱，≤254；服务端trim并转小写 |
| `data.status` | integer | 0待验证 1已订阅 2已退订 |
| `data.verifiedAt` | Time&#124;null | 当前邮箱验证成功时间 |
| `data.subscribeTime` | Time&#124;null | 最近激活时间 |
| `data.unsubscribeTime` | Time&#124;null | 最近退订时间 |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "userId": "12",
    "subscriptionId": "401",
    "email": "leaf@example.test",
    "status": 0,
    "verifiedAt": null,
    "subscribeTime": null,
    "unsubscribeTime": null,
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

后台全局资源，必须检查本次权限的ALL范围；目标不存在404。

---

<a id="api-133"></a>

### 18.8 管理邮件订阅状态

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/rss-subscriptions/{subscriptionId}/status` |
| 接口描述 | 管理邮件订阅状态（待实现） |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `blog:rss:update` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `subscriptionId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `If-Revision` | Header | string | 是 | 原样携带详情X-Revision；缺失428、不匹配412 |
| `status` | JSON Body | integer | 是 | 1激活 2退订 |

#### 请求示例

```http
PUT /api/v1/admin/rss-subscriptions/401/status
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "status": 2
}
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | SubscriptionAdmin | 本接口业务结果 |
| `data.userId` | ID | 所属本站用户 |
| `data.subscriptionId` | ID | 订阅ID |
| `data.email` | string | 订阅邮箱，≤254；服务端trim并转小写 |
| `data.status` | integer | 0待验证 1已订阅 2已退订 |
| `data.verifiedAt` | Time&#124;null | 当前邮箱验证成功时间 |
| `data.subscribeTime` | Time&#124;null | 最近激活时间 |
| `data.unsubscribeTime` | Time&#124;null | 最近退订时间 |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "userId": "12",
    "subscriptionId": "401",
    "email": "leaf@example.test",
    "status": 2,
    "verifiedAt": null,
    "subscribeTime": null,
    "unsubscribeTime": "2026-10-30T02:00:00.000Z",
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

只更新状态，不允许写userId/email/verifiedAt。激活仅允许当前邮箱已经验证且所属用户启用；待验证状态不能被管理员绕过，409/STATE_CONFLICT。激活写subscribeTime并清空unsubscribeTime；退订写unsubscribeTime。重新激活会更新订阅轮次并使旧退订票据失效；退订保留当前轮次，支持相同票据幂等退订。 先验证身份、权限与范围，再在同一事务锁定资源并校验If-Revision；重复目标也不能跳过版本校验。 请求体仅接受上表字段，列出的可空字段也必须显式出现；拒绝未知字段、错误类型和客户端审计字段。

---

<a id="api-134"></a>

### 18.9 删除邮件订阅

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/rss-subscriptions/{subscriptionId}` |
| 接口描述 | 删除邮件订阅（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:rss:delete` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `subscriptionId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `If-Revision` | Header | string | 是 | 原样携带详情X-Revision；缺失428、不匹配412 |

#### 请求示例

```http
DELETE /api/v1/admin/rss-subscriptions/401
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 本接口业务结果 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

需ALL；物理删除，旧确认/退订票据因目标不存在失效，不自动转移邮箱归属。 先验证身份、权限与范围，再在同一事务锁定资源并校验If-Revision；重复目标也不能跳过版本校验。

---

<a id="api-135"></a>

### 18.10 读取公开RSS Feed

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/rss.xml` |
| 接口描述 | 读取公开RSS Feed（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 公开；仅按本接口声明的能力与可见范围访问 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

无Path、Query或请求体参数。

#### 请求示例

```http
GET /api/v1/blog/rss.xml
```

#### 响应参数

成功输出XML；错误仍遵守第14章HTTP与Result契约。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| 响应体 | XML | 原始XML文档，不包Result |
| Content-Type（Header） | string | 本接口声明的XML媒体类型；错误仍为JSON Result |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/rss+xml; charset=UTF-8
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0"><channel><title>DriftingLeaves</title><link>https://blog.example.test/</link><description>记录与分享</description><item><title>第一片落叶</title><link>https://blog.example.test/articles/first-leaf</link><guid isPermaLink="true">https://blog.example.test/articles/first-leaf</guid><pubDate>Fri, 30 Oct 2026 02:00:00 GMT</pubDate><description>记录与分享</description></item></channel></rss>
```

#### 业务说明

成功Content-Type=application/rss+xml; charset=UTF-8，返回RSS2.0而非Result。取最近20篇PUBLIC文章，publishTime DESC,articleId DESC；guid使用稳定本站文章URL，link使用规范slug，pubDate为RFC822日期。description输出转义后的摘要，不输出Markdown、私密正文或邮箱。配置的公开博客基址由服务端提供，不能信任任意Host头。

---

<a id="api-136"></a>

### 18.11 读取公开站点地图

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/sitemap.xml` |
| 接口描述 | 读取公开站点地图（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 公开；仅按本接口声明的能力与可见范围访问 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `page` | Query | integer | 否 | 可选，正整数；仅供sitemapindex的子图链接使用 |

#### 请求示例

```http
GET /api/v1/blog/sitemap.xml
```

#### 响应参数

成功输出XML；错误仍遵守第14章HTTP与Result契约。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| 响应体 | XML | 原始XML文档，不包Result |
| Content-Type（Header） | string | 本接口声明的XML媒体类型；错误仍为JSON Result |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/xml; charset=UTF-8
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"><url><loc>https://blog.example.test/articles/first-leaf</loc><lastmod>2026-10-30T02:00:00.000Z</lastmod></url></urlset>
```

#### 业务说明

成功application/xml; charset=UTF-8，不包Result。只输出站点公开入口、PUBLIC文章及可公开分类/标签URL；使用安全XML编码，lastmod取公开资源更新时间，UTC格式。超过单文件50000条或未压缩50MiB时返回sitemapindex；同一路径支持page正整数查询读取有界子图，未知页404。不得悄悄截断已发布URL。任何缓存均需随发布、下架、回收、可见性或slug变化失效，不能继续暴露非PUBLIC条目。

---

<a id="group-19"></a>

## 19. 访客、浏览记录与统计报表

本章全部待实现。匿名统计标识与Sa-Token账号完全分开，封禁统计标识不能停用本站账号，也不能凭统计记录取得评论、点赞或订阅权限。报表范围、PV/UV与累计阅读数口径见第12.13节。

<a id="api-137"></a>

### 19.1 记录站点页面浏览

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/analytics/page-views` |
| 接口描述 | 记录站点页面浏览（待实现） |
| Content-Type | application/json |
| 访问要求 | 公开；可选Bearer，携带时必须校验有效会话和账号状态 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 否 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `fingerprint` | JSON Body | string | 是 | ASCII字母数字或连字符16..150；浏览器随机统计标识，可伪造，不能作为身份 |
| `siteCode` | JSON Body | string | 是 | home/blog/cv；服务端与明确配置的来源站点核验 |
| `pagePath` | JSON Body | S(2048) | 是 | 规范站内路径，必须以/起始，剔除fragment及全部query |
| `pageTitle` | JSON Body | S(200)? | 是，可null | 展示标题，不作为鉴权或资源定位依据 |
| `referer` | JSON Body | URL? | 是，可null | 来源URL；服务端再次脱敏并去掉query/fragment |
| `articleId` | JSON Body | ID&#124;null | 是，可null | 仅blog文章详情可传；需PUBLIC且与pagePath的规范slug匹配 |

#### 请求示例

```http
POST /api/v1/analytics/page-views
Content-Type: application/json

{
  "fingerprint": "d681988a-cbd0-4ef6-bdec-f84bfa234e16",
  "siteCode": "home",
  "pagePath": "/",
  "pageTitle": "DriftingLeaves",
  "referer": null,
  "articleId": null
}
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | TrackingResult | 本接口业务结果 |
| `data.recorded` | boolean | 数据库成功记录本次浏览后为true，不返回访客会话 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "recorded": true
  },
  "errors": []
}
```

#### 业务说明

IP/UA、viewTime和userId由服务端读取；不接受visitorId、用户ID、会话ID或Token字段。携带Bearer时必须有效，匿名不创建sys_user。事务写访客首次/最近时间及一条page_view后返回recorded=true；失败503不伪造已记录。该POST不增加blog_article.view_count，文章成功详情读取仍按7.2计一次，避免双计。受限统计标识返回403/VISITOR_BLOCKED，只影响此采集入口；可伪造标识不能用于阻止公开阅读。重复POST可能产生多条浏览，无通用幂等键。 请求体仅接受上表字段，列出的可空字段也必须显式出现；拒绝未知字段、错误类型和客户端审计字段。

---

<a id="api-138"></a>

### 19.2 分页查询访客

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/visitors` |
| 接口描述 | 分页查询访客（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:visitor:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `page` | Query | integer | 否 | 默认1，1..100000 |
| `pageSize` | Query | integer | 否 | 默认10，1..100 |
| `country` | Query | S(64) | 否 | 精确匹配 |
| `province` | Query | S(64) | 否 | 精确匹配 |
| `city` | Query | S(64) | 否 | 精确匹配 |
| `isBlocked` | Query | integer | 否 | 可选0/1，对持久化标记筛选 |

#### 请求示例

```http
GET /api/v1/admin/visitors?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<Visitor> | 本接口业务结果 |
| `data.page` | integer | 当前页码 |
| `data.pageSize` | integer | 每页条数 |
| `data.total` | string | 同过滤条件的总条数 |
| `data.totalPages` | string | 总页数 |
| `data.records` | array | 无记录为[] |
| `data.records[].visitorId` | ID | 统计访客ID，不是登录身份 |
| `data.records[].fingerprint` | string | ASCII统计标识，不可作为鉴权凭据 |
| `data.records[].ipAddress` | string&#124;null | 最近IP，仅后台可见 |
| `data.records[].userAgent` | string&#124;null | ≤1024，最近User-Agent |
| `data.records[].country` | string&#124;null | 国家 |
| `data.records[].province` | string&#124;null | 省份 |
| `data.records[].city` | string&#124;null | 城市 |
| `data.records[].isBlocked` | integer | 数据库封禁标记0/1；实际生效还需检查blockedUntil |
| `data.records[].blockedUntil` | Time&#124;null | 封禁结束，已封禁且null表示永久 |
| `data.records[].firstVisitTime` | Time | 首次访问时间 |
| `data.records[].lastVisitTime` | Time | 最近访问时间 |
| `data.records[].createTime` | Time | 创建时间，UTC |
| `data.records[].updateTime` | Time | 最后更新时间，UTC |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "visitorId": "501",
        "fingerprint": "d681988a-cbd0-4ef6-bdec-f84bfa234e16",
        "ipAddress": "192.0.2.1",
        "userAgent": null,
        "country": null,
        "province": null,
        "city": null,
        "isBlocked": 0,
        "blockedUntil": null,
        "firstVisitTime": "2026-10-30T02:00:00.000Z",
        "lastVisitTime": "2026-10-30T02:00:00.000Z",
        "createTime": "2026-10-30T02:00:00.000Z",
        "updateTime": "2026-10-30T02:00:00.000Z"
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

需ALL；lastVisitTime DESC,visitorId DESC。仅后台返回统计标识、IP与UA，不公开原始访客清单。

---

<a id="api-139"></a>

### 19.3 查询访客详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/visitors/{visitorId}` |
| 接口描述 | 查询访客详情（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:visitor:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 返回X-Revision，读取不要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `visitorId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |

#### 请求示例

```http
GET /api/v1/admin/visitors/501
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Visitor | 本接口业务结果 |
| `data.visitorId` | ID | 统计访客ID，不是登录身份 |
| `data.fingerprint` | string | ASCII统计标识，不可作为鉴权凭据 |
| `data.ipAddress` | string&#124;null | 最近IP，仅后台可见 |
| `data.userAgent` | string&#124;null | ≤1024，最近User-Agent |
| `data.country` | string&#124;null | 国家 |
| `data.province` | string&#124;null | 省份 |
| `data.city` | string&#124;null | 城市 |
| `data.isBlocked` | integer | 数据库封禁标记0/1；实际生效还需检查blockedUntil |
| `data.blockedUntil` | Time&#124;null | 封禁结束，已封禁且null表示永久 |
| `data.firstVisitTime` | Time | 首次访问时间 |
| `data.lastVisitTime` | Time | 最近访问时间 |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "visitorId": "501",
    "fingerprint": "d681988a-cbd0-4ef6-bdec-f84bfa234e16",
    "ipAddress": "192.0.2.1",
    "userAgent": null,
    "country": null,
    "province": null,
    "city": null,
    "isBlocked": 0,
    "blockedUntil": null,
    "firstVisitTime": "2026-10-30T02:00:00.000Z",
    "lastVisitTime": "2026-10-30T02:00:00.000Z",
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

X-Revision仅覆盖isBlocked/blockedUntil，排除会持续变化的最近访问/IP/UA；详情仍返回当前统计字段。

---

<a id="api-140"></a>

### 19.4 封禁访客统计标识

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/visitors/{visitorId}/block` |
| 接口描述 | 封禁访客统计标识（待实现） |
| Content-Type | application/json |
| 访问要求 | 登录且启用 + `admin:access` + `blog:visitor:block` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `visitorId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `If-Revision` | Header | string | 是 | 原样携带详情X-Revision；缺失428、不匹配412 |
| `blockedUntil` | JSON Body | Time&#124;null | 是，可null | 必须显式出现；null永久，否则晚于当前UTC时间 |

#### 请求示例

```http
PUT /api/v1/admin/visitors/501/block
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "blockedUntil": "2026-10-31T02:00:00.000Z"
}
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Visitor | 本接口业务结果 |
| `data.visitorId` | ID | 统计访客ID，不是登录身份 |
| `data.fingerprint` | string | ASCII统计标识，不可作为鉴权凭据 |
| `data.ipAddress` | string&#124;null | 最近IP，仅后台可见 |
| `data.userAgent` | string&#124;null | ≤1024，最近User-Agent |
| `data.country` | string&#124;null | 国家 |
| `data.province` | string&#124;null | 省份 |
| `data.city` | string&#124;null | 城市 |
| `data.isBlocked` | integer | 数据库封禁标记0/1；实际生效还需检查blockedUntil |
| `data.blockedUntil` | Time&#124;null | 封禁结束，已封禁且null表示永久 |
| `data.firstVisitTime` | Time | 首次访问时间 |
| `data.lastVisitTime` | Time | 最近访问时间 |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "visitorId": "501",
    "fingerprint": "d681988a-cbd0-4ef6-bdec-f84bfa234e16",
    "ipAddress": "192.0.2.1",
    "userAgent": null,
    "country": null,
    "province": null,
    "city": null,
    "isBlocked": 1,
    "blockedUntil": "2026-10-31T02:00:00.000Z",
    "firstVisitTime": "2026-10-30T02:00:00.000Z",
    "lastVisitTime": "2026-10-30T02:00:00.000Z",
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

需ALL，置isBlocked=1；到期后采集入口按时间自动视为未封禁，不要求定时任务先更新数据库。本人账号状态不受影响。 先验证身份、权限与范围，再在同一事务锁定资源并校验If-Revision；重复目标也不能跳过版本校验。 请求体仅接受上表字段，列出的可空字段也必须显式出现；拒绝未知字段、错误类型和客户端审计字段。

---

<a id="api-141"></a>

### 19.5 解除访客统计限制

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/visitors/{visitorId}/unblock` |
| 接口描述 | 解除访客统计限制（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:visitor:unblock` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `visitorId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `If-Revision` | Header | string | 是 | 原样携带详情X-Revision；缺失428、不匹配412 |

#### 请求示例

```http
PUT /api/v1/admin/visitors/501/unblock
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Visitor | 本接口业务结果 |
| `data.visitorId` | ID | 统计访客ID，不是登录身份 |
| `data.fingerprint` | string | ASCII统计标识，不可作为鉴权凭据 |
| `data.ipAddress` | string&#124;null | 最近IP，仅后台可见 |
| `data.userAgent` | string&#124;null | ≤1024，最近User-Agent |
| `data.country` | string&#124;null | 国家 |
| `data.province` | string&#124;null | 省份 |
| `data.city` | string&#124;null | 城市 |
| `data.isBlocked` | integer | 数据库封禁标记0/1；实际生效还需检查blockedUntil |
| `data.blockedUntil` | Time&#124;null | 封禁结束，已封禁且null表示永久 |
| `data.firstVisitTime` | Time | 首次访问时间 |
| `data.lastVisitTime` | Time | 最近访问时间 |
| `data.createTime` | Time | 创建时间，UTC |
| `data.updateTime` | Time | 最后更新时间，UTC |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
X-Revision: "<current-revision>"
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "visitorId": "501",
    "fingerprint": "d681988a-cbd0-4ef6-bdec-f84bfa234e16",
    "ipAddress": "192.0.2.1",
    "userAgent": null,
    "country": null,
    "province": null,
    "city": null,
    "isBlocked": 0,
    "blockedUntil": null,
    "firstVisitTime": "2026-10-30T02:00:00.000Z",
    "lastVisitTime": "2026-10-30T02:00:00.000Z",
    "createTime": "2026-10-30T02:00:00.000Z",
    "updateTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

置isBlocked=0、blockedUntil=null；重复目标幂等。仅影响此标识的采集限制。 先验证身份、权限与范围，再在同一事务锁定资源并校验If-Revision；重复目标也不能跳过版本校验。

---

<a id="api-142"></a>

### 19.6 删除访客统计记录

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/visitors/{visitorId}` |
| 接口描述 | 删除访客统计记录（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:visitor:delete` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 要求If-Revision |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `visitorId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `If-Revision` | Header | string | 是 | 原样携带详情X-Revision；缺失428、不匹配412 |

#### 请求示例

```http
DELETE /api/v1/admin/visitors/501
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 本接口业务结果 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

物理删除；对应page_view.visitor_id由外键置NULL，保留PV明细，UV可能下降。不能使用此接口删除用户、留言、评论或订阅。 先验证身份、权限与范围，再在同一事务锁定资源并校验If-Revision；重复目标也不能跳过版本校验。

---

<a id="api-143"></a>

### 19.7 分页查询浏览记录

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/page-views` |
| 接口描述 | 分页查询浏览记录（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:view:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `page` | Query | integer | 否 | 默认1，1..100000 |
| `pageSize` | Query | integer | 否 | 默认10，1..100 |
| `visitorId` | Query | ID | 否 | 统计访客筛选 |
| `userId` | Query | ID | 否 | 采集时登录用户筛选 |
| `articleId` | Query | ID | 否 | 文章筛选 |
| `siteCode` | Query | string | 否 | home/blog/cv |
| `pagePath` | Query | S(2048) | 否 | 规范路径精确匹配 |
| `referer` | Query | S(2048) | 否 | 脱敏来源字面包含 |
| `begin` | Query | Time | 否 | viewTime含下界 |
| `end` | Query | Time | 否 | viewTime不含上界 |

#### 请求示例

```http
GET /api/v1/admin/page-views?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<PageView> | 本接口业务结果 |
| `data.page` | integer | 当前页码 |
| `data.pageSize` | integer | 每页条数 |
| `data.total` | string | 同过滤条件的总条数 |
| `data.totalPages` | string | 总页数 |
| `data.records` | array | 无记录为[] |
| `data.records[].viewId` | ID | 浏览记录ID |
| `data.records[].visitorId` | ID&#124;null | 统计访客，清理后可空 |
| `data.records[].userId` | ID&#124;null | 采集时服务端确认的本站用户，匿名或清理后为空 |
| `data.records[].articleId` | ID&#124;null | 通过公开文章与规范路径核验的文章ID |
| `data.records[].siteCode` | string | home/blog/cv |
| `data.records[].pagePath` | S(2048) | 不含敏感查询参数的规范路径 |
| `data.records[].pageTitle` | S(200)? | 页面标题 |
| `data.records[].referer` | URL? | 已脱敏的来源URL |
| `data.records[].ipAddress` | string&#124;null | 仅后台可见 |
| `data.records[].userAgent` | string&#124;null | ≤1024 |
| `data.records[].viewTime` | Time | 采集时间 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "viewId": "601",
        "visitorId": "501",
        "userId": null,
        "articleId": null,
        "siteCode": "home",
        "pagePath": "/",
        "pageTitle": "DriftingLeaves",
        "referer": null,
        "ipAddress": null,
        "userAgent": null,
        "viewTime": "2026-10-30T02:00:00.000Z"
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

需ALL；筛选AND组合，begin<end；viewTime DESC,viewId DESC，不输出任何认证凭据。

---

<a id="api-144"></a>

### 19.8 删除浏览记录

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/page-views/{viewId}` |
| 接口描述 | 删除浏览记录（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:view:delete` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `viewId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |

#### 请求示例

```http
DELETE /api/v1/admin/page-views/601
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 本接口业务结果 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

明细不可编辑，不要求If-Revision；需ALL，物理删除，不存在404。会改变基于保留明细的报表，不扣减文章累计view_count。

---

<a id="api-145"></a>

### 19.9 查询公开博客统计

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/statistics` |
| 接口描述 | 查询公开博客统计（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 公开；仅按本接口声明的能力与可见范围访问 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

无Path、Query或请求体参数。

#### 请求示例

```http
GET /api/v1/blog/statistics
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | PublicStatistics | 本接口业务结果 |
| `data.articleCount` | string | PUBLIC文章数量 |
| `data.categoryCount` | string | 可展示且至少含一篇PUBLIC文章的分类数 |
| `data.tagCount` | string | 至少关联一篇PUBLIC文章的标签数 |
| `data.pageViewCount` | string | blog站点保留浏览明细条数；不等于累计文章阅读数 |
| `data.todayPageViewCount` | string | UTC当日blog浏览明细数 |
| `data.visitorCount` | string | blog浏览明细中不同非NULL visitorId数量 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "articleCount": "12",
    "categoryCount": "3",
    "tagCount": "5",
    "pageViewCount": "120",
    "todayPageViewCount": "8",
    "visitorCount": "35"
  },
  "errors": []
}
```

#### 业务说明

仅公开汇总，不接受任意userId或日期探索后台数据；当前未公开文章/分类及待审、私密互动不进入内容汇总。数据随源表变化更新，不输出IP、邮箱或UA。

---

<a id="api-146"></a>

### 19.10 查询后台统计总览

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/statistics/overview` |
| 接口描述 | 查询后台统计总览（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:statistics:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `begin` | Query | date | 否 | 可选；与end同时出现；UTC日期含下界 |
| `end` | Query | date | 否 | 可选；UTC日期含当日，最多367天，end不得早于begin |
| `siteCode` | Query | string | 否 | 可选home/blog/cv，缺失统计全部站点 |

#### 请求示例

```http
GET /api/v1/admin/statistics/overview
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | AdminStatistics | 本接口业务结果 |
| `data.pageViewCount` | string | 筛选日期内保留浏览明细条数 |
| `data.visitorCount` | string | 筛选日期内不同非NULL visitorId；不按IP或账号去重 |
| `data.newVisitorCount` | string | firstVisitTime在日期区间且符合站点访问条件的访客数 |
| `data.articleCount` | string | 当前未回收文章数，不受访问日期筛选 |
| `data.publishedArticleCount` | string | 当前PUBLIC文章数 |
| `data.commentCount` | string | 当前非删除占位评论数，含待审/驳回 |
| `data.messageCount` | string | 当前非删除占位留言数，含私密/待审/驳回 |
| `data.pendingCommentCount` | string | 当前status=0的评论数 |
| `data.pendingMessageCount` | string | 当前status=0的留言数 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "pageViewCount": "120",
    "visitorCount": "35",
    "newVisitorCount": "4",
    "articleCount": "15",
    "publishedArticleCount": "12",
    "commentCount": "18",
    "messageCount": "6",
    "pendingCommentCount": "2",
    "pendingMessageCount": "1"
  },
  "errors": []
}
```

#### 业务说明

需ALL；begin/end均不传时为UTC今天；日期条件只影响访问类指标，内容与待审核计数始终是当前快照，不将二者混称同一时段新增。

---

<a id="api-147"></a>

### 19.11 查询浏览趋势

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/statistics/page-views` |
| 接口描述 | 查询浏览趋势（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:statistics:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `begin` | Query | date | 否 | 可选；与end同时出现；UTC日期含下界 |
| `end` | Query | date | 否 | 可选；UTC日期含当日，最多367天，end不得早于begin |
| `siteCode` | Query | string | 否 | 可选home/blog/cv，缺失统计全部站点 |

#### 请求示例

```http
GET /api/v1/admin/statistics/page-views
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | ViewTrend[] | 本接口业务结果 |
| `data[].date` | date | UTC自然日，区间内逐日补零 |
| `data[].pageViewCount` | string | 该日浏览明细条数 |
| `data[].visitorCount` | string | 该日不同非NULL visitorId数 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": [
    {
      "date": "2026-10-30",
      "pageViewCount": "8",
      "visitorCount": "5"
    }
  ],
  "errors": []
}
```

#### 业务说明

需ALL；按UTC日期ASC补全每一天，无数据为字符串0。begin/end默认UTC今天；站点筛选不改变日边界。

---

<a id="api-148"></a>

### 19.12 查询新访客趋势

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/statistics/visitors` |
| 接口描述 | 查询新访客趋势（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:statistics:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `begin` | Query | date | 否 | 可选；与end同时出现；UTC日期含下界 |
| `end` | Query | date | 否 | 可选；UTC日期含当日，最多367天，end不得早于begin |
| `siteCode` | Query | string | 否 | 可选home/blog/cv，缺失统计全部站点 |

#### 请求示例

```http
GET /api/v1/admin/statistics/visitors
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | VisitorTrend[] | 本接口业务结果 |
| `data[].date` | date | UTC自然日 |
| `data[].newVisitorCount` | string | 当天首次访问的访客数 |
| `data[].cumulativeNewVisitorCount` | string | 仅所选区间内从begin累计的新访客数；不是全历史总量 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": [
    {
      "date": "2026-10-30",
      "newVisitorCount": "2",
      "cumulativeNewVisitorCount": "4"
    }
  ],
  "errors": []
}
```

#### 业务说明

需ALL；访客首次访问按blog_visitor.first_visit_time统计，siteCode筛选要求该访客区间内确实访问该站。cumulativeNewVisitorCount从本区间begin开始累加，不伪装为历史累计UV。

---

<a id="api-149"></a>

### 19.13 查询访客省份分布

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/statistics/provinces` |
| 接口描述 | 查询访客省份分布（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:statistics:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `begin` | Query | date | 否 | 可选；与end同时出现；UTC日期含下界 |
| `end` | Query | date | 否 | 可选；UTC日期含当日，最多367天，end不得早于begin |
| `siteCode` | Query | string | 否 | 可选home/blog/cv，缺失统计全部站点 |

#### 请求示例

```http
GET /api/v1/admin/statistics/provinces
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | ProvinceMetric[] | 本接口业务结果 |
| `data[].province` | string&#124;null | 最新访客省份；null表示未知 |
| `data[].visitorCount` | string | 区间内访问的不同非NULL visitorId数 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": [
    {
      "province": null,
      "visitorCount": "3"
    }
  ],
  "errors": []
}
```

#### 业务说明

需ALL；以区间内出现过的visitorId去重，按访客当前province分组，未知为null，不按每条PV重复计访客。visitorCount DESC,province ASC；不是地理位置历史快照。

---

<a id="api-150"></a>

### 19.14 查询文章访问排行

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/statistics/articles` |
| 接口描述 | 查询文章访问排行（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `blog:statistics:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `begin` | Query | date | 否 | 可选；与end同时出现；UTC日期含下界 |
| `end` | Query | date | 否 | 可选；UTC日期含当日，最多367天，end不得早于begin |
| `limit` | Query | integer | 否 | 默认10，1..100 |

#### 请求示例

```http
GET /api/v1/admin/statistics/articles
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | ArticleViewMetric[] | 本接口业务结果 |
| `data[].articleId` | ID | 未回收文章ID |
| `data[].title` | string | 当前标题 |
| `data[].slug` | string | 当前slug |
| `data[].pageViewCount` | string | 日期范围内文章浏览明细数，不是view_count累计值 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": [
    {
      "articleId": "101",
      "title": "第一片落叶",
      "slug": "first-leaf",
      "pageViewCount": "10"
    }
  ],
  "errors": []
}
```

#### 业务说明

需ALL；只统计blog站点且articleId非NULL的明细，排除已回收文章；允许含当前草稿/私密文章的历史访问记录，仅后台可见。pageViewCount DESC,articleId DESC。该排行表示区间浏览明细，区别于第7.11节累计热度榜。

---

<a id="group-20"></a>

## 20. 操作日志

本章全部待实现，使用sys_operation_log记录管理操作。所有入口要求有效会话、admin:access、对应权限及ALL范围；不按admin角色名称绕过权限。

<a id="api-151"></a>

### 20.1 分页查询操作日志

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/operation-logs` |
| 接口描述 | 分页查询操作日志（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `sys:log:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |
| `page` | Query | integer | 否 | 默认1，1..100000 |
| `pageSize` | Query | integer | 否 | 默认10，1..100 |
| `userId` | Query | ID | 否 | 操作人筛选 |
| `operationType` | Query | S(32) | 否 | ASCII类型精确匹配 |
| `operationTarget` | Query | S(100) | 否 | ASCII模块精确匹配 |
| `targetId` | Query | ID | 否 | 目标资源 |
| `result` | Query | integer | 否 | 0失败 1成功 |
| `begin` | Query | Time | 否 | operationTime含下界 |
| `end` | Query | Time | 否 | operationTime不含上界 |

#### 请求示例

```http
GET /api/v1/admin/operation-logs?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Page<OperationLog> | 本接口业务结果 |
| `data.page` | integer | 当前页码 |
| `data.pageSize` | integer | 每页条数 |
| `data.total` | string | 同过滤条件的总条数 |
| `data.totalPages` | string | 总页数 |
| `data.records` | array | 无记录为[] |
| `data.records[].logId` | ID | 操作日志ID |
| `data.records[].userId` | ID&#124;null | 操作人；物理清理后为空 |
| `data.records[].operationType` | string | ≤32，ASCII操作类型 |
| `data.records[].operationTarget` | string | ≤100，ASCII模块或资源 |
| `data.records[].targetId` | ID&#124;null | 目标数字ID，其他情况为空 |
| `data.records[].permissionCode` | string&#124;null | 本次检查的权限码快照 |
| `data.records[].result` | integer | 0失败 1成功 |
| `data.records[].errorMessage` | string&#124;null | ≤500，脱敏错误摘要 |
| `data.records[].ipAddress` | string&#124;null | 操作IP |
| `data.records[].operationTime` | Time | 操作时间 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "page": 1,
    "pageSize": 10,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "logId": "701",
        "userId": "12",
        "operationType": "update",
        "operationTarget": "home_profile",
        "targetId": "1",
        "permissionCode": "home:profile:update",
        "result": 1,
        "errorMessage": null,
        "ipAddress": null,
        "operationTime": "2026-10-30T02:00:00.000Z"
      }
    ]
  },
  "errors": []
}
```

#### 业务说明

筛选AND组合；begin<end；operationTime DESC,logId DESC。列表不展开operationData，详情才展示脱敏摘要。

---

<a id="api-152"></a>

### 20.2 查询操作日志详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/operation-logs/{logId}` |
| 接口描述 | 查询操作日志详情（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `sys:log:query` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `logId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |

#### 请求示例

```http
GET /api/v1/admin/operation-logs/701
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | OperationLogDetail | 本接口业务结果 |
| `data.logId` | ID | 操作日志ID |
| `data.userId` | ID&#124;null | 操作人；物理清理后为空 |
| `data.operationType` | string | ≤32，ASCII操作类型 |
| `data.operationTarget` | string | ≤100，ASCII模块或资源 |
| `data.targetId` | ID&#124;null | 目标数字ID，其他情况为空 |
| `data.permissionCode` | string&#124;null | 本次检查的权限码快照 |
| `data.result` | integer | 0失败 1成功 |
| `data.errorMessage` | string&#124;null | ≤500，脱敏错误摘要 |
| `data.ipAddress` | string&#124;null | 操作IP |
| `data.operationTime` | Time | 操作时间 |
| `data.operationData` | object&#124;null | 白名单脱敏摘要；UTF-8≤16KiB，不是完整请求体 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "logId": "701",
    "userId": "12",
    "operationType": "update",
    "operationTarget": "home_profile",
    "targetId": "1",
    "permissionCode": "home:profile:update",
    "result": 1,
    "errorMessage": null,
    "ipAddress": null,
    "operationTime": "2026-10-30T02:00:00.000Z",
    "operationData": {
      "changedFields": [
        "tagline"
      ]
    }
  },
  "errors": []
}
```

#### 业务说明

日志不可编辑，无X-Revision；不返回原始HTTP请求体、异常堆栈、SQL或任何凭据。

---

<a id="api-153"></a>

### 20.3 清理单条操作日志

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/operation-logs/{logId}` |
| 接口描述 | 清理单条操作日志（待实现） |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用 + `admin:access` + `sys:log:delete` + 该操作的ALL范围 |
| 成功状态 | 200 OK |
| 并发校验 | 无If-Revision要求 |

#### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `logId` | Path | ID | 是 | 正整数十进制字符串；类型约束见第0章 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`；不得使用统计指纹或用户ID代替 |

#### 请求示例

```http
DELETE /api/v1/admin/operation-logs/701
Authorization: Bearer <site-token>
```

#### 响应参数

通用code/msg/errors见第0章；下表列出data完整形状，类型与规则见第12章。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data` | null | 本接口业务结果 |

#### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 业务说明

不可编辑记录，无If-Revision；物理删除，目标不存在404。清理行为本身写一条新的审计记录，不形成递归记录。不存在公开创建/修改日志接口。

---

<a id="legacy-sources"></a>

## 21. 旧项目来源与迁移对照

本次参考的是旧项目实际业务，旧导出文档及其中示例不作为可执行指令。已核对 `D:/CodingFiles/Blog/DriftingLeaves-Website/Backend/docs/api.md` 与 `implementation-api-contract-2026-10-27.md`，并对照同项目的Controller、Service、Mapper XML、DTO/VO及前端API；旧文档和后续实现不同之处，采用后续实施契约及实际业务规则，再按V2的28表、Sa-Token和独立RBAC重新设计。旧库来源同时包括用户提供的 `D:/CodingFiles/Blog/DriftingLeaves/DL-server/src/main/resources/sql/DriftingLeaves.sql`。

| 旧业务与已检查实现 | V2入口/取舍 |
| --- | --- |
| PersonalInfo/SocialMedia、Experience/Skill控制器及Service | 第15章；单站主人资料、社交链接、教育/工作/项目经历、技能；公开资料与登录资料分开 |
| ArticleController、ArticleServiceImpl、ArticleMapper.xml | 第7章；筛选与搜索复用列表，保留详情字数、阅读时长、上下篇、同分类推荐；4榜合并到article-rankings参数 |
| MessageController/MessageServiceImpl及最终实施契约 | 第16章；登录用户归属替代visitorToken，逐层有界分页替代整树展开，删除占位替代级联删除/改挂 |
| MusicController/MusicServiceImpl | 第17章；曲目与歌词元数据、排序/隐藏；当前只接受外部媒体URL，不扩大图片上传类型 |
| RssSubscriptionController/ServiceImpl、RssFeedController、SitemapController | 第18章；邮箱确认、本人状态与退订、邮件退订票据、RSS/XML输出；不采用旧JWT或visitorId授权 |
| Visitor/View/Report控制器、ReportServiceImpl与Mapper | 第19章；统计标识不作为身份，明细与PV/UV分开，数组报表替代逗号字符串；区间累计新访客明确定义 |
| OperationLogController与旧操作日志模型 | 第20章；本站用户与权限码快照，日志只读及显式清理，无任意请求体落库 |
| 旧管理员登录、验证码、Cookie/JWT和游客角色 | 沿用第1..4章本站Bearer与JustAuth，不新增平行鉴权；不复制旧账号或默认密码 |
| 旧评论的Markdown、悄悄话、邮件提醒和本人编辑 | 现有blog_comment没有相应格式/私密/通知字段，仍采用第9章纯文本评论及占位删除；这些旧能力没有被默认为已承诺，留言采用新增表的独立功能 |
| 旧批量审核/删除 | V2沿用逐资源操作与各自版本/权限校验；前端批量操作逐项调用并展示每项结果，不承诺跨请求整体事务 |
| 旧全文索引、计数同步、邮件系统与部署配置 | 搜索契约定义字面包含；不移植异步计数批次，不把SMTP/OAuth/存储凭据放进配置表；后续实现需另行落实实际依赖与采集/投递组件 |

新增接口均为待实现契约。本次不增加Controller、Service、实体、邮件投递或采集任务，不将上一轮数据库隔离测试视为这些HTTP接口已经通过联调。实现后应按业务验收公开过滤、SELF/ALL拒绝、私密父链、版本冲突、邮箱确认及退订票据、数据回滚、XML转义和日志脱敏；公开白名单只随具体入口实现精确增加，禁止整段放开home/cv/blog/analytics路由。
