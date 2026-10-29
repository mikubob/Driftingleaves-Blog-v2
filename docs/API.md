# DriftingLeavesV2 总接口文档

> **文档说明**：依据 V2 当前 POM、18 表 SQL 和业务设计编写，采用参考 assessment API 文档的逐接口格式。编写日期：2026-10-30（Asia/Shanghai）。当前已实现第 1、2 章的 15 个身份/本人安全接口和基础设施；其余章节仍为待实现契约。隔离测试不代表真实平台已联调或接口已上线，运行说明见 [后端运行说明](后端运行说明.md)。
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

---

<a id="common"></a>

## 0. 通用协议与鉴权

### 0.1 路径、类型与输入

- 根路径 `/api/v1`。`/auth` 身份流程，`/me` 操作本人，`/blog` 前台，`/admin` 后台。下文基本信息与请求示例均使用含此前缀的完整调用路径。
- HTTPS；JSON 请求/响应 `application/json; charset=UTF-8`，属性 camelCase。设计决定JSON请求体上限8MiB（正文另有解码后1MiB限制）。文件上传为 multipart，文件内容和 OAuth 回调例外见各节。无请求体标为 `—`，不得用 GET 改变业务状态（OAuth 回调和阅读计数按各节约定）。
- `ID` 是十进制字符串，范围 `1..9223372036854775807`，拒绝 JSON 数字、负数、小数和前导零；所有主键、外键和 ID 数组均如此。非 ID 的 BIGINT 计数、字节数也用十进制字符串输出，避免精度丢失；分页统计同样见下表。
- `Time` 是 UTC、毫秒精度的 RFC 3339 字符串，如 `2026-10-29T02:00:00.000Z`。应用、连接统一使用 UTC，DATETIME(3) 按 UTC 存取；前端再转本地时区。请求中的时间仅用于查询，不允许客户端写审计或发布时间。
- 字符串长度按 Unicode 字符计数，密码另按 UTF-8 字节约束。除密码/Markdown 外，文本先去首尾空白；可空字符串的空串归一为 null，非空字段拒绝空白。简介/描述为纯文本。未知 JSON 字段、无效枚举、错误类型、重复 JSON 属性均返回 `400/VALIDATION_ERROR`，禁止把 DTO 直接批量映射到实体。
- `S(n)` 表示长度 1..n 的非空字符串，`S0(n)` 表示长度 0..n；`?` 表示可为 null；`[]` 表示数组。模型表内未标 `?` 的响应字段必有且非 null；列表无数据为 `[]`，不为 null。
- `URL?` 最大 2048 字符；外部 URL 只接受绝对 http/https，不接受 userinfo、控制字符、javascript/data/file 协议。本站文件 URL 仅接受[文件与附件](#group-11)生成的路径。写 URL 不触发服务端抓取。头像/封面只能使用已公开的资源，不能借展示字段公开私密附件。
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

设计决定：保护“最后有效管理员”指至少一个启用用户关联启用的 `admin` 角色（ALL），该角色仍拥有启用的 `admin:access` 和当前 SQL 所有初始 `sys:*` 权限。影响此条件的用户/角色/权限/授权写入，统一先锁定初始化 `sys_role(role_id=1)` 作为串行检查点，再检查事务后的结果；并发不能各自通过检查而合计清空管理入口。初始化 admin/author/member 角色码及所有已由后端使用的权限码不可改名或删除；状态/授权仍受此保护。普通显示名称可编辑。新增字典权限不会自动产生后端能力，也不会自动授予管理员。

### 0.6 请求保护与跨域

Redis 限流采用服务端识别 IP（仅信任明确配置的反向代理）与账号/Token 双维度；登录失败按 IP+归一 username 限制，注册/OAuth 发起/交换也按浏览器+IP，评论/点赞/上传按用户+IP。首期阈值是部署参数，429 使用 Retry-After，不增加验证码系统。日志不得记录密码、Authorization、OAuth code/state/ticket 或第三方 Token。跨域只允许明确前端 origin，预检允许 Authorization/If-Revision，暴露 X-Revision/Location/Retry-After；带 OAuth Cookie 请求允许 credentials，不能使用通配 origin。非白名单业务路由默认拒绝匿名。

初始角色按当前 SQL 显式授权；匿名访客不对应数据库角色：

| 角色码 | 初始能力 |
| --- | --- |
| admin | 全部初始接口权限及后台菜单，ALL；新增权限仍需显式授予 |
| author | 后台入口、本人文章创建/查询/编辑/发布/回收/恢复、分类/标签只读、本人文件管理及前台评论/点赞，SELF；无评论审核或系统授权管理权限 |
| member | 前台评论、删除本人评论、点赞/取消点赞，SELF；无后台入口和菜单 |

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

共 **86 个不重复的 HTTP 方法与路径组合**。关联表不单独暴露 CRUD；未规划用户物理删除、文章永久删除、密码找回、邮箱验证、公开友链申请、独立评论编辑或第三方 Token 托管。首位管理员由受控初始化创建，无公开初始化接口和默认密码。

| 模块 | 接口数 | 方法与完整路径索引 |
| --- | --- | --- |
| 认证与第三方授权 | 7 | [POST /api/v1/auth/register](#api-1)、[POST /api/v1/auth/login](#api-2)、[POST /api/v1/auth/logout](#api-3)、[GET /api/v1/auth/oauth/providers](#api-4)、[POST /api/v1/auth/oauth/{source}/authorize](#api-5)、[GET /api/v1/auth/oauth/{source}/callback](#api-6)、[POST /api/v1/auth/oauth/exchange](#api-7) |
| 本人资料与安全设置 | 8 | [GET /api/v1/me](#api-8)、[PATCH /api/v1/me/profile](#api-9)、[PUT /api/v1/me/password](#api-10)、[DELETE /api/v1/me/password](#api-11)、[GET /api/v1/me/capabilities](#api-12)、[GET /api/v1/me/menus](#api-13)、[GET /api/v1/me/oauth-bindings](#api-14)、[DELETE /api/v1/me/oauth-bindings/{source}](#api-15) |
| 用户管理 | 5 | [GET /api/v1/admin/users](#api-16)、[GET /api/v1/admin/users/{userId}](#api-17)、[POST /api/v1/admin/users](#api-18)、[PUT /api/v1/admin/users/{userId}](#api-19)、[PUT /api/v1/admin/users/{userId}/roles](#api-20) |
| 角色与授权管理 | 6 | [GET /api/v1/admin/roles](#api-21)、[GET /api/v1/admin/roles/{roleId}](#api-22)、[POST /api/v1/admin/roles](#api-23)、[PUT /api/v1/admin/roles/{roleId}](#api-24)、[DELETE /api/v1/admin/roles/{roleId}](#api-25)、[PUT /api/v1/admin/roles/{roleId}/grants](#api-26) |
| 权限字典管理 | 5 | [GET /api/v1/admin/permissions](#api-27)、[GET /api/v1/admin/permissions/{permissionId}](#api-28)、[POST /api/v1/admin/permissions](#api-29)、[PUT /api/v1/admin/permissions/{permissionId}](#api-30)、[DELETE /api/v1/admin/permissions/{permissionId}](#api-31) |
| 菜单管理 | 5 | [GET /api/v1/admin/menus](#api-32)、[GET /api/v1/admin/menus/{menuId}](#api-33)、[POST /api/v1/admin/menus](#api-34)、[PUT /api/v1/admin/menus/{menuId}](#api-35)、[DELETE /api/v1/admin/menus/{menuId}](#api-36) |
| 文章与归档 | 10 | [GET /api/v1/blog/articles](#api-37)、[GET /api/v1/blog/articles/{slug}](#api-38)、[GET /api/v1/blog/archives](#api-39)、[GET /api/v1/admin/articles](#api-40)、[GET /api/v1/admin/articles/{articleId}](#api-41)、[POST /api/v1/admin/articles](#api-42)、[PUT /api/v1/admin/articles/{articleId}](#api-43)、[PUT /api/v1/admin/articles/{articleId}/status](#api-44)、[DELETE /api/v1/admin/articles/{articleId}](#api-45)、[POST /api/v1/admin/articles/{articleId}/restore](#api-46) |
| 分类与标签 | 12 | [GET /api/v1/blog/categories](#api-47)、[GET /api/v1/admin/categories](#api-48)、[GET /api/v1/admin/categories/{categoryId}](#api-49)、[POST /api/v1/admin/categories](#api-50)、[PUT /api/v1/admin/categories/{categoryId}](#api-51)、[DELETE /api/v1/admin/categories/{categoryId}](#api-52)、[GET /api/v1/blog/tags](#api-53)、[GET /api/v1/admin/tags](#api-54)、[GET /api/v1/admin/tags/{tagId}](#api-55)、[POST /api/v1/admin/tags](#api-56)、[PUT /api/v1/admin/tags/{tagId}](#api-57)、[DELETE /api/v1/admin/tags/{tagId}](#api-58) |
| 评论审核与点赞 | 11 | [GET /api/v1/blog/articles/{articleId}/comments](#api-59)、[POST /api/v1/blog/articles/{articleId}/comments](#api-60)、[GET /api/v1/me/comments](#api-61)、[DELETE /api/v1/me/comments/{commentId}](#api-62)、[GET /api/v1/admin/comments](#api-63)、[GET /api/v1/admin/comments/{commentId}](#api-64)、[PUT /api/v1/admin/comments/{commentId}/review](#api-65)、[DELETE /api/v1/admin/comments/{commentId}](#api-66)、[GET /api/v1/blog/articles/{articleId}/like](#api-67)、[PUT /api/v1/blog/articles/{articleId}/like](#api-68)、[DELETE /api/v1/blog/articles/{articleId}/like](#api-69) |
| 友链与站点配置 | 10 | [GET /api/v1/blog/links](#api-70)、[GET /api/v1/admin/links](#api-71)、[GET /api/v1/admin/links/{linkId}](#api-72)、[POST /api/v1/admin/links](#api-73)、[PUT /api/v1/admin/links/{linkId}](#api-74)、[DELETE /api/v1/admin/links/{linkId}](#api-75)、[GET /api/v1/blog/configs](#api-76)、[GET /api/v1/admin/configs](#api-77)、[GET /api/v1/admin/configs/{configKey}](#api-78)、[PUT /api/v1/admin/configs/{configKey}](#api-79) |
| 文件与附件 | 7 | [GET /api/v1/admin/files](#api-80)、[GET /api/v1/admin/files/{fileId}](#api-81)、[POST /api/v1/admin/files](#api-82)、[GET /api/v1/admin/files/{fileId}/content](#api-83)、[DELETE /api/v1/admin/files/{fileId}](#api-84)、[GET /api/v1/blog/articles/{articleId}/files/{fileId}/content](#api-85)、[GET /api/v1/admin/articles/{articleId}/files/{fileId}/content](#api-86) |

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

平台顶层 GET 回调不携带本站 Bearer。后端凭 state、浏览器 Cookie 和 Redis 流程定位操作；BIND 再校验服务端保存的原会话仍有效且属于目标用户。可信流程成功后 303 到固定完成页，不在回调直接向浏览器发本站 Token。

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

至少一项；缺失保持，null 清空可空字段。username、status、角色、权限不可改。

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
      "blog:comment:deleteOwn"
    ],
    "dataScopes": {
      "blog:article:like": "SELF",
      "blog:comment:create": "SELF",
      "blog:comment:deleteOwn": "SELF"
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

source 升序，仅返回平台和绑定时间，不暴露 uuid、绑定记录 ID 或第三方 Token。

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

按 createTime DESC,userId DESC；全局操作。

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

sys:user:query

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

整体替换，不接受隐藏的增量追加；If-Revision 来自该用户详情，受最后管理员保护。

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

按 roleId ASC。

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

roleCode 不可改；修改影响授权时撤销受影响会话。

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

内置 admin/author/member 禁止删除；自定义角色删除清理关联并撤销受影响授权。

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

两类授权同事务完整替换；menuIds 补齐祖先，页面/按钮所需权限必须显式列入 permissionIds，不静默扩大授权。

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
| `data.records[].permissionId` | ID | 权限 ID；目录节点为 null |
| `data.records[].permissionCode` | string | 稳定接口权限码，目录节点为 null |
| `data.records[].permissionName` | string | 权限显示名 |
| `data.records[].module` | string | 权限模块 |
| `data.records[].description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
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

按 permissionId ASC。

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
| `data.permissionId` | ID | 权限 ID；目录节点为 null |
| `data.permissionCode` | string | 稳定接口权限码，目录节点为 null |
| `data.permissionName` | string | 权限显示名 |
| `data.module` | string | 权限模块 |
| `data.description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
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
| `data.permissionId` | ID | 权限 ID；目录节点为 null |
| `data.permissionCode` | string | 稳定接口权限码，目录节点为 null |
| `data.permissionName` | string | 权限显示名 |
| `data.module` | string | 权限模块 |
| `data.description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
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
| `data.permissionId` | ID | 权限 ID；目录节点为 null |
| `data.permissionCode` | string | 稳定接口权限码，目录节点为 null |
| `data.permissionName` | string | 权限显示名 |
| `data.module` | string | 权限模块 |
| `data.description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
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

permissionCode 不可改；停用需撤销受影响授权。

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

固定后端权限禁止删除；菜单引用时 409/RESOURCE_IN_USE，不能静默移除门禁。

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
| 并发校验 | 无 If-Revision 要求 |

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

后台(sys:menu:query)

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

验证父类型、组件白名单及页面/按钮权限；后端菜单 path 不作为 API 鉴权规则。

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

检查自引用、移入后代、类型变化及后代约束，禁止并发移动产生环；menuCode 不可改。

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

有子节点 409；叶子删除级联 role_menu，独立接口权限不因此撤销。

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

全部过滤 AND 组合；isTop DESC,publishTime DESC,articleId DESC。keyword 仅标题/摘要字面包含，隐藏分类不暴露。

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
    "htmlContent": "<h1 id=\"第一片落叶\">第一片落叶</h1>\n<p>你好。</p>"
  },
  "errors": []
}
```

#### 业务说明

每次成功原子增加 view_count；失败不计数，这是阅读次数而非独立访客。仅输出清理后的 HTML。

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
| `data[].configKey` | string | site 或 comment |
| `data[].configValue` | object | 严格白名单配置对象，具体形状见配置模型 |
| `data[].configValue.title` | string | 文章或站点标题 |
| `data[].configValue.description` | string | 说明，可 null；site 内 description 可为空串 |
| `data[].configValue.icp` | string | 备案文本，可空串 |
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
        "icp": ""
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
| `data[].configKey` | string | site 或 comment |
| `data[].configValue` | object | 严格白名单配置对象，具体形状见配置模型 |
| `data[].configValue.title` | string | 文章或站点标题 |
| `data[].configValue.description` | string | 说明，可 null；site 内 description 可为空串 |
| `data[].configValue.icp` | string | 备案文本，可空串 |
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
        "icp": ""
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

只支持 site/comment，configKey ASC；不提供任意建键接口。

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
| `configKey` | Path | string | 是 | 仅 site/comment，未知键 404 |
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
| `data.configKey` | string | site 或 comment |
| `data.configValue` | object | 严格白名单配置对象，具体形状见配置模型 |
| `data.configValue.title` | string | 文章或站点标题 |
| `data.configValue.description` | string | 说明，可 null；site 内 description 可为空串 |
| `data.configValue.icp` | string | 备案文本，可空串 |
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
      "icp": ""
    },
    "description": "站点信息",
    "isPublic": 1,
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

site/comment；未知键 404。

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
| `configKey` | Path | string | 是 | 仅 site/comment，未知键 404 |
| `Authorization` | Header | string | 是 | `Bearer <site-token>`，本站 Sa-Token 会话 |
| `If-Revision` | Header | string | 是 | 原样携带详情返回的带引号 X-Revision；缺失 428、不匹配 412 |
| `configValue` | JSON Body | object | 是 | site: title/description/icp；comment: enabled/reviewRequired；完整白名单对象，UTF-8 ≤16KiB |
| `description` | JSON Body | string&#124;null | 是，可 null | ≤255 字符 |
| `isPublic` | JSON Body | integer | 是 | site 为 0/1；comment 必须 0 |

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
    "icp": ""
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
| `data.configKey` | string | site 或 comment |
| `data.configValue` | object | 严格白名单配置对象，具体形状见配置模型 |
| `data.configValue.title` | string | 文章或站点标题 |
| `data.configValue.description` | string | 说明，可 null；site 内 description 可为空串 |
| `data.configValue.icp` | string | 备案文本，可空串 |
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
      "icp": ""
    },
    "description": "站点信息",
    "isPublic": 1,
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

#### 业务说明

完整替换值；site={title,description,icp}，comment={enabled,reviewRequired} 且 isPublic 必须 0；未知内外字段 400。

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
| ArticlePublicDetail | ArticlePublic + htmlContent:string；不返回Markdown源、作者邮箱、后台状态或deletedAt |
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
| ConfigWrite | configValue:object、description:S(255)?、isPublic:0或1，均必填；仅支持下表两种键及字段，不接受任意JSON结构 |

友链url无唯一键，不虚构“相同URL必定409”的规则。URL仅保存/展示，不由服务器探测抓取。链接名不唯一。配置JSON为对象且最多16KiB（UTF-8）；属性外层和内部均拒绝未知字段，防止借config_value扩展无依据的系统。

| configKey | configValue完整形状 | 公开规则 |
| --- | --- | --- |
| site | `{title:S(100),description:S0(500),icp:S0(100)}`，三项必填 | isPublic可为0/1；初始为1 |
| comment | `{enabled:boolean,reviewRequired:boolean}`，两项必填 | isPublic必须0；初始true/true，不向前端公开审核策略 |

不存数据库、Redis、SMTP、第三方App Secret或对象存储凭据；未知键404。不提供任意新增/删除配置键接口。comment.enabled关闭后保留历史内容。前台通过文章allowComment及新增失败反馈交互；服务端仍检查站点开关，不依赖前端按钮是否显示。

### 12.8 文件模型与读取响应

blog_file没有公开标志或文章外键，不能仅凭fileId认为可匿名访问。首期固定本地存储，storageType=local、bucket=""；无OSS接入或多存储切换API。

| 模型 | 字段与约束 |
| --- | --- |
| File | fileId/uploaderId:ID、originalName:string（1..255）、mimeType:string（≤100）、sizeBytes:string、storageType:"local"、createTime:Time、previewUrl:string（后台内容接口，非公开直链） |
| Upload | multipart只有file，单个、非空；不接uploaderId、文件路径、bucket、objectKey、isPublic |

设计决定：首期只允许PNG/JPEG图片，单文件≤10MiB、单次multipart总大小≤11MiB；扩展名白名单`.png/.jpg/.jpeg`必须与服务端内容识别一致，不信Content-Type。使用JDK ImageIO先读尺寸再解码验证真实图片，拒绝不合法结构/异常尺寸（宽高各≤10000、像素总数≤2500万）。不支持WebP/PDF/SVG/HTML/脚本/压缩包，当前POM未配WebP编解码或PDF解析器，不预装额外依赖。图片不承诺压缩转码；以后有其他附件需求再明确类型及校验契约。

原文件名仅展示，移除路径分隔符/控制字符，结果空或>255报400；服务端生成ASCII随机object_key（≤512），normalize后的绝对路径必须仍位于上传根目录内，防目录穿越和符号链接越界。存储根目录不能被Nginx/静态资源映射公开。File模型不返回bucket/objectKey/磁盘路径。

成功内容响应为原始文件字节，非Result：`Content-Type`取服务端确认的image/png或image/jpeg、`Content-Length`取实际字节数、`X-Content-Type-Options:nosniff`、`Content-Disposition:inline; filename*=UTF-8''...`安全编码文件名。首期不支持Range/分片/断点上传，忽略Range返回完整200；文件响应`Cache-Control:no-store`，避免下架/私密后缓存绕过。失败仍返回对应HTTP的JSON Result。前端携带Bearer fetch后台内容并用临时object URL预览，不能把Token拼接进img URL。

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

keyword 首期仅标题/摘要字面包含，不检索 LONGTEXT；参数化查询并转义 LIKE 的 `%/_/反斜线`，不把用户输入当通配符或SQL。后台排序固定；不复刻旧全文/热门榜系统。month 必须为合法年/月，年份1000..9999并转换UTC半开区间；9999-12只设下界，避免生成超出DATETIME范围的上界；不接受任意日期SQL。前台按隐藏/不存在分类筛选返回空页，按不存在标签筛选返回空页。

<a id="file-consistency"></a>

### 13.4 文件引用、访问控制与存储一致性

私密/草稿/下架/回收站附件不得通过前台路径读取，即使持有Token也404。通过后台文章上下文读取时同时满足文章和文件权限范围；只允许文章作者身份不等于已获跨用户文件权限。上传者本人的后台原文件预览与文章公开状态无关。文件表没有独立可变资源，删除不要求If-Revision，但必须执行[文件存储一致性](#file-consistency)引用检查及锁。

设计决定：本站附件引用仅支持Markdown链接/图片和文章coverUrl，规范路径为 `/api/v1/blog/articles/{articleId}/files/{fileId}/content`；必须是当前文章articleId，不能指向另一篇文章的上下文。创建草稿后取得articleId，再上传/编辑引用；POST新文章不能附带未知articleId的本站文件URL。后端解析Markdown语法树（含清理后保留的HTML链接/图片）和coverUrl确认引用集合，不以文本随意包含数字作为引用证据。相同路径的绝对本站URL归一成规范相对路径；其余本站文件路径/编码绕过/存储直链拒绝，外部URL不映射为内部文件。

文章写入时，每个引用必须存在，写作者须有`blog:file:query`及对应文件范围；不能通过引用他人私密文件让其公开。发布时再检查所有引用可用。前台HTML保留上述公开上下文；后台详情HTML将这些路径改为相应`/admin/articles/.../content`，前端需Bearer fetch后替换临时object URL，原始Markdown不改写。一个文件被多篇文章引用时，只要某篇PUBLIC且确实引用，就可从该篇上下文公开读取；**需要私密隔离时必须另传一份文件，不能在公开文章复用同一fileId**。修改/撤销引用或下架后立即按当前状态鉴权。

为保持18表内的最小可执行方案，头像、友链图标和配置首期不接受本站附件API路径（包括等价绝对URL），仅用外部http(s)资源；不新增文件用途/公开状态字段。不能把私密附件URL粘贴进头像或公开配置实现匿名访问。

先写临时文件并完成校验，再原子移动为随机最终存储键，最后插入元数据并提交；任一步失败不得返回File成功结果，数据库写失败时删除刚写文件，补偿失败记录运维错误以清理孤立对象。没有成功元数据的存储对象永不通过API读取。

删除在持有文件行锁时查所有文章（含私密、草稿、回收站）的Markdown/cover引用，仍有引用返回409。扫描应按批次解析现有18表，不增文件引用表；数据量增长导致扫描不可接受时再单独演进引用模型，不能只扫描公开HTML缓存。所有文章创建/编辑/删除引用同时锁定涉及文件行，保证引用新增和文件删除互斥；多文件按fileId升序，先锁文件再锁文章，复读文章并校验X-Revision。实现不得只“先查一次再删”而允许并发插入引用。

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
| blog_site_config | site/comment 白名单对象；公开接口只返回允许公开项 |

<a id="errors"></a>

## 14. 错误码与实施边界

### 14.1 HTTP 与业务错误

| HTTP | code | 适用场景 |
| --- | --- | --- |
| 200 | OK | 查询、更新、删除、幂等重放成功；均有 Result |
| 201 | OK | 创建新业务记录（含注册、文章、评论、上传）；返回模型；后台创建设置对应详情Location，注册/评论通过响应ID及本人接口定位，不设置Location |
| 400 | VALIDATION_ERROR | 字段、枚举、分页、查询组合或 JSON 非法 |
| 400 | OAUTH_STATE_INVALID | state/浏览器/操作不匹配、过期或已消费；不泄露哪一项失败 |
| 400 | OAUTH_DENIED | 用户在平台取消授权 |
| 401 | UNAUTHENTICATED | Token 缺失、失效、过期或被注销 |
| 401 | AUTH_FAILED | 密码登录名不存在、密码错误或无本站密码，统一文案“账号或密码错误” |
| 403 | ACCOUNT_DISABLED | 已确认凭据身份后发现本站账号禁用 |
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

第 1、2 章已实现，并通过真实隔离 MySQL/Redis 与 MVC 链验证；平台网络使用边界替身，尚未进行真实平台 App 回调及浏览器弹窗联调。第 3..11 章不提供成功空壳，最后管理员及资源 SQL 授权仍随对应业务实现。配置、命令和测试边界见 [后端运行说明](后端运行说明.md)。
