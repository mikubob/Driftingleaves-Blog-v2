# DriftingLeavesV2 接口文档

接口先按 Admin、Blog、Home、Cv 四个端划分，再在当前端内按业务细分。更新日期：2026-11-01。

统一根路径为 `/api/v1`，紧接根路径的第一个业务前缀必须是所属端名。当前共 **192 个 HTTP 路由、3 个 WebSocket 入口**，包含各端复用同一业务能力的独立路径。

2026-11-01 将 26 个单条管理写入口合并为[26 个统一操作入口](#admin-batch)，每次支持 1..100 个显式目标；单条操作也提交一项数组，不再提供重复的单条写路由。另增用户批量启用、禁用、删除 3 项及前端未知路由上报 2 项，当前 192 个 HTTP 路由（Admin 123 个），详情、创建、编辑及本人接口保留。旧客户端须与后端同步更新。

| 端 | 完整前缀 | 用途 | HTTP 路由数 |
| --- | --- | --- | --- |
| [Admin 管理端](#client-admin) | `/api/v1/admin` | 登录、本人设置与后台管理 | 123 |
| [Blog 博客端](#client-blog) | `/api/v1/blog` | 登录、本人互动、公开博客与统计 | 57 |
| [Home 主页端](#client-home) | `/api/v1/home` | 主人资料、社交、配置与统计 | 5 |
| [Cv 简历端](#client-cv) | `/api/v1/cv` | 主人资料、社交、简历、配置与统计 | 7 |

[完整接口索引](API_INDEX.md) · [通用协议](#common) · [响应模型](#models) · [错误码](#errors) · [开发约定](API_CONTRACT.md)

### 路径迁移与共享能力

- 原 `/auth/**` 和 `/me/**` 分别迁到 `/admin/auth/**`、`/admin/me/**` 及 `/blog/auth/**`、`/blog/me/**`。本人评论、留言、点赞记录和邮件订阅属于 Blog。
- 原 `/analytics/**`、`/ws/online` 迁到 Home、Blog、Cv 各自前缀下；`siteCode` 必须与当前端一致，WebSocket 票据也必须在同端使用。Admin 不参与在线人数统计。
- Blog、Home、Cv 分别通过自己端的 `/profile`、`/social-links`、`/configs` 读取共享公开数据。更改资料和配置仍使用 Admin 入口。
- 旧的无端名前缀入口已移除。前后端须一起部署；OAuth 平台登记的回调地址须更新到配置选定的 `/api/v1/blog/auth/oauth/{source}/callback` 或 `/api/v1/admin/auth/oauth/{source}/callback`，仍使用既有固定完成页配置。
- 四端路径不建立新的账号、角色或会话体系。Admin 的认证与本人设置维持公开/登录本人规则，后台业务继续要求 `admin:access`、业务权限和数据范围。

<a id="common"></a>

## 通用协议

### 路径与鉴权

- 下文使用完整路径，统一前缀 `/api/v1`。JSON 请求使用 `application/json`，上传使用 `multipart/form-data`。
- 登录后通过 `Authorization: Bearer <accessToken>` 传递本站 Sa-Token。Blog 的密码、邮箱验证码及已绑定第三方登录固定有效 259200 秒（3 天）；Admin 只有账密、绑定邮箱验证码和图形验证码同时通过才签发 604800 秒（7 天）会话。无 refreshToken、无滑动续期，不接受客户端指定有效期。前台及历史会话均不能访问受保护的后台接口，即使该用户拥有后台权限，也必须重新完成后台登录。
- 浏览器以 localStorage 的 `dl-blog-session`、`dl-admin-session` 分别保存 `{accessToken, expiresAt}`，到期时间按登录响应的 expiresIn 换算。刷新或重新打开页面后先检查到期时间，再读取对应端 `/me`、`/me/capabilities` 核验身份和权限；Admin 还读取 `/me/menus`、确认 `admin:access` 后注册动态路由。资料、权限和菜单不持久化，恢复不续期。退出、核验失败或 401/UNAUTHENTICATED 清除本端持久凭据。浏览器禁用存储时仅本页有效；旧 Token 保留原到期时间，重新登录后采用新有效期。
- **公开**：无需登录；**登录本人**：账号启用，身份只取当前会话；**后台：P**：账号启用，同时具备 `admin:access` 与权限 P。未认证 401，无权限 403。
- 文章、文件、评论按本次权限的 ALL/SELF 范围检查；范围外资源返回 404。多个权限分别检查范围。分类、标签、用户、角色等全局资源不产生“本人版本”；标注 ALL 的操作必须具备 ALL 范围。
- 密码变更、清除密码、发起绑定与解绑要求最近 300 秒成功认证。密码变更/清除/解绑、账号禁用及授权变更会撤销相关会话；需要时重新登录。
- 认证、本人资料、后台业务和带身份的响应使用 `Cache-Control: no-store`。OAuth 还需同浏览器 Cookie 与明确 Origin，见 [13.1](#oauth-flow)。

<a id="pagination"></a>

### 0.2 响应与分页

普通接口统一返回 Result；文件流、XML 与 OAuth 回调按各接口说明处理。默认成功 HTTP 200，创建通常为 201；特殊状态在基本信息中注明。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | string | 成功为 `OK`，失败见 [错误码](#errors) |
| `msg` | string | 成功为 `成功`，失败为可读提示 |
| `data` | object/array/null | 业务数据；无返回值或失败时为 null |
| `errors` | array | 始终存在；字段校验失败或账号用户名/邮箱唯一冲突时为 `{field,message}[]`，其余为 [] |

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

分页 `data` 统一如下；各接口响应模型只说明 `records[]` 的业务字段。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `page` | integer | 默认 1，范围 1..100000 |
| `pageSize` | integer | 默认 10，范围 1..100；评论/留言上限以接口为准 |
| `total` | string | 同一过滤条件下的记录数 |
| `totalPages` | string | 总页数，无记录为 `"0"` |
| `records` | array | 本页记录，无记录为 []；超出末页仍保留请求页码 |

响应示例展示代表性业务字段，完整字段与可空性见各接口链接的响应模型。实际响应不因字段为 null 而省略字段。

### 0.3 参数约定

- 参数表中的 Path、Header、Cookie 显式标注在说明中；GET 其余参数为 Query，JSON 接口其余参数为 Body，上传字段为 multipart。无请求体的接口不得发送实际载荷。
- `ID` 是 `1..9223372036854775807` 的十进制字符串，不接受 JSON 数字、负数、小数和前导零。BIGINT 计数、字节数及分页统计也以十进制字符串返回。
- `Time` 是 UTC 毫秒精度 RFC 3339，如 `2026-10-29T02:00:00.000Z`；简历日期等例外按参数表。
- `S(n)`：1..n 个 Unicode 字符；`S0(n)`：0..n；`?`/`null`：可空；`[]`：数组。除密码/Markdown 外文本去首尾空白，可空文本的空串归一为 null。
- 必填不得缺失或 null，显式声明可 null 的字段除外。PUT 为完整替换，可空字段也须出现；PATCH 缺失保持、null 清空可空字段；POST 默认值见参数表。
- 查询参数每项仅传一个非空值；未知、重复或空白参数以及未知 JSON 字段、重复 JSON 属性、错误类型或枚举均返回 400，不静默忽略。
- slug 区分大小写，仅允许 `[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*`；文章最长 200，分类/标签最长 100。名称、用户名、邮箱按数据库排序规则判断唯一。
- URL 最长 2048，外部资源只接受合法 HTTP(S)，社交 mailto 的例外见对应模型；本站附件仅接受指定的文章上下文路径，见 [13.4](#file-consistency)。
- JSON 请求体上限 8MiB；Markdown 解码后最多 1MiB；文件限制见上传接口。审计时间、发布时间及操作者身份由服务端生成。

### 0.4 编辑版本与重试

支持版本校验的详情接口返回 `X-Revision: "<opaque>"`，各接口明确标注；要求版本的写接口须在 `If-Revision` 原样携带该值（含引号），不接受 `*`。缺失返回 428，过期返回 412，先重新获取详情再合并。列表没有逐条版本，编辑前先取详情。

需要版本的写入成功返回新 X-Revision，删除无需返回；无实际变化可以保持原版本。先检查权限和范围，再校验版本与状态；状态不变也不能跳过版本。版本涵盖业务字段及关联集合，不以阅读数或审计时间变化判定编辑冲突。

统一的[批量接口](#admin-batch)是多资源例外：逐条使用 Body 中的 `items[].revision`，不使用整个请求的 If-Revision；状态写入在响应 `data.items[].revision` 返回各资源的新版本，不返回单一 X-Revision。版本来源和校验语义仍与单条接口一致。

没有通用幂等键。创建超时先查询确认；点赞/取消点赞、注销可幂等，OAuth state/ticket 一次性。429 读取 Retry-After；服务不可用返回 503，不当作成功重试。跨域需允许 Authorization/If-Revision/X-Captcha-Id/X-Captcha-Code 并暴露 X-Revision/Location/Retry-After。

### 0.5 查找接口

- [完整接口索引](API_INDEX.md)：先按端，再按业务名称或完整路径查找。
- [文章场景速查](#article-scenarios)：删除、分类/标签/作者筛选、搜索、归档、回收和恢复。
- 当前 **192 个 HTTP 路由及 3 个 WebSocket 入口**，按下方四端分区查找。
- [后台统一操作（29 个入口）](#admin-batch)：参数、响应、权限和整批失败规则集中在 1.22，单个目标也走同一协议。
- 字段与状态、锁顺序、事务及平台接入的完整开发约定见 [接口开发约定](API_CONTRACT.md)，部署与历史验收见 [后端运行说明](后端运行说明.md)。

<a id="request-protection"></a>

<a id="captcha"></a>

### 邮箱验证与登录方式

- 公开注册（`/blog/auth/register` 及保留的 `/admin/auth/register`）必须提供 `email` 和 `emailCode`，仅消费 REGISTER 用途验证码；注册只授予 member，不创建后台身份。
- 前台可以选择用户名密码、邮箱验证码或已绑定第三方登录，三种登录均需填写图形验证码。第三方 LOGIN 不再自动注册账号；历史已有绑定仍可登录。
- 后台仅允许用户名、密码、账号当前邮箱、邮件验证码和图形验证码组合登录；第三方只支持已登录后的绑定。所有受保护 `/admin/**` 都检查后台认证标记，前台会话及旧会话需重新登录后台。

| 接口 | JSON 请求体 | 成功响应 |
| --- | --- | --- |
| `POST /api/v1/blog/auth/email-code` | `{"email":"leaf@example.test","purpose":"REGISTER"}`；purpose 仅 REGISTER / LOGIN | `Result<null>` |
| `POST /api/v1/blog/auth/email-login` | `{"email":"leaf@example.test","emailCode":"012345"}` | `Result<Session>`，三天 |
| `POST /api/v1/admin/auth/email-code` | `{"username":"leaf","password":"Example-password-2026","email":"leaf@example.test"}` | `Result<null>`；先核对账密、当前邮箱、启用状态和 admin:access |

以上接口均公开并要求 `X-Captcha-Id`、`X-Captcha-Code`，只接收声明字段、不接受查询参数。发送成功与失败都会消耗图形验证码；成功后前端刷新图片，注册或登录提交时填写新图片。邮件验证码六位纯数字、五分钟有效，按注册/前台登录/后台登录分开，任何一次邮件校验都会原子消费对应验证码；验证码不出现在 HTTP 响应或日志中。发送接口仅在 SMTP 接受后返回成功，未启用或发送失败返回 503，失败清理验证码。

所有发送用途和端共享每邮箱每 60 秒一次、每可信 IP 每 60 秒五次的限额，超限 429 并带 Retry-After。发送接口在图形验证码通过后计数；后台发送还检查账号登录失败次数。前台密码/邮箱登录及后台登录/发送共用 login-total 每分钟 30 次的入口预算；邮箱或后台邮件验证码失败也计入已有登录失败预算。

账号邮箱沿用 `sys_user.email` 唯一约束，不新增表或自动改写历史用户。邮箱验证码登录每次证明当前邮箱所有权，不把已有联系邮箱或订阅邮箱当作注册验证结果。旧管理员必须预先有可用的账号邮箱及密码；未配置时不能使用新后台登录，应由部署者通过既有账号管理或参数化运维操作补齐，禁止重建业务库。

### 图形验证码

获取接口为 `GET /api/v1/admin/auth/captcha` 和 `GET /api/v1/blog/auth/captcha`，均公开、无查询参数、无请求体，返回 `Cache-Control: no-store`。两端共享每可信 IP 每 60 秒 30 次的生成窗口，超限返回 429 和 Retry-After。

首次加载、点击图片刷新和提交后刷新均调用上述接口、共用同一预算；刷新不会绕过限流，也不会重置窗口。超限在生成图片和写入验证码缓存之前拒绝。

使用 Hutool `CaptchaUtil.createCircleCaptcha` 生成 200×80 PNG、6 位纯数字和 20 个干扰圆圈，数字保留前导零。验证码不是本站会话或 OAuth state，不设置 Cookie，不要求 Bearer。

```http
GET /api/v1/blog/auth/captcha
```

```json
{"code":"OK","msg":"成功","data":{"captchaId":"<43位URL安全随机字符串>","imageBase64":"data:image/png;base64,<PNG内容>","expiresIn":120},"errors":[]}
```

| 响应字段 | 类型 | 说明 |
| --- | --- | --- |
| `captchaId` | string | 43 位 `[A-Za-z0-9_-]` 随机标识，不是十进制业务 ID |
| `imageBase64` | string | PNG Data URI，可直接作为 img 的 src；不返回答案 |
| `expiresIn` | integer | 固定 120 秒，读取不续期 |

以下写接口必须在 Header 携带 `X-Captcha-Id: <captchaId>` 和 `X-Captcha-Code: <6位数字>`，Body 维持各自原有字段：

| 接口 | 验证范围 |
| --- | --- |
| `POST /api/v1/{admin,blog}/auth/register` | 注册 |
| `POST /api/v1/{admin,blog}/auth/login` | 前台账密 / 后台账密与邮箱组合登录 |
| `POST /api/v1/{admin,blog}/auth/email-code` | 发送邮箱验证码 |
| `POST /api/v1/blog/auth/email-login` | 邮箱验证码登录 |
| `POST /api/v1/{admin,blog}/auth/oauth/{source}/authorize` | 仅 `operation=LOGIN`；BIND 沿用原会话、最近认证、Origin 与浏览器校验 |
| `POST /api/v1/blog/articles/{articleId}/comments` | 发表评论或回复 |
| `POST /api/v1/blog/messages` | 发表留言或回复 |
| `PUT /api/v1/blog/me/messages/{messageId}` | 编辑本人留言；仍须携带 If-Revision，修改后重新审核 |
| `POST /api/v1/admin/messages/{messageId}/replies` | 后台回复留言 |

验证码在业务操作前验证；进入校验后的第一次尝试即原子删除，正确、错误或后续业务失败都不能复用。缺失、格式错误、过期、答案错误及重放统一返回 HTTP 400、`CAPTCHA_INVALID`，不累计密码错误次数。Redis 不可用返回 503，不能跳过校验。其他入口校验、鉴权或限流提前拒绝时可能尚未消费，客户端每次写请求结束后均清空输入并重新获取图片，不自动重放写入，正文保留供用户重试。

跨域沿用明确 Origin 白名单，CORS 在鉴权前处理 OPTIONS，并允许 `X-Captcha-Id`、`X-Captcha-Code`、Authorization、Content-Type、If-Revision。验证码不依赖跨域 Cookie；OAuth authorize/exchange 仍带 credentials，回调和兑换不再次要求图形验证码，以免破坏 JustAuth state 与一次性票据流程。日志不得记录验证码答案或上述验证码请求头。

### 0.6 接口限流覆盖与阈值

以下路径均省略统一前缀 `/api/v1`。Admin 与 Blog 的同类认证操作共享既有计数预算；三个公开端读取相同业务数据时共用 Service 缓存。所有限流继续使用本站 Redis 原子固定窗口，不采用参考项目的进程内令牌桶。IP 仅取服务端识别的可信客户端地址；IP、用户、浏览器等是分别累计的维度，任一超限即拒绝，不能仅限制它们的组合。用户只能来自当前有效会话，fingerprint 和 OAuth 浏览器标识均不能代替本站身份。

| 业务及路径 | 操作窗口与默认阈值 | 实施状态 |
| --- | --- | --- |
| `GET /{admin,blog}/auth/captcha` | 两端共用60秒30次/可信IP | 已实现，生成图片前限流 |
| `POST /blog/auth/login` 凭据失败 | 60秒10次；IP、IP+归一用户名 | 已实现，可部署配置 |
| `POST /blog/auth/login` 总请求 | 60秒30次/IP，成功和失败都计数，进入 BCrypt 前执行；独立于失败次数 | 已实现，进入 BCrypt 前累计独立 IP 总预算 |
| `POST /blog/auth/register` | 60秒5次；IP、存在的浏览器标识；无标识按IP | 已实现，可部署配置 |
| `POST /blog/auth/oauth/{source}/authorize`、`POST /blog/auth/oauth/exchange` | 共用60秒20次；IP、浏览器标识；无标识按IP | 已实现，可部署配置 |
| `GET /blog/auth/oauth/{source}/callback` | 所有平台共用60秒30次/IP，独立于发起/兑换窗口；超限不得消费state或调用平台 | 已实现 |
| `PUT/DELETE /blog/me/password` | 两种方法共用60秒10次；IP、用户 | 已实现 |
| `POST /blog/articles/{articleId}/comments` | 60秒10次；IP、用户 | 已实现 |
| `PUT/DELETE /blog/articles/{articleId}/like` | 两种方法及所有文章共用60秒60次；IP、用户 | 已实现 |
| `POST /blog/messages` | 60秒10次；IP、用户 | 已实现，与本人编辑共享窗口 |
| `PUT /blog/me/messages/{messageId}` | 与创建留言共用60秒10次；IP、用户，跨留言ID累计 | 已实现，与创建共享窗口 |
| `POST /admin/messages/{messageId}/replies` | 60秒10次；IP、用户，跨父留言ID累计 | 已实现，跨父留言累计 |
| `POST /admin/files` 上传 | 60秒10次；IP、用户 | 已实现 |
| `PUT /blog/me/rss-subscription` 申请/重发 | 共用60秒5次；IP、用户 | 已实现 |
| `POST /blog/me/rss-subscription/confirm` | 60秒10次；IP、用户，限流在消费票据之前 | 已实现 |
| `POST /blog/rss-subscriptions/unsubscribe` | 60秒30次/IP，跨票据累计；保持有效票据退订幂等 | 已实现 |
| `POST /blog/analytics/online/tickets` | 60秒120次/可信IP、30次/已验证浏览器统计键 | 已实现，两个维度各累计一次 |
| WebSocket `GET /blog/ws/online` Upgrade | 60秒120次/可信IP；每连接业务消息60秒30条 | 已实现；超限握手429与Retry-After，消息超限1008关闭 |
| `POST /blog/analytics/page-views` | 60秒60次/IP、30次/fingerprint；携带有效会话时另限60次/用户；home/blog/cv共用窗口 | 已实现，在行锁、地域解析和写入前按独立 IP、fingerprint、可选用户原子累计 |
| 公开内容GET公共窗口 | 60秒120次/IP；覆盖 `/blog/articles`、文章详情、归档、热门榜、分类/标签列表与详情、作者列表与详情、评论列表、留言列表、友链、配置、音乐、统计、RSS、站点地图及 `/home/profile`、`/home/social-links`、`/cv/experiences`、`/cv/skills` | 已实现，所有这些入口共享，冷/热缓存同样计数 |
| `GET /blog/articles` 且带 `keyword` | 另限60秒10次/IP，跨关键词、页码及作者/分类/标签筛选累计 | 已实现，跨关键词和筛选累计 |
| `GET /blog/articles/{slug}` | 另限60秒60次/IP，跨slug累计 | 已实现，跨 slug 累计 |
| `GET /blog/rss.xml`、`GET /blog/sitemap.xml` | 两种XML及所有子图页共用60秒30次/IP | 已实现，RSS/全部地图页共享 |
| `GET /blog/articles/{articleId}/files/{fileId}/content` | 60秒120次/IP，跨文章及文件累计，独立于公开内容GET公共窗口 | 已实现，独立预算，保持 PUBLIC 和引用校验 |
| `GET /admin/statistics/{overview,page-views,visitors,provinces,articles}` | 五类报表共用60秒30次；IP、用户，跨日期与站点条件累计；花括号内为五个既有路径的简写 | 已实现，实时 ALL 范围与日期检查后累计 |

新增阈值为实现默认值，后续按真实页面请求数、代理/NAT及压测结果调整并同步文档；已在当前实现中启用；部署仍需正确配置可信代理。未列出的普通本人/后台CRUD保持当前鉴权、版本、事务及请求大小约束，不机械增加统一写限流。

同一请求在同一业务窗口只计一次；公共窗口与专用窗口属于独立预算，各计一次，不叠加两套相同业务计数器。路径参数、关键词、查询日期和客户端自报身份不得拆分IP窗口。鉴权、严格输入检查先于业务限流；登录总请求限流先于密码计算；所有限流均先于业务副作用。超限统一 `429/RATE_LIMITED`，携带正整数秒 `Retry-After`，客户端等待后再试；被拒请求不消费state/ticket、不发送邮件、不增加阅读量或写入浏览明细。限流Redis不可用返回503，不跳过保护，也不降级匿名。OPTIONS不计数。

<a id="not-found-protection"></a>

#### 0.6.1 错误页展示与反复 404 临时封禁

四端全屏错误页保留 `404 / 页面不存在`，描述固定为“你访问的页面已经走丢了”；不显示接口 HTTP 状态、业务错误码或原始异常信息。接口仍保留真实 HTTP 状态和 `code/msg/data/errors`，请求层仍能读取 `Retry-After`，不能把错误改成 HTTP 200。

参考 `FeiTwnd-Website` 的限频后封禁一天机制，使用本站 Redis 原子固定窗口：同一可信客户端 IP 在 60 秒内最多允许 20 次不存在访问，第 21 次触发 86400 秒临时封禁。四端共享 IP 预算，不按路径、Token、UA 或 fingerprint 拆分；只存 IP 摘要，不修改本站账号或 `blog_visitor`。封禁期间所有新的 API 请求及 WebSocket 握手返回 `403/ACCESS_BLOCKED`，附正整数秒 `Retry-After`；不会续期，到期自动恢复。已建立的 WebSocket 不主动断开。共享出口 IP 的访问者共用预算，部署须配置准确的可信代理。

服务端实际返回 404 的统一 JSON 响应（含接口路由缺失和业务资源不存在）计一次；达到阈值的响应改为 `403/ACCESS_BLOCKED`。OPTIONS、500/503、网络异常及普通429不计入不存在次数，不能因为错误页采用404样式就封禁服务故障的访问者。Redis 保护不可用时返回 `503/SERVICE_UNAVAILABLE`，不继续执行业务。

SPA 未知路由未必请求后端，Blog 与 Admin 在每次成功进入兜底路由时上报一次；Admin 未登录仍按原规则跳转登录，不将此导航当作404；接口404引起的错误页不再次上报。不上报完整URL、查询参数或错误信息，不自动重试；此上报仅能累计请求者自己的 IP，不能当作账号或统计指纹的封禁依据。关闭 JavaScript 的未知前端路径需由静态站点网关另行限制。

| 项目 | 约定 |
| --- | --- |
| 接口 | `POST /api/v1/blog/security/not-found`；`POST /api/v1/admin/security/not-found` |
| 鉴权 | 公开；必须携带在 `app.auth.allowed-origins` 中的明确 `Origin` |
| 参数 | 无 Path/Query/Body 参数；不接受客户端 IP、用户ID、fingerprint 或目标URL |
| 成功 | HTTP 200；`{"code":"OK","msg":"成功","data":null,"errors":[]}` |
| 错误 | 参数/请求体400，非法来源403/FORBIDDEN，封禁403/ACCESS_BLOCKED，保护不可用503 |
| 副作用 | 只累计当前可信 IP 的不存在窗口；达到阈值写入带一天TTL的封禁键，不创建浏览或访客记录 |

<a id="public-cache"></a>

### 0.7 Redis 业务缓存范围

**本节已接入实现，验证记录见[运行说明](后端运行说明.md#spring-cache-redis)**，参考 FeiTwnd-Website 的按业务分区、差异TTL、读未命中回源和写后失效方式，适配本站Sa-Token、PUBLIC、ALL/SELF、审核与阅读计数规则。下列为服务端Redis缓存，与浏览器/CDN缓存独立；现有 `Cache-Control: no-store` 保持不变。TTL从成功写入起算，命中不续期；正常业务写入必须主动失效，不能等TTL才反映下架或资料变化。

**实现方式明确采用Spring Cache注解缓存，Redis作为缓存后端。** 主页、社交链接、简历、友链、音乐、公开配置等简单查询在Service公开查询方法使用 `@Cacheable`；新增、更新、删除使用 `@CacheEvict`，有分页或多种筛选的业务使用 `allEntries=true`；需要同时清理多个业务域时使用 `@Caching(evict={...})`。不为这些简单缓存逐个编写RedisTemplate读写流程。统一通过 `@EnableCaching` 启用，由 `RedisCacheManager` 配置各业务TTL、键前缀和序列化；注解放置、事务与代理要求见[Spring Cache注解开发约定](API_CONTRACT.md#spring-cache-annotations)。当前已启用并新增 server 的 Boot cache starter；沿用现有 Redis 连接。

| 缓存业务及GET路径 | 默认TTL | 缓存边界与变化来源 |
| --- | --- | --- |
| `/home/profile`、`/home/social-links` | 1小时 | 仅公开资料/可见社交链接；对应后台修改、增删、排序或显示状态变化失效 |
| `/cv/experiences`、`/cv/skills` | 1小时 | 保留类型、分页等实际筛选；对应后台增删改及显示/排序变化失效 |
| `/blog/links`、`/blog/music` | 1小时 | 只含审核通过友链/可见音乐；审核、增删改、排序或显示变化失效 |
| `/blog/configs` | 1小时 | 只缓存site且is_public=1的公开响应；配置修改或撤销公开失效；不缓存comment/message写策略 |
| `/blog/categories`、`/blog/categories/{slug}`、`/blog/tags`、`/blog/tags/{slug}` | 30分钟 | 字典当前资料及PUBLIC计数；字典变化及公开文章集合/关联变化失效 |
| `/blog/authors`、`/blog/authors/{userId}` | 30分钟 | 只含有PUBLIC作品的作者公开资料和计数；作品变化及用户昵称/头像/简介修改失效 |
| `/blog/articles` 不带keyword，包括作者/分类/标签/月组合筛选 | 10分钟 | 缓存文章集合、排序与公开静态资料；响应阅读/点赞/评论计数仍从数据库补充；仅page≤10且pageSize≤20写缓存，其他合法查询正常回源 |
| `/blog/articles` 带keyword | 60秒 | 缓存同一完整查询的搜索集合；仅page≤3且pageSize≤20写缓存；防止大量一次性关键词长驻Redis；仍执行专用搜索限流 |
| `/blog/articles/{slug}` | 15分钟 | 只缓存公开正文、安全HTML、派生阅读信息与公开关联；缓存命中仍校验PUBLIC，并按7.2锁内成功加一次阅读；不缓存整条含副作用的Service调用 |
| `/blog/archives` | 30分钟 | 按页码/页大小缓存月份与PUBLIC数量；发布、下架、回收、恢复、永久删除或可见性变化失效 |
| `/blog/article-rankings` | 60秒 | period、metric、limit及UTC月份分别入键；排行次序与分数使用同一缓存快照，阅读/点赞变化最多延迟60秒；文章下架、删除、标题/slug等资料变化主动失效 |
| `/blog/articles/{articleId}/comments` | 30秒 | 仅公开评论及完整可见父链，不含待审/驳回原文；新增、审核、本人/后台分支删除、祖先状态及文章可见性变化失效 |
| `/blog/statistics` | 5分钟 | 公开汇总；访问PV/UV允许最多5分钟延迟，UTC日期入键；文章/分类/标签变化及后台统计清理主动失效 |
| `/blog/rss.xml`、`/blog/sitemap.xml` | 30分钟 | 缓存XML；RSS和地图分区，地图索引与各page分别入键；依赖内容、字典、公开配置、站点基址变化主动失效；子图仍遵守50000条/50MiB及不存在页404 |

所有分页、筛选、parentId、类型和大小写敏感slug必须完整入键；不能只按路径缓存。当前文章viewCount、likeCount、commentCount保持数据库口径，列表/详情缓存不引入异步计数或Redis点赞副本；排行与公开统计的延迟仅限上表明确的字段。新产生的访问明细及每次阅读/点赞不清空所有文章正文缓存。

**不缓存的业务响应**：`/blog/auth`、`/blog/me`、`/admin` 全部响应；`/blog/messages`（可含本人标记、私密分支及不同会话可见内容）；采集POST及全部业务写响应；文件字节、预览和临时下载地址。会话、OAuth及订阅票据继续使用独立Redis安全存储，不能当作普通查询缓存丢失后回源重建。权限、菜单、账号状态、最近认证、SQL归属及comment/message开关仍实时校验。

缓存命中不得跳过请求校验、限流或原有鉴权/可见性检查；后台Token不扩大公开响应。失败、异常、404和私人数据不写公共缓存；合法空列表可缓存并随新增失效。缓存读故障可在限流及必要安全校验通过后直接读数据库；限流/会话故障仍503，不能以“缓存降级”绕过。事务失效、并发旧值回填、Redis恢复后的处理及后续验收见[缓存与限流开发约定](API_CONTRACT.md#cache-contract)。

<a id="cache-lifecycle"></a>

### 0.8 新增、更新、删除后的完整缓存策略

本节将参考项目的Spring Cache注解 `@Cacheable` 读缓存、`@Caching` 联合清理、`@CacheEvict(allEntries=true)` 清理整个业务域的经验，落实到本站实际写接口。**统一采用“读未命中回源并缓存；新增/修改/删除提交后删除关联缓存；下一次读再重建”**，不使用 `@CachePut` 把写请求DTO直接变成公开响应，也不只依赖TTL。具体事务时机和故障规则见[开发约定0.8.4](API_CONTRACT.md#cache-contract)。

#### 0.8.1 读取、写入与删除的顺序

| 阶段 | 必须执行的缓存处理 |
| --- | --- |
| 首次读取或条目过期 | 完成校验、限流、可见性检查；数据库查询及响应组装成功后才缓存公开结果，设置0.7的TTL |
| 缓存命中 | 使用当前有效业务代次，写入保护期间不命中；保持原有PUBLIC/鉴权及阅读副作用规则，不续TTL，不回写私人字段 |
| 新增数据 | 数据库提交后清理所属列表、筛选页、total及派生汇总；此前缓存的空页/空集合也必须清掉，不能只处理新记录详情 |
| 修改数据 | 锁内取得修改前的slug、关联和身份资料；提交后清理旧值和新值可能命中的详情、全部相关列表和派生缓存；重命名后旧slug不能继续读到旧内容 |
| 删除、下架、转私密、回收 | 提交后删除对象详情缓存，同时使依赖它的列表、计数、导航、推荐、RSS和地图失效；不能只删除一个详情键，不能把已删除对象写回缓存 |
| 删除或修改数据库失败 | 不缓存写请求结果、不伪造删除成功；数据库回滚时按原事务结果处理保护标记，已有已提交数据仍按原规则读取 |
| 写入后的下一次读取 | 从已提交数据库数据重新计算当前集合、total和公开资料；已删除/不可见目标仍按原接口404或空集合返回，404不写缓存 |

“删除缓存”分两种：可准确定位的单条数据键直接删除；同一业务有多页、多种组合参数时按业务域整体失效，避免漏掉未知组合键。后者按开发约定更换业务代次，使旧分区立即不可读、旧条目按TTL回收，效果不能变成等TTL才生效。禁止FLUSHDB/FLUSHALL或顺带删除会话、限流、OAuth及订阅票据；不在请求中用KEYS扫描整库。缓存清理针对服务端Redis，不放宽既有HTTP no-store策略。

#### 0.8.2 写接口与缓存删除清单

以下路径省略 `/api/v1`，`PUT/DELETE`表示同一路径的两种方法。为避免漏删，文章及字典元数据写入采用关联业务域整体失效，不为每个作者、分类、标签、关键词和页码枚举删除键。

| 写接口 | 提交后必须删除或整体失效的缓存 |
| --- | --- |
| `POST /admin/articles`；`PUT /admin/articles/{articleId}` | 文章列表及全部组合筛选/搜索、文章详情域（含其他文章的上下篇/推荐）、归档、热门榜、分类/标签列表与详情计数、作者列表/详情、公开统计、RSS、全部地图索引/子页，以及该文章全部公开评论页；草稿写入也按此统一规则清理 |
| `PUT /admin/articles/{articleId}/status` | 同上；下架不能等15分钟详情TTL或30分钟XML TTL；首次发布清理之前的空集合，重复发布不改变原有邮件发送规则 |
| `DELETE /admin/articles/{articleId}`；`POST /admin/articles/{articleId}/restore` | 同文章元数据写入，并清理该文章的全部公开评论页；恢复保持下架，清缓存不代表恢复成PUBLIC |
| `DELETE /admin/articles/{articleId}/permanent` | 同回收规则；成功后文章详情、评论、列表、导航、推荐和XML不得残留可访问条目；文件仍按原业务保留，不因清缓存删除存储对象 |
| `POST /admin/categories`；`PUT/DELETE /admin/categories/{categoryId}` | 分类列表/详情域；文章列表/搜索/详情域、归档、热门榜、作者列表/详情、公开统计、RSS及地图；覆盖改名、隐藏、排序、旧/新slug及删除后category_id置null |
| `POST /admin/tags`；`PUT/DELETE /admin/tags/{tagId}` | 标签列表/详情域；文章列表/搜索/详情域、归档、热门榜、作者列表/详情、公开统计、RSS及地图；删除还覆盖文章标签关联级联变化，不能只删标签详情 |
| `PUT /admin/users/batch-enable`；`PUT /admin/users/batch-disable` | 作者、文章、搜索、文章详情和评论缓存整批成功后统一清理；只修改账号状态 |
| `POST /admin/users/batch-delete` | 清理统计缓存；点赞按明细实时聚合，历史浏览及日志保留 |
| `PATCH /blog/me/profile`；`PUT /admin/users/{userId}` 中公开字段变化 | 作者列表/详情、含作者资料的文章列表/搜索/详情、公开评论域；昵称/头像/简介变化不能继续使用缓存中的旧资料；邮箱等未公开字段不进入公共缓存 |
| `POST /blog/articles/{articleId}/comments` | 该articleId下所有parentId、分页的公开评论缓存；待审新增也统一清理；文章列表/详情的评论数仍实时查库，不清空所有正文 |
| `DELETE /blog/me/comments/{commentId}`；`PUT /admin/comments/{commentId}/review`；`DELETE /admin/comments/{commentId}` | 根据数据库中的articleId清理该文章全部公开评论页和回复页；祖先审核或删除影响后代，不能只删commentId对应的一条结果 |
| `PUT/DELETE /blog/articles/{articleId}/like`；成功文章详情GET | 不删正文缓存，不建立Redis计数副本；本次互动数按数据库读取，排行榜最多延迟60秒；这是0.7明确允许的计数例外 |
| `POST /admin/links`；`PUT/DELETE /admin/links/{linkId}` | 整个公开友链域，含所有分页与排序；审核拒绝/隐藏/删除后下一次公开查询不能继续显示旧友链 |
| `PUT /admin/configs/site` | 公开site配置、RSS及地图；从公开改为不公开也必须删旧缓存，RSS继续按原规则检查必需公开配置 |
| `PUT /admin/configs/comment`、`PUT /admin/configs/message` | 不建立或清理不存在的策略缓存；继续原配置行锁与锁内实时读取，新开关/审核策略立即用于后续写请求 |
| `PUT /admin/home/profile` | 整个主页公开资料域；不与sys_user作者资料混为同一业务 |
| `POST /admin/home/social-links`；`PUT/DELETE /admin/home/social-links/{socialId}` | 整个公开社交链接域，含显示、隐藏、排序及空列表 |
| `POST /admin/cv/experiences`；`PUT/DELETE /admin/cv/experiences/{experienceId}` | 整个公开经历域，含全部类型和分页；改变type时旧类型与新类型都必须失效 |
| `POST /admin/cv/skills`；`PUT/DELETE /admin/cv/skills/{skillId}` | 整个公开技能域，含实际筛选及分页；更新/隐藏/排序/删除均清理 |
| `POST /admin/music`；`PUT/DELETE /admin/music/{musicId}` | 整个公开播放列表域，含所有分页；显示、排序和地址变化一并生效 |
| `POST /blog/analytics/page-views` | 不缓存写入响应，不逐次清空公开统计；PV/UV按0.7允许的5分钟TTL更新；不改变文章累计阅读量 |
| `DELETE /admin/visitors/{visitorId}`；`DELETE /admin/page-views/{viewId}` | 公开统计域主动失效；封禁/解封统计标识仍实时读库，不创建访客身份缓存 |
| 文件上传/删除、留言创建/编辑/审核/删除/回复、邮件订阅各类写接口、日志清理及其余身份/授权写接口 | 对应业务响应没有公共查询缓存，不机械清空整站。文件删除先按原规则拒绝仍被引用的文件；留言私密可见性、订阅轮次及会话撤销照旧执行，不能借公共缓存清理删除安全状态 |

无实际业务变化的幂等写入可以不重复清缓存，但仍须完成鉴权、版本及状态检查；前次提交后缓存清理失败且保护尚未解除时，不能因“数据库已是目标值”跳过失效恢复。所有写入口（HTTP或现有内部调用）都在真正落库的Service负责关联清理，不能只在Controller清理。参考项目的异步摘要回填也会主动清理文章缓存；本站今后若增加内部写入，须沿用同一规则，本次不新增摘要任务。

参考方法、实际清理缓存名及本站补齐项见[参考项目写入清理对照](API_CONTRACT.md#reference-cache-eviction)；并发、回滚及提交后Redis故障仍按[事务失效规则](API_CONTRACT.md#cache-contract)处理。当前已在真正落库的 Service 接入；实际缓存域与恢复步骤见运行说明。

<a id="client-admin"></a>

## 1. Admin 管理端

<a id="admin-group-1"></a>

### 1.1 认证与第三方授权

<a id="admin-api-1"></a>

#### 1.1.1 注册账号

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/auth/register` |
| 接口描述 | 注册账号 |
| Content-Type | application/json |
| 访问要求 | 公开 |
| 成功状态 | 201 Created |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `X-Captcha-Id` | string | 是 | Header；见[验证码契约](#captcha) |
| `X-Captcha-Code` | string | 是 | Header；6 位数字，保留前导零 |
| `username` | string | 是 | 3..64 位 ASCII 字母、数字或下划线；全库不区分大小写唯一 |
| `password` | string | 是 | 8..64 个字符且 UTF-8 ≤72 字节；不 trim、不截断 |
| `nickname` | string | 是 | 1..64 字符 |
| `email` | string | 是 | 合法邮箱，≤254 字符，去首尾空白并转小写；注册后保存为账号邮箱 |
| `emailCode` | string | 是 | 对应用途邮件中的六位数字；五分钟有效，校验一次即失效 |

##### 请求示例

```http
POST /api/v1/admin/auth/register
X-Captcha-Id: <captchaId>
X-Captcha-Code: 012345
Content-Type: application/json

{"username":"leaf","password":"Example-password-2026","nickname":"落叶","email":"leaf@example.test","emailCode":"012345"}
```

##### 响应参数

`data`：[Me](#response-me)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "userId": "12", "username": "leaf", "nickname": "落叶", "email": "leaf@example.test", "avatar": null, "intro": null,
    "status": 1
  },
  "errors": []
}
```

先验证 REGISTER 用途的邮箱验证码；用户和 member 关联同事务创建；仅返回资料，不自动登录，不接受角色、状态或客户端 userId。用户名、邮箱或二者重复时，返回 [409/UNIQUE_CONFLICT 及对应字段提示](#user-unique-conflict)。

---

<a id="admin-api-2"></a>

#### 1.1.2 账密与邮箱组合登录

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/auth/login` |
| 接口描述 | 账密与邮箱组合登录 |
| Content-Type | application/json |
| 访问要求 | 公开 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `X-Captcha-Id` | string | 是 | Header；见[验证码契约](#captcha) |
| `X-Captcha-Code` | string | 是 | Header；6 位数字，保留前导零 |
| `username` | string | 是 | 3..64 位 ASCII 字母、数字或下划线；全库不区分大小写唯一 |
| `password` | string | 是 | 8..64 个字符且 UTF-8 ≤72 字节；不 trim、不截断 |
| `email` | string | 是 | 合法邮箱，≤254 字符，去首尾空白并转小写；必须匹配该账号当前邮箱 |
| `emailCode` | string | 是 | 对应用途邮件中的六位数字；五分钟有效，校验一次即失效 |

##### 请求示例

```http
POST /api/v1/admin/auth/login
X-Captcha-Id: <captchaId>
X-Captcha-Code: 012345
Content-Type: application/json

{"username":"leaf","password":"Example-password-2026","email":"leaf@example.test","emailCode":"012345"}
```

##### 响应参数

`data`：[Session](#response-session)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "tokenType": "Bearer", "accessToken": "<site-token>", "expiresIn": 604800,
    "user": {
      "userId": "12", "username": "leaf", "nickname": "落叶", "email": null, "avatar": null, "intro": null,
      "status": 1
    }
  },
  "errors": []
}
```

用户名不存在、密码错误、纯第三方账号无密码统一返回 401/AUTH_FAILED；验证凭据后再区分禁用账号。持有用户行锁确认身份和状态后创建会话、更新 lastLoginTime，提交后才返回；数据库提交失败撤销刚签发 Token，Redis 创建失败返回 503。

---

<a id="admin-api-3"></a>

#### 1.1.3 退出当前登录

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/auth/logout` |
| 接口描述 | 退出当前登录 |
| Content-Type | 无请求体 |
| 访问要求 | 幂等注销；无 Token 或已失效仍返回 200 |

##### 请求参数

无业务参数；鉴权见访问要求。

##### 请求示例

```http
POST /api/v1/admin/auth/logout
Authorization: Bearer <site-token>
```

##### 响应参数

`data` 固定为 null。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

仅撤销当前 Token；缺失或已失效也返回 200，不泄露会话状态。

---

<a id="admin-api-4"></a>

#### 1.1.4 查询可用第三方平台

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/auth/oauth/providers` |
| 接口描述 | 查询可用第三方平台 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

##### 请求参数

无 Path、Query 或请求体参数；GET/无模型写操作不发送请求体。

##### 请求示例

```http
GET /api/v1/admin/auth/oauth/providers
```

##### 响应参数

`data`：[Provider](#response-provider) 数组。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":[{"source":"GITHUB","displayName":"GitHub"}],"errors":[]}
```

只列配置完整且启用的平台，示例 GITHUB 不代表已经开通；不返回 App ID/Secret。

---

<a id="admin-api-5"></a>

#### 1.1.5 发起第三方绑定

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/auth/oauth/{source}/authorize` |
| 接口描述 | 发起第三方绑定 |
| Content-Type | application/json |
| 访问要求 | 仅 BIND；须后台完整认证会话 + 最近 300 秒认证；浏览器 Cookie/Origin 必填 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `source` | string | 是 | Path；服务端已启用平台的大写标识；未知或未启用 404 |
| `operation` | string | 是 | 仅允许 BIND 绑定当前用户；LOGIN 返回 403/FORBIDDEN；不接收 redirectUri/userId/uuid |
| `__Host-dl-oauth-browser` | string | 是（发起时可由后端新设） | Cookie；Secure、HttpOnly、SameSite=Lax、Path=/，仅绑定浏览器，不是业务登录凭证 |
| `Origin` | string | 是 | Header；浏览器自动发送，必须匹配配置前端 origin；BIND/兑换附加身份要求见访问要求 |

##### 请求示例

```http
POST /api/v1/admin/auth/oauth/GITHUB/authorize
Cookie: __Host-dl-oauth-browser=<browser-id>
Origin: https://blog.example.com
Authorization: Bearer <admin-token>
Content-Type: application/json

{"operation":"BIND"}
```

##### 响应参数

`data`：[OAuthAuthorizeResult](#response-oauthauthorizeresult)。

##### 响应示例

```http
Set-Cookie: __Host-dl-oauth-browser=<browser-id>; Max-Age=600; Path=/; Secure; HttpOnly; SameSite=Lax
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "authorizationUrl": "https://github.com/login/oauth/authorize?client_id=<configured>&state=<random>",
    "expiresIn": 300
  },
  "errors": []
}
```

后台禁止第三方直接登录。BIND 需完成后台账密和邮箱验证的原 Token 及最近 300 秒认证，校验 Origin，并设置/读取 OAuth 浏览器 Cookie；绑定使用弹窗保留原 Token。

---

<a id="admin-api-6"></a>

#### 1.1.6 第三方平台授权回调

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/auth/oauth/{source}/callback` |
| 接口描述 | 第三方平台授权回调 |
| Content-Type | 无请求体 |
| 访问要求 | 仅平台回调；校验 state、浏览器 Cookie、流程及原会话；不要求回调请求携带本站 Bearer |
| 成功状态 | 303（可信流程重定向） |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `source` | string | 是 | Path；服务端已启用平台的大写标识；未知或未启用 404 |
| `__Host-dl-oauth-browser` | string | 是（发起时可由后端新设） | Cookie；Secure、HttpOnly、SameSite=Lax、Path=/，仅绑定浏览器，不是业务登录凭证 |
| `state` | string | 是 | 与当前浏览器、平台和流程匹配，一次性 |
| `code` | string | 成功时 | 所选平台 GET 回调授权码；适配器负责平台实际参数 |
| `error` | string | 拒绝时 | 平台取消/错误标记，不透传平台说明 |
| `error_description` | string | 否 | 平台错误说明，仅允许带回；不交给业务、不写日志、不回显 |

##### 请求示例

```http
GET /api/v1/admin/auth/oauth/GITHUB/callback?code=<platform-code>&state=<random-state>
Cookie: __Host-dl-oauth-browser=<browser-id>
```

##### 响应参数

| 字段/响应头 | 类型 | 说明 |
| --- | --- | --- |
| `Location` | string | 固定前端完成页；成功 fragment 携带 ticket，可信失败携带白名单错误码 |
| `Cache-Control` | string | no-store |
| `Referrer-Policy` | string | no-referrer |
| 响应体 | — | 成功重定向不返回 Result；不可信/无效流程直接返回对应 HTTP JSON 错误 |

##### 响应示例

```http
HTTP/1.1 303 See Other
Location: https://blog.example.com/oauth/complete#ticket=<one-time-ticket>
Cache-Control: no-store
Referrer-Policy: no-referrer
```

平台顶层 GET 回调不携带本站 Bearer。后端凭 state、浏览器 Cookie 和 Redis 流程定位操作；BIND 再校验服务端保存的原会话仍有效且属于目标用户。可信流程成功后 303 到固定完成页，不在回调直接向浏览器发本站 Token。已消费的可信流程遇到数据库访问或事务提交失败时，303 到固定完成页的 `#error=SERVICE_UNAVAILABLE`，不签发成功票据，且不能重放本次 state。

---

<a id="admin-api-7"></a>

#### 1.1.7 兑换一次性绑定结果

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/auth/oauth/exchange` |
| 接口描述 | 兑换一次性绑定结果 |
| Content-Type | application/json |
| 访问要求 | 同浏览器 Cookie；仅 BIND，须完成后台完整认证的原 Token；LOGIN 禁止 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ticket` | string | 是 | 回调得到的本站一次性票据；有效期 60 秒，不是平台 code 或本站 Token |
| `__Host-dl-oauth-browser` | string | 是（发起时可由后端新设） | Cookie；Secure、HttpOnly、SameSite=Lax、Path=/，仅绑定浏览器，不是业务登录凭证 |
| `Origin` | string | 是 | Header；浏览器自动发送，必须匹配配置前端 origin；BIND/兑换附加身份要求见访问要求 |

##### 请求示例

```http
POST /api/v1/admin/auth/oauth/exchange
Cookie: __Host-dl-oauth-browser=<browser-id>
Origin: https://blog.example.com
Authorization: Bearer <admin-token>
Content-Type: application/json

{"ticket":"<one-time-ticket>"}
```

##### 响应参数

`data`：[OAuthResult](#response-oauthresult)。

##### 响应示例

BIND 成功（200，原 Token 必须有效，不返回新 Session）：

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "operation": "BIND",
    "binding": {
      "source": "GITHUB", "createTime": "2026-10-29T02:00:00.000Z"
    }
  },
  "errors": []
}
```

必须携带同浏览器 Cookie 及发起时已完成后台完整认证的原 Token，仅返回 binding，不切换账号；禁止通过 LOGIN 票据获取后台会话。票据有效期 60 秒且仅消费一次。

---

<a id="admin-group-2"></a>

### 1.2 本人资料与安全设置

<a id="admin-api-8"></a>

#### 1.2.1 查询本人资料

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/me` |
| 接口描述 | 查询本人资料 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

##### 请求参数

无业务参数；鉴权见访问要求。

##### 请求示例

```http
GET /api/v1/admin/me
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[Me](#response-me)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "userId": "12", "username": "leaf", "nickname": "落叶", "email": null, "avatar": null, "intro": null,
    "status": 1
  },
  "errors": []
}
```

---

<a id="admin-api-9"></a>

#### 1.2.2 修改本人资料

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PATCH /api/v1/admin/me/profile` |
| 接口描述 | 修改本人资料 |
| Content-Type | application/json |
| 访问要求 | 登录本人 |
| 并发校验 | 必填 If-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `nickname` | string | 否 | 提供时不可为 null，1..64 字符 |
| `email` | string&#124;null | 否 | 合法邮箱，≤254 字符，唯一；null 清空 |
| `avatar` | string&#124;null | 否 | 外部 http(s) URL ≤2048 字符；null 清空 |
| `intro` | string&#124;null | 否 | 纯文本 ≤500 字符；null 清空；四项至少提供一项 |

##### 请求示例

```http
PATCH /api/v1/admin/me/profile
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"nickname":"新落叶","email":null,"avatar":null,"intro":null}
```

##### 响应参数

`data`：[Me](#response-me)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "userId": "12", "username": "leaf", "nickname": "新落叶", "email": null, "avatar": null, "intro": null,
    "status": 1
  },
  "errors": []
}
```

至少一项；缺失保持，null 清空可空字段，可空字符串去首尾空白后为空也清空。username、status、角色、权限不可改。头像只接受外部 HTTP(S) 资源，不接受附件 API 路径及其等价绝对 URL（含路径编码、归一化后的形式）。

---

<a id="admin-api-10"></a>

#### 1.2.3 设置或修改本站密码

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/me/password` |
| 接口描述 | 设置或修改本站密码 |
| Content-Type | application/json |
| 访问要求 | 登录本人 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `newPassword` | string | 是 | 同注册密码规则 |
| `currentPassword` | string | 条件必填 | 已有密码时必填且必须验证；纯第三方无密码账号必须省略 |

##### 请求示例

```http
PUT /api/v1/admin/me/password
Authorization: Bearer <site-token>
Content-Type: application/json

{"newPassword":"New-example-password-2026","currentPassword":"Example-password-2026"}
```

##### 响应参数

`data` 固定为 null。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

最近认证；有密码验证 currentPassword，无密码必须由最近第三方登录证明身份。成功撤销本人全部会话。

---

<a id="admin-api-11"></a>

#### 1.2.4 清除本站密码

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/me/password` |
| 接口描述 | 清除本站密码 |
| Content-Type | application/json |
| 访问要求 | 登录本人 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `currentPassword` | string | 是 | 验证现有密码；无密码账号返回 STATE_CONFLICT |

##### 请求示例

```http
DELETE /api/v1/admin/me/password
Authorization: Bearer <site-token>
Content-Type: application/json

{"currentPassword":"Example-password-2026"}
```

##### 响应参数

`data` 固定为 null。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

最近认证并验证现密码；锁用户后确认至少保留一种可用第三方登录方式，成功撤销全部会话。

---

<a id="admin-api-12"></a>

#### 1.2.5 查询本人有效角色与权限

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/me/capabilities` |
| 接口描述 | 查询本人有效角色与权限 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

##### 请求参数

无业务参数；鉴权见访问要求。

##### 请求示例

```http
GET /api/v1/admin/me/capabilities
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[Capabilities](#response-capabilities)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "roleCodes": [
      "member"
    ],
    "permissionCodes": [
      "blog:article:like"
    ],
    "dataScopes": {
      "blog:article:like": "SELF", "blog:comment:create": "SELF", "blog:comment:deleteOwn": "SELF",
      "blog:message:create": "SELF", "blog:message:deleteOwn": "SELF", "blog:message:updateOwn": "SELF"
    }
  },
  "errors": []
}
```

普通 member 也可调用；仅返回当前有效授权，角色/权限字典序，数据范围按每个权限计算。

---

<a id="admin-api-13"></a>

#### 1.2.6 查询本人可访问菜单树

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/me/menus` |
| 接口描述 | 查询本人可访问菜单树 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

##### 请求参数

无业务参数；鉴权见访问要求。

##### 请求示例

```http
GET /api/v1/admin/me/menus
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[MenuNode](#response-menunode) 数组。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": [
    {
      "menuId": "100", "parentId": null, "menuCode": "content", "menuName": "内容管理", "menuType": "D",
      "routeName": "Content", "status": 1
    }
  ],
  "errors": []
}
```

无 admin:access 返回 []。菜单授权与权限求交集并检查祖先；返回隐藏但有权的路由和按钮，按钮不能生成前端路由。

---

<a id="admin-api-14"></a>

#### 1.2.7 查询本人第三方绑定

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/me/oauth-bindings` |
| 接口描述 | 查询本人第三方绑定 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

##### 请求参数

无业务参数；鉴权见访问要求。

##### 请求示例

```http
GET /api/v1/admin/me/oauth-bindings
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[OAuthBinding](#response-oauthbinding) 数组。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":[{"source":"GITHUB","createTime":"2026-10-29T02:00:00.000Z"}],"errors":[]}
```

source 升序，只列当前配置完整且启用的平台绑定，仅返回平台和绑定时间，不暴露 uuid、绑定记录 ID 或第三方 Token。未启用或配置不完整的绑定记录仍保留在库中，不计为可用登录方式。

---

<a id="admin-api-15"></a>

#### 1.2.8 解绑本人第三方账号

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/me/oauth-bindings/{source}` |
| 接口描述 | 解绑本人第三方账号 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `source` | string | 是 | Path；服务端已启用平台的大写标识；未知或未启用 404 |

##### 请求示例

```http
DELETE /api/v1/admin/me/oauth-bindings/GITHUB
Authorization: Bearer <site-token>
```

##### 响应参数

`data` 固定为 null。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

最近认证；未绑定 404。锁同一用户，检查剩余有效密码或可用平台绑定；禁止解绑最后登录方式，成功撤销全部会话。

---

<a id="group-6"></a>

<a id="admin-group-3"></a>

### 1.3 菜单管理

<a id="api-32"></a>

#### 1.3.1 查询后台完整菜单树

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/menus` |
| 接口描述 | 查询后台完整菜单树 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`sys:menu:query` |

##### 请求参数

无业务参数；鉴权见访问要求。

##### 请求示例

```http
GET /api/v1/admin/menus
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[MenuNode](#response-menunode) 数组。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": [
    {
      "menuId": "100", "parentId": null, "menuCode": "content", "menuName": "内容管理", "menuType": "D",
      "routeName": "Content", "status": 1
    }
  ],
  "errors": []
}
```

含禁用、隐藏及按钮节点，按 sortOrder ASC,menuId ASC 逐层排序。

---

<a id="api-33"></a>

#### 1.3.2 查询菜单详情

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/menus/{menuId}` |
| 接口描述 | 查询菜单详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`sys:menu:query` |
| 并发校验 | 返回 X-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `menuId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/admin/menus/110
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[Menu](#response-menu)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "menuId": "110", "parentId": "100", "menuCode": "article", "menuName": "文章管理", "menuType": "M",
    "routeName": "Article", "status": 1
  },
  "errors": []
}
```

返回完整持久化菜单字段和 X-Revision，不按启用、可见性或本人菜单授权过滤。摘要覆盖 MenuWrite 全部业务字段（含稳定 menuCode），不包含审计时间、子节点及 role_menu 集合。

---

<a id="api-34"></a>

#### 1.3.3 创建菜单节点

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/menus` |
| 接口描述 | 创建菜单节点 |
| Content-Type | application/json |
| 访问要求 | 后台：`sys:menu:create` |
| 成功状态 | 201 Created |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `parentId` | ID&#124;null | 是，可 null | 根节点 null；D/M 父为 D 或 null；B 父必须为 M |
| `menuCode` | string | 是 | [A-Za-z0-9:_-]+，≤64，区分大小写唯一、创建后不可变 |
| `menuName` | string | 是 | 1..64 字符 |
| `menuType` | string | 是 | D 目录、M 页面、B 按钮 |
| `routeName` | string&#124;null | 条件必填 | D/M 非空且唯一，[A-Za-z][A-Za-z0-9_]* ≤64；B 必须 null |
| `path` | string&#124;null | 条件必填 | D/M 以 / 起始的唯一内部路径 ≤255；无 ..、反斜线、协议、查询串、fragment；B 为 null |
| `component` | string&#124;null | 条件必填 | D/M 为组件注册白名单键 ≤255，B 为 null |
| `icon` | string&#124;null | 否 | 图标白名单键 ≤64，默认 null |
| `permissionId` | ID&#124;null | 条件必填 | D 必须 null；M/B 指向已存在权限 |
| `sortOrder` | integer | 否 | int32，默认 0，升序 |
| `isVisible` | integer | 否 | 0/1，默认 1；B 默认且必须 0 |
| `keepAlive` | integer | 否 | 默认 0；仅 M 可为 1 |
| `status` | integer | 否 | 0/1，默认 1 |

##### 请求示例

```http
POST /api/v1/admin/menus
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "parentId": "100", "menuCode": "articleExtra", "menuName": "文章扩展页", "menuType": "M",
  "routeName": "ArticleExtra", "path": "/content/articles-extra", "component": "content/article/index",
  "icon": "document", "permissionId": "10", "sortOrder": 20, "isVisible": 1, "keepAlive": 0, "status": 1
}
```

##### 响应参数

`data`：[Menu](#response-menu)。

##### 响应示例

```http
Location: /api/v1/admin/menus/10
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "menuId": "300", "parentId": "100", "menuCode": "articleExtra", "menuName": "文章扩展页", "menuType": "M",
    "routeName": "ArticleExtra", "status": 1
  },
  "errors": []
}
```

在同一 READ COMMITTED 事务先锁固定菜单结构检查点（sys_role.role_id=1），再锁操作者，锁内复核原 Token、启用身份及双权限后，验证父类型、完整祖先链、组件/图标白名单及页面/按钮权限引用。M/B 仅要求门禁权限存在，允许引用停用权限，此时本人树不返回该节点；不隐式授予任何角色。允许在禁用父节点下维护结构，本人树仍截断禁用分支。后端菜单 path 不作为 API 鉴权规则。

---

<a id="api-35"></a>

#### 1.3.4 修改菜单节点

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/menus/{menuId}` |
| 接口描述 | 修改菜单节点 |
| Content-Type | application/json |
| 访问要求 | 后台：`sys:menu:update` |
| 并发校验 | 必填 If-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `menuId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `parentId` | ID&#124;null | 是，可 null | 根节点 null；D/M 父为 D 或 null；B 父必须为 M |
| `menuName` | string | 是 | 1..64 字符 |
| `menuType` | string | 是 | D 目录、M 页面、B 按钮 |
| `routeName` | string&#124;null | 是，可 null | D/M 非空且唯一，[A-Za-z][A-Za-z0-9_]* ≤64；B 必须 null |
| `path` | string&#124;null | 是，可 null | D/M 以 / 起始的唯一内部路径 ≤255；无 ..、反斜线、协议、查询串、fragment；B 为 null |
| `component` | string&#124;null | 是，可 null | D/M 为组件注册白名单键 ≤255，B 为 null |
| `icon` | string&#124;null | 是，可 null | 图标白名单键 ≤64，默认 null |
| `permissionId` | ID&#124;null | 是，可 null | D 必须 null；M/B 指向已存在权限 |
| `sortOrder` | integer | 是 | int32，默认 0，升序 |
| `isVisible` | integer | 是 | 0/1，默认 1；B 默认且必须 0 |
| `keepAlive` | integer | 是 | 默认 0；仅 M 可为 1 |
| `status` | integer | 是 | 0/1，默认 1 |

##### 请求示例

```http
PUT /api/v1/admin/menus/110
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "parentId": "100", "menuName": "文章扩展页", "menuType": "M", "routeName": "ArticleExtra",
  "path": "/content/articles-extra", "component": "content/article/index", "icon": "document",
  "permissionId": "10", "sortOrder": 20, "isVisible": 1, "keepAlive": 0, "status": 1
}
```

##### 响应参数

`data`：[Menu](#response-menu)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "menuId": "110", "parentId": "100", "menuCode": "article", "menuName": "文章扩展页", "menuType": "M",
    "routeName": "ArticleExtra", "status": 1
  },
  "errors": []
}
```

先锁固定菜单结构检查点，再按 ID 升序锁操作者及受影响分支关联用户，最后锁目标节点并复核身份、双权限与版本。检查自引用、移入后代、完整祖先链和类型变化对全部后代的约束，禁止并发移动产生环；menuCode 不可改。字段与门禁校验同创建，NULL 必须实际清空。停用、隐藏、移动及修改门禁均保留角色关联及会话；菜单实时读库，无旧菜单缓存。移动不自动授予新祖先，修改门禁不自动授予新权限；需显式重新保存角色 grants。

---


<a id="group-3"></a>

<a id="admin-group-4"></a>

### 1.4 用户管理

用户批量操作见[批量启用](#batch-user-enable)、[批量禁用](#batch-user-disable)、[批量删除（含单个用户删除）](#batch-user-delete)，均支持 1..100 个显式目标。

<a id="api-16"></a>

#### 1.4.1 分页查询用户

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/users` |
| 接口描述 | 分页查询用户 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`sys:user:query` |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `status` | integer | 否 | 可选，0/1；含义见该业务状态约定 |
| `roleId` | ID | 否 | 可选，按关联角色筛选 |

##### 请求示例

```http
GET /api/v1/admin/users?page=1&pageSize=10
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：分页对象，`records[]` 为 [UserAdmin](#response-useradmin)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "userId": "12", "username": "leaf", "nickname": "落叶", "email": null, "avatar": null, "intro": null,
        "status": 1
      }
    ]
  },
  "errors": []
}
```

按 createTime DESC,userId DESC；全局操作。keyword 去首尾空白后按 username、nickname、email 字面包含匹配，遵循数据库字符排序规则，`%`、`_`、反斜杠均不作为 SQL 通配符。roleId 按完整关联过滤，包含已分配但禁用的角色；多个角色不会重复计算用户。

---

<a id="api-17"></a>

#### 1.4.2 查询用户详情

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/users/{userId}` |
| 接口描述 | 查询用户详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`sys:user:query` |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/admin/users/12
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[UserAdmin](#response-useradmin)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "userId": "12", "username": "leaf", "nickname": "落叶", "email": null, "avatar": null, "intro": null,
    "status": 1
  },
  "errors": []
}
```

详情返回完整 roleIds（包含已分配但禁用的角色）和与本人资料共用的 X-Revision；不存在返回 404。密码、登录时间及审计时间不参与版本。

---

<a id="api-18"></a>

#### 1.4.3 创建用户

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/users` |
| 接口描述 | 创建用户 |
| Content-Type | application/json |
| 访问要求 | 后台：`sys:user:create` |
| 成功状态 | 201 Created |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 | 3..64 位 ASCII 字母、数字或下划线；全库不区分大小写唯一 |
| `password` | string | 是 | 8..64 个字符且 UTF-8 ≤72 字节；不 trim、不截断 |
| `nickname` | string | 是 | 1..64 字符 |
| `email` | string&#124;null | 否 | 同本人资料，默认 null |
| `avatar` | string&#124;null | 否 | 仅外部 http(s)，默认 null |
| `intro` | string&#124;null | 否 | ≤500 字符，默认 null |
| `status` | integer | 否 | 0 禁用、1 启用，默认 1；不接受 roleIds |

##### 请求示例

```http
POST /api/v1/admin/users
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "username": "leaf", "password": "Example-password-2026", "nickname": "落叶", "email": null, "avatar": null,
  "intro": null, "status": 1
}
```

##### 响应参数

`data`：[UserAdmin](#response-useradmin-2)。

##### 响应示例

```http
Location: /api/v1/admin/users/12
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "userId": "12", "username": "leaf", "nickname": "落叶", "email": null, "avatar": null, "intro": null,
    "status": 1
  },
  "errors": []
}
```

新建用户不分配角色；需要 assignRole 权限才能另行分配，不沿用公开注册的 member 默认。用户名、邮箱或二者重复时，返回 [409/UNIQUE_CONFLICT 及对应字段提示](#user-unique-conflict)。

---

<a id="api-19"></a>

#### 1.4.4 修改用户资料及状态

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/users/{userId}` |
| 接口描述 | 修改用户资料及状态 |
| Content-Type | application/json |
| 访问要求 | 后台：`sys:user:update` |
| 并发校验 | 必填 If-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `nickname` | string | 是 | 1..64 字符 |
| `email` | string&#124;null | 是，可 null | 合法邮箱，≤254 字符，唯一 |
| `avatar` | string&#124;null | 是，可 null | 仅外部 http(s) URL |
| `intro` | string&#124;null | 是，可 null | ≤500 字符 |
| `status` | integer | 是 | 0/1；禁用撤销全部会话 |

##### 请求示例

```http
PUT /api/v1/admin/users/12
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"nickname":"落叶","email":null,"avatar":null,"intro":null,"status":1}
```

##### 响应参数

`data`：[UserAdmin](#response-useradmin)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "userId": "12", "username": "leaf", "nickname": "落叶", "email": null, "avatar": null, "intro": null,
    "status": 1
  },
  "errors": []
}
```

不能修改 username 或密码；禁用撤销全部会话，重启用不恢复旧 Token。

---

<a id="api-20"></a>

#### 1.4.5 分配用户角色

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/users/{userId}/roles` |
| 接口描述 | 分配用户角色 |
| Content-Type | application/json |
| 访问要求 | 后台：`sys:user:assignRole` |
| 并发校验 | 必填 If-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `roleIds` | ID[] | 是 | 0..100 个，不重复、存在且启用；[] 清空；整体替换 |

##### 请求示例

```http
PUT /api/v1/admin/users/12/roles
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"roleIds":["3"]}
```

##### 响应参数

`data`：[UserAdmin](#response-useradmin)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "userId": "12", "username": "leaf", "nickname": "落叶", "email": null, "avatar": null, "intro": null,
    "status": 1
  },
  "errors": []
}
```

整体替换，不接受隐藏的增量追加；If-Revision 来自该用户详情，受最后管理员保护。集合中的角色必须全部存在且启用；任一项无效整笔返回 400。角色实际变化后撤销目标全部会话，Redis 失败则回滚关联替换并返回 503；同一集合的顺序不同不算变更，保持版本和会话。

---

<a id="group-4"></a>

<a id="admin-group-5"></a>

### 1.5 角色与授权管理

<a id="api-21"></a>

#### 1.5.1 分页查询角色

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/roles` |
| 接口描述 | 分页查询角色 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`sys:role:query` |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `status` | integer | 否 | 可选，0/1；含义见该业务状态约定 |

##### 请求示例

```http
GET /api/v1/admin/roles?page=1&pageSize=10
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：分页对象，`records[]` 为 [Role](#response-role)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "roleId": "4", "roleCode": "editor", "roleName": "内容编辑", "description": null, "dataScope": "SELF",
        "status": 1
      }
    ]
  },
  "errors": []
}
```

按 roleId ASC。keyword 去首尾空白后按 roleCode、roleName 字面包含匹配，遵循对应数据库列的字符排序规则；`%`、`_`、反斜杠均不作为 SQL 通配符。

---

<a id="api-22"></a>

#### 1.5.2 查询角色详情

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/roles/{roleId}` |
| 接口描述 | 查询角色详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`sys:role:query` |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `roleId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/admin/roles/4
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[Role](#response-role)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "roleId": "4", "roleCode": "editor", "roleName": "内容编辑", "description": null, "dataScope": "SELF",
    "status": 1
  },
  "errors": []
}
```

sys:role:query

---

<a id="api-23"></a>

#### 1.5.3 创建角色

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/roles` |
| 接口描述 | 创建角色 |
| Content-Type | application/json |
| 访问要求 | 后台：`sys:role:create` |
| 成功状态 | 201 Created |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `roleCode` | string | 是 | [a-z][a-z0-9_]{0,63}，唯一且创建后不可变 |
| `roleName` | string | 是 | 1..64 字符 |
| `description` | string&#124;null | 否 | ≤255 字符，默认 null |
| `dataScope` | string | 否 | ALL/SELF，默认 SELF |
| `status` | integer | 否 | 0/1，默认 1 |

##### 请求示例

```http
POST /api/v1/admin/roles
Authorization: Bearer <site-token>
Content-Type: application/json

{"roleCode":"editor","roleName":"内容编辑","description":null,"dataScope":"SELF","status":1}
```

##### 响应参数

`data`：[Role](#response-role)。

##### 响应示例

```http
Location: /api/v1/admin/roles/4
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "roleId": "4", "roleCode": "editor", "roleName": "内容编辑", "description": null, "dataScope": "SELF",
    "status": 1
  },
  "errors": []
}
```

初始无菜单、无权限；分配角色不隐式授予未配置的接口权限。

---

<a id="api-24"></a>

#### 1.5.4 修改角色

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/roles/{roleId}` |
| 接口描述 | 修改角色 |
| Content-Type | application/json |
| 访问要求 | 后台：`sys:role:update` |
| 并发校验 | 必填 If-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `roleId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `roleName` | string | 是 | 1..64 字符 |
| `description` | string&#124;null | 是，可 null | ≤255 字符，默认 null |
| `dataScope` | string | 是 | ALL/SELF，默认 SELF |
| `status` | integer | 是 | 0/1，默认 1 |

##### 请求示例

```http
PUT /api/v1/admin/roles/4
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"roleName":"内容编辑","description":null,"dataScope":"SELF","status":1}
```

##### 响应参数

`data`：[Role](#response-role)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "roleId": "4", "roleCode": "editor", "roleName": "内容编辑", "description": null, "dataScope": "SELF",
    "status": 1
  },
  "errors": []
}
```

roleCode 不可改；dataScope 或 status 实际变化时撤销全部关联用户的所有会话（包含禁用用户），角色禁用后保留权限与菜单关联，重新启用不恢复旧 Token。仅修改 roleName/description 不撤销会话。管理员角色仍须满足最后有效管理员保护，不能缩为 SELF 或停用。

---


<a id="api-26"></a>

#### 1.5.6 配置角色权限与菜单

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/roles/{roleId}/grants` |
| 接口描述 | 配置角色权限与菜单 |
| Content-Type | application/json |
| 访问要求 | 后台：`sys:role:grant` |
| 并发校验 | 必填 If-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `roleId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `permissionIds` | ID[] | 是 | ≤1000 个，不重复；整体替换，权限存在且启用 |
| `menuIds` | ID[] | 是 | ≤1000 个，不重复；存在且启用，自动补齐祖先；页面/按钮权限须显式列在 permissionIds |

##### 请求示例

```http
PUT /api/v1/admin/roles/4/grants
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"permissionIds":["1","10","11"],"menuIds":["110","1011"]}
```

##### 响应参数

`data`：[Role](#response-role)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "roleId": "4", "roleCode": "editor", "roleName": "内容编辑", "description": null, "dataScope": "SELF",
    "status": 1
  },
  "errors": []
}
```

两类授权同事务完整替换；menuIds 补齐祖先，最终集合升序返回。选择的节点及祖先均须存在、启用且符合父类型和无环约束；页面/按钮所需权限必须显式列入 permissionIds 并启用，不静默扩大授权。permissionIds 可包含无菜单的独立接口权限，[] 可清空；任一无效整笔返回 400/VALIDATION_ERROR。最终集合实际变化后撤销全部关联用户的所有会话，Redis 失败则回滚两个集合并返回 503；输入顺序或祖先显式/自动补齐不同但最终集合相同，保留版本及会话。admin 授权受最后有效管理员保护。

---

<a id="group-5"></a>

<a id="admin-group-6"></a>

### 1.6 权限字典管理

<a id="api-27"></a>

#### 1.6.1 分页查询权限字典

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/permissions` |
| 接口描述 | 分页查询权限字典 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`sys:permission:query` |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `module` | string | 否 | 可选，[a-z][a-z0-9_]{0,31} |
| `status` | integer | 否 | 可选，0/1；含义见该业务状态约定 |

##### 请求示例

```http
GET /api/v1/admin/permissions?page=1&pageSize=10
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：分页对象，`records[]` 为 [Permission](#response-permission)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "permissionId": "114", "permissionCode": "blog:article:export", "permissionName": "导出文章",
        "module": "blog", "description": null, "status": 1
      }
    ]
  },
  "errors": []
}
```

按 permissionId ASC。keyword 去首尾空白后按 permissionCode、permissionName 字面包含匹配，遵循对应列的数据库排序规则；`%`、`_`、反斜杠均不作为 SQL 通配符。module 精确匹配，所有过滤 AND 组合；列表与总数使用相同条件，包含禁用项。

---

<a id="api-28"></a>

#### 1.6.2 查询权限详情

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/permissions/{permissionId}` |
| 接口描述 | 查询权限详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`sys:permission:query` |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `permissionId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/admin/permissions/114
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[Permission](#response-permission)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "permissionId": "114", "permissionCode": "blog:article:export", "permissionName": "导出文章",
    "module": "blog", "description": null, "status": 1
  },
  "errors": []
}
```

sys:permission:query

---

<a id="api-29"></a>

#### 1.6.3 创建权限字典项

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/permissions` |
| 接口描述 | 创建权限字典项 |
| Content-Type | application/json |
| 访问要求 | 后台：`sys:permission:create` |
| 成功状态 | 201 Created |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `permissionCode` | string | 是 | ≤100 字符；模块:资源:动作，允许 admin:access 例外，创建后不可变 |
| `permissionName` | string | 是 | 1..64 字符 |
| `module` | string | 是 | [a-z][a-z0-9_]{0,31} |
| `description` | string&#124;null | 否 | ≤255 字符，默认 null |
| `status` | integer | 否 | 0/1，默认 1 |

##### 请求示例

```http
POST /api/v1/admin/permissions
Authorization: Bearer <site-token>
Content-Type: application/json

{"permissionCode":"blog:article:export","permissionName":"导出文章","module":"blog","description":null,"status":1}
```

##### 响应参数

`data`：[Permission](#response-permission)。

##### 响应示例

```http
Location: /api/v1/admin/permissions/114
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "permissionId": "114", "permissionCode": "blog:article:export", "permissionName": "导出文章",
    "module": "blog", "description": null, "status": 1
  },
  "errors": []
}
```

新增字典不会自动建立路由能力，也不自动授予 admin。示例 export 是自定义字典项，不表示已有导出 API。

---

<a id="api-30"></a>

#### 1.6.4 修改权限字典项

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/permissions/{permissionId}` |
| 接口描述 | 修改权限字典项 |
| Content-Type | application/json |
| 访问要求 | 后台：`sys:permission:update` |
| 并发校验 | 必填 If-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `permissionId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `permissionName` | string | 是 | 1..64 字符 |
| `module` | string | 是 | [a-z][a-z0-9_]{0,31} |
| `description` | string&#124;null | 是，可 null | ≤255 字符，默认 null |
| `status` | integer | 是 | 0/1，默认 1 |

##### 请求示例

```http
PUT /api/v1/admin/permissions/114
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"permissionName":"导出文章","module":"blog","description":null,"status":1}
```

##### 响应参数

`data`：[Permission](#response-permission)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "permissionId": "114", "permissionCode": "blog:article:export", "permissionName": "导出文章",
    "module": "blog", "description": null, "status": 1
  },
  "errors": []
}
```

permissionCode 创建后不可改，PUT 不接受该字段。仅名称、模块或说明变化不撤销会话；status 实际变化（停用或重新启用）撤销经任意角色关联该权限的全部用户的所有会话，包含禁用用户、禁用角色的关联，同一用户去重。停用保留 sys_role_permission 和菜单门禁引用；实时授权和菜单查询排除禁用权限，重新启用不恢复旧 Token。同值 PUT 仍须校验版本，业务值相同可保留版本和会话。

写事务复用固定 sys_role(role_id=1) 保护锁，再按 ID 升序锁操作者及全部受影响用户，最后锁目标权限；锁内复核原 Token、当前启用身份及双权限，然后校验版本。固定管理员保护权限停用受 LAST_ADMIN 约束。会话撤销成功后才提交数据库，Redis 失败返回 503 并回滚资料、状态；授权和菜单每次实时读库，无旧授权缓存。

---


<a id="admin-group-7"></a>

### 1.7 文章与附件

<a id="api-40"></a>

#### 1.7.1 分页查询后台文章

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/articles` |
| 接口描述 | 分页查询后台文章 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:article:query` |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `categoryId` | ID | 否 | 可选，分类筛选 |
| `tagId` | ID | 否 | 可选，标签筛选 |
| `authorId` | ID | 否 | 可选，作者筛选，不扩大 SELF |
| `status` | integer | 否 | 可选，0/1/2；含义见该业务状态约定 |
| `visibility` | integer | 否 | 可选，1 公开、2 私密 |
| `trash` | integer | 否 | 可选，0 正常、1 回收站，默认 0 |

##### 请求示例

```http
GET /api/v1/admin/articles?page=1&pageSize=10
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：分页对象，`records[]` 为 [ArticleAdminSummary](#response-articleadminsummary)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "articleId": "101", "authorId": "12",
        "author": {
          "userId": "12", "nickname": "落叶"
        },
        "category": null, "title": "第一片落叶", "slug": "first-leaf", "status": 0
      }
    ]
  },
  "errors": []
}
```

updateTime DESC,articleId DESC；trash 默认 0。SELF 强制本人，authorId 不能扩大范围。

---

<a id="api-41"></a>

#### 1.7.2 查询后台文章详情

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/articles/{articleId}` |
| 接口描述 | 查询后台文章详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:article:query` |
| 并发校验 | 返回 X-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/admin/articles/101
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[ArticleAdminDetail](#response-articleadmindetail)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "articleId": "101", "authorId": "12",
    "author": {
      "userId": "12", "nickname": "落叶"
    },
    "category": null, "title": "第一片落叶", "slug": "first-leaf", "status": 0
  },
  "errors": []
}
```

含回收站及 Markdown；关联与正文属于同一聚合版本。

---

<a id="api-42"></a>

#### 1.7.3 创建草稿文章

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/articles` |
| 接口描述 | 创建草稿文章 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:article:create` |
| 成功状态 | 201 Created |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `title` | string | 是 | 1..200 字符 |
| `slug` | string | 是 | ≤200，ASCII 连字符分词、区分大小写唯一；回收站占用 |
| `summary` | string | 否 | 0..500 字符，默认空串，不可 null |
| `categoryId` | ID&#124;null | 否 | 默认 null，非 null 必须存在 |
| `coverUrl` | string&#124;null | 否 | URL ≤2048，默认 null；内部文件只能引用当前文章上下文，创建时不可指定未知文章 ID |
| `articleType` | integer | 否 | 1 原创、2 转载、3 翻译，默认 1 |
| `originalUrl` | string&#124;null | 否 | 默认 null；转载/翻译必须提供合法 URL |
| `visibility` | integer | 否 | 1 公开、2 私密，默认 1 |
| `isTop` | integer | 否 | 0/1，默认 0 |
| `allowComment` | integer | 否 | 0/1，默认 1 |
| `markdownContent` | string | 是 | UTF-8 ≤1MiB；草稿可空串，发布须非空白；拒绝 htmlContent |
| `tagIds` | ID[] | 否 | ≤20 个，不重复且全部存在，默认 [] |

##### 请求示例

```http
POST /api/v1/admin/articles
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "title": "第一片落叶", "slug": "first-leaf", "summary": "记录与分享", "categoryId": null, "coverUrl": null,
  "articleType": 1, "originalUrl": null, "visibility": 1, "isTop": 0, "allowComment": 1,
  "markdownContent": "# 第一片落叶\n\n你好。",
  "tagIds": []
}
```

##### 响应参数

`data`：[ArticleAdminDetail](#response-articleadmindetail)。

##### 响应示例

```http
Location: /api/v1/admin/articles/101
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "articleId": "101", "authorId": "12",
    "author": {
      "userId": "12", "nickname": "落叶"
    },
    "category": null, "title": "第一片落叶", "slug": "first-leaf", "status": 0
  },
  "errors": []
}
```

作者固定当前用户，status 固定 0；文章、正文、标签同事务。创建并取得 ID 后再添加本站附件引用。

创建成功返回详情 Location 与当前聚合 X-Revision；封面中的等价绝对本站附件 URL 保存为规范公开相对路径。后台详情的 coverUrl 也保留此规范写入值，HTML 内的本站附件属性则转换为后台文章上下文；后台预览封面需使用对应后台上下文并以 Bearer fetch 获取，不能向图片 URL 添加 Token。

---

<a id="api-43"></a>

#### 1.7.4 编辑文章

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/articles/{articleId}` |
| 接口描述 | 编辑文章 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:article:update` |
| 并发校验 | 必填 If-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `title` | string | 是 | 1..200 字符 |
| `slug` | string | 是 | ≤200，ASCII 连字符分词、区分大小写唯一；回收站占用 |
| `summary` | string | 是 | 0..500 字符，默认空串，不可 null |
| `categoryId` | ID&#124;null | 是，可 null | 默认 null，非 null 必须存在 |
| `coverUrl` | string&#124;null | 是，可 null | URL ≤2048，默认 null；内部文件只能引用当前文章上下文，创建时不可指定未知文章 ID |
| `articleType` | integer | 是 | 1 原创、2 转载、3 翻译，默认 1 |
| `originalUrl` | string&#124;null | 是，可 null | 默认 null；转载/翻译必须提供合法 URL |
| `visibility` | integer | 是 | 1 公开、2 私密，默认 1 |
| `isTop` | integer | 是 | 0/1，默认 0 |
| `allowComment` | integer | 是 | 0/1，默认 1 |
| `markdownContent` | string | 是 | UTF-8 ≤1MiB；草稿可空串，发布须非空白；拒绝 htmlContent |
| `tagIds` | ID[] | 是 | ≤20 个，不重复且全部存在，默认 [] |

##### 请求示例

```http
PUT /api/v1/admin/articles/101
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "title": "第一片落叶", "slug": "first-leaf", "summary": "记录与分享", "categoryId": null, "coverUrl": null,
  "articleType": 1, "originalUrl": null, "visibility": 1, "isTop": 0, "allowComment": 1,
  "markdownContent": "# 第一片落叶\n\n你好。",
  "tagIds": []
}
```

##### 响应参数

`data`：[ArticleAdminDetail](#response-articleadmindetail)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "articleId": "101", "authorId": "12",
    "author": {
      "userId": "12", "nickname": "落叶"
    },
    "category": null, "title": "第一片落叶", "slug": "first-leaf", "status": 0
  },
  "errors": []
}
```

回收站不可编辑；当前已发布时还须 publish 权限及其数据范围。拒绝 status/authorId/publishTime/htmlContent 等服务端字段。

---




<a id="api-86"></a>

#### 1.7.8 读取后台文章附件

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/articles/{articleId}/files/{fileId}/content` |
| 接口描述 | 读取后台文章附件 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:article:query` + `blog:file:query` |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `fileId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/admin/articles/101/files/42/content
Authorization: Bearer <site-token>
```

##### 响应参数

| 字段/响应头 | 类型 | 说明 |
| --- | --- | --- |
| 响应体 | binary | PNG/JPEG 原始字节，不是 Result |
| `Content-Type` | string | 服务端确认的 image/png 或 image/jpeg |
| `Content-Length` | integer | 实际文件字节数 |
| `Content-Disposition` | string | inline；filename* 使用安全 UTF-8 编码 |
| `X-Content-Type-Options` | string | nosniff |
| `Cache-Control` | string | no-store；下架/撤销引用后重新鉴权 |

##### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: image/png
Content-Length: 2048
Content-Disposition: inline; filename*=UTF-8''leaf.png
X-Content-Type-Options: nosniff
Cache-Control: no-store

<PNG binary>
```

同时检查文章 query 与文件 query 及各自范围；文章须实际引用。文章作者身份不能代替跨用户文件授权。

---


#### 22.8 实施说明

本章 6 个新路由已计入 159 个已实现接口，authorId 为既有路由扩展。开发范围与验收条件见 [补充业务验收](API_CONTRACT.md#planned-acceptance)，实际验证记录见[后端运行说明](后端运行说明.md)。

---

<a id="admin-group-8"></a>

### 1.8 分类与标签

<a id="api-48"></a>

#### 1.8.1 分页查询后台分类

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/categories` |
| 接口描述 | 分页查询后台分类 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:category:query` |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `status` | integer | 否 | 可选，0/1；含义见该业务状态约定 |

##### 请求示例

```http
GET /api/v1/admin/categories?page=1&pageSize=10
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：分页对象，`records[]` 为 [Category](#response-category)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "categoryId": "1", "categoryName": "技术", "slug": "tech", "description": null, "sortOrder": 0,
        "status": 1
      }
    ]
  },
  "errors": []
}
```

sortOrder ASC,categoryId ASC；全局字典，不使用 SELF 本人过滤。keyword 去首尾空白后按 categoryName 字面包含匹配，status 精确匹配；所有条件 AND 组合，总数与列表使用相同条件。

---

<a id="api-49"></a>

#### 1.8.2 查询分类详情

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/categories/{categoryId}` |
| 接口描述 | 查询分类详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:category:query` |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `categoryId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/admin/categories/1
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[Category](#response-category)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "categoryId": "1", "categoryName": "技术", "slug": "tech", "description": null, "sortOrder": 0, "status": 1
  },
  "errors": []
}
```

后台(blog:category:query)

---

<a id="api-50"></a>

#### 1.8.3 创建分类

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/categories` |
| 接口描述 | 创建分类 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:category:create` |
| 成功状态 | 201 Created |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `categoryName` | string | 是 | 1..64，按库排序规则唯一 |
| `slug` | string | 是 | ASCII slug ≤100，区分大小写唯一 |
| `description` | string&#124;null | 否 | ≤255，默认 null |
| `sortOrder` | integer | 否 | int32，默认 0 |
| `status` | integer | 否 | 0/1，默认 1 |

##### 请求示例

```http
POST /api/v1/admin/categories
Authorization: Bearer <site-token>
Content-Type: application/json

{"categoryName":"技术","slug":"tech","description":null,"sortOrder":0,"status":1}
```

##### 响应参数

`data`：[Category](#response-category)。

##### 响应示例

```http
Location: /api/v1/admin/categories/1
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "categoryId": "1", "categoryName": "技术", "slug": "tech", "description": null, "sortOrder": 0, "status": 1
  },
  "errors": []
}
```

后台(blog:category:create)

---

<a id="api-51"></a>

#### 1.8.4 修改分类

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/categories/{categoryId}` |
| 接口描述 | 修改分类 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:category:update` |
| 并发校验 | 必填 If-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `categoryId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `categoryName` | string | 是 | 1..64，按库排序规则唯一 |
| `slug` | string | 是 | ASCII slug ≤100，区分大小写唯一 |
| `description` | string&#124;null | 是，可 null | ≤255，默认 null |
| `sortOrder` | integer | 是 | int32，默认 0 |
| `status` | integer | 是 | 0/1，默认 1 |

##### 请求示例

```http
PUT /api/v1/admin/categories/1
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"categoryName":"技术","slug":"tech","description":null,"sortOrder":0,"status":1}
```

##### 响应参数

`data`：[Category](#response-category)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "categoryId": "1", "categoryName": "技术", "slug": "tech", "description": null, "sortOrder": 0, "status": 1
  },
  "errors": []
}
```

后台(blog:category:update)

---


<a id="api-54"></a>

#### 1.8.6 分页查询后台标签

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/tags` |
| 接口描述 | 分页查询后台标签 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:tag:query` |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |

##### 请求示例

```http
GET /api/v1/admin/tags?page=1&pageSize=10
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：分页对象，`records[]` 为 [Tag](#response-tag)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "tagId": "1", "tagName": "Java", "slug": "java", "createTime": "2026-10-29T02:00:00.000Z",
        "updateTime": "2026-10-29T02:00:00.000Z"
      }
    ]
  },
  "errors": []
}
```

tagId ASC；全局字典，不使用 SELF 本人过滤。keyword 去首尾空白后按 tagName 字面包含匹配，总数与列表使用相同条件。公开标签列表不要求已关联文章，公开分类列表也不要求已存在文章。

---

<a id="api-55"></a>

#### 1.8.7 查询标签详情

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/tags/{tagId}` |
| 接口描述 | 查询标签详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:tag:query` |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `tagId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/admin/tags/1
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[Tag](#response-tag)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "tagId": "1", "tagName": "Java", "slug": "java", "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

后台(blog:tag:query)

---

<a id="api-56"></a>

#### 1.8.8 创建标签

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/tags` |
| 接口描述 | 创建标签 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:tag:create` |
| 成功状态 | 201 Created |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `tagName` | string | 是 | 1..64，按库排序规则唯一 |
| `slug` | string | 是 | ASCII slug ≤100，区分大小写唯一 |

##### 请求示例

```http
POST /api/v1/admin/tags
Authorization: Bearer <site-token>
Content-Type: application/json

{"tagName":"Java","slug":"java"}
```

##### 响应参数

`data`：[Tag](#response-tag)。

##### 响应示例

```http
Location: /api/v1/admin/tags/1
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "tagId": "1", "tagName": "Java", "slug": "java", "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

后台(blog:tag:create)

---

<a id="api-57"></a>

#### 1.8.9 修改标签

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/tags/{tagId}` |
| 接口描述 | 修改标签 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:tag:update` |
| 并发校验 | 必填 If-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `tagId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `tagName` | string | 是 | 1..64，按库排序规则唯一 |
| `slug` | string | 是 | ASCII slug ≤100，区分大小写唯一 |

##### 请求示例

```http
PUT /api/v1/admin/tags/1
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"tagName":"Java","slug":"java"}
```

##### 响应参数

`data`：[Tag](#response-tag)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "tagId": "1", "tagName": "Java", "slug": "java", "createTime": "2026-10-29T02:00:00.000Z",
    "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

后台(blog:tag:update)

---


<a id="admin-group-9"></a>

### 1.9 评论管理

<a id="api-63"></a>

#### 1.9.1 分页查询后台评论

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/comments` |
| 接口描述 | 分页查询后台评论 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:comment:query` |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..50 |
| `articleId` | ID | 否 | 可选，文章筛选 |
| `userId` | ID | 否 | 可选，评论者筛选，不扩大评论管理范围 |
| `status` | integer | 否 | 可选，0/1/2/3；含义见该业务状态约定 |

##### 请求示例

```http
GET /api/v1/admin/comments?page=1&pageSize=10
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：分页对象，`records[]` 为 [CommentAdmin](#response-commentadmin)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "commentId": "201", "articleId": "101", "userId": "12", "parentId": null, "content": "感谢分享。",
        "status": 0
      }
    ]
  },
  "errors": []
}
```

每页最多 50；SELF 为本人文章下的评论，非“评论本人”；createTime DESC,commentId DESC。

---

<a id="api-64"></a>

#### 1.9.2 查询后台评论详情

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/comments/{commentId}` |
| 接口描述 | 查询后台评论详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:comment:query` |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `commentId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/admin/comments/201
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[CommentAdmin](#response-commentadmin)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "commentId": "201", "articleId": "101", "userId": "12", "parentId": null, "content": "感谢分享。", "status": 0
  },
  "errors": []
}
```

可按权限查询待审/驳回内容；禁止通过前台列表混出私人内容。

---



<a id="admin-group-10"></a>

### 1.10 留言板与审核

<a id="api-115"></a>

#### 1.10.1 分页查询后台留言

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/messages` |
| 接口描述 | 分页查询后台留言 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:message:query`（ALL） |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |
| `status` | integer | 否 | 0/1/2/3 |
| `userId` | ID | 否 | 作者筛选 |
| `isSecret` | integer | 否 | 0/1 |
| `begin` | Time | 否 | createTime含下界 |
| `end` | Time | 否 | createTime不含上界 |

##### 请求示例

```http
GET /api/v1/admin/messages?page=1&pageSize=10
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：分页对象，`records[]` 为 [MessagePrivate](#response-messageprivate)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "messageId": "301", "parentId": null,
        "author": {
          "userId": "12", "nickname": "落叶", "avatar": null
        },
        "content": "你好，欢迎交流。", "htmlContent": "<p>你好，欢迎交流。</p>", "contentFormat": 0, "status": 1
      }
    ]
  },
  "errors": []
}
```

筛选AND组合；begin<end；createTime DESC,messageId DESC。后台可读私密内容，不向前台公开后台投影。

---

<a id="api-116"></a>

#### 1.10.2 查询后台留言详情

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/messages/{messageId}` |
| 接口描述 | 查询后台留言详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:message:query`（ALL） |
| 并发校验 | 返回 X-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `messageId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/admin/messages/301
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[MessagePrivate](#response-messageprivate)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "messageId": "301", "parentId": null,
    "author": {
      "userId": "12", "nickname": "落叶", "avatar": null
    },
    "content": "你好，欢迎交流。", "htmlContent": "<p>你好，欢迎交流。</p>", "contentFormat": 0, "status": 1
  },
  "errors": []
}
```

---



<a id="api-119"></a>

#### 1.10.5 后台回复留言

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/messages/{messageId}/replies` |
| 接口描述 | 后台回复留言 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:message:review`（ALL） |
| 成功状态 | 201 Created |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `X-Captcha-Id` | string | 是 | Header；见[验证码契约](#captcha) |
| `X-Captcha-Code` | string | 是 | Header；6 位数字，保留前导零 |
| `messageId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `content` | S(2000) | 是 | 去首尾空白后非空；Markdown保留原文内部空白 |
| `contentFormat` | integer | 是 | 0纯文本 1Markdown |
| `notifyReply` | integer | 是 | 0/1；见留言通知边界 |

##### 请求示例

```http
POST /api/v1/admin/messages/301/replies
Authorization: Bearer <site-token>
X-Captcha-Id: <captchaId>
X-Captcha-Code: 012345
Content-Type: application/json

{"content":"你好，欢迎交流。","contentFormat":0,"notifyReply":0}
```

##### 响应参数

`data`：[MessagePrivate](#response-messageprivate)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "messageId": "301", "parentId": null,
    "author": {
      "userId": "12", "nickname": "落叶", "avatar": null
    },
    "content": "你好，欢迎交流。", "htmlContent": "<p>你好，欢迎交流。</p>", "contentFormat": 0, "status": 1
  },
  "errors": []
}
```

使用现有审核权限+ALL，没有新增未定义权限码；作者为当前后台用户，父节点固定Path，继承整条私密范围，初始status=1。父链必须允许展示，待审/驳回/占位目标409；message.enabled=false时拒绝。不接受isAdminReply标志，展示管理员身份时实时核验有效角色。 创建成功不设置Location，通过响应ID及本人/后台详情入口定位。

---

<a id="admin-group-11"></a>

### 1.11 友链

<a id="api-71"></a>

#### 1.11.1 分页查询后台友链

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/links` |
| 接口描述 | 分页查询后台友链 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:link:query` |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `status` | integer | 否 | 可选，0/1/2；含义见该业务状态约定 |

##### 请求示例

```http
GET /api/v1/admin/links?page=1&pageSize=10
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：分页对象，`records[]` 为 [Link](#response-link)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "linkId": "1", "linkName": "示例友站", "url": "https://example.com", "avatar": null, "description": null,
        "sortOrder": 0, "status": 1
      }
    ]
  },
  "errors": []
}
```

sortOrder ASC,linkId ASC。

---

<a id="api-72"></a>

#### 1.11.2 查询友链详情

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/links/{linkId}` |
| 接口描述 | 查询友链详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:link:query` |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `linkId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/admin/links/1
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[Link](#response-link)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "linkId": "1", "linkName": "示例友站", "url": "https://example.com", "avatar": null, "description": null,
    "sortOrder": 0, "status": 1
  },
  "errors": []
}
```

后台(blog:link:query)

---

<a id="api-73"></a>

#### 1.11.3 创建友链

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/links` |
| 接口描述 | 创建友链 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:link:create` |
| 成功状态 | 201 Created |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `linkName` | string | 是 | 1..100，不要求唯一 |
| `url` | string | 是 | http(s) URL ≤2048，不要求唯一 |
| `avatar` | string&#124;null | 否 | 仅外部 http(s)，默认 null |
| `description` | string&#124;null | 否 | ≤255，默认 null |
| `sortOrder` | integer | 否 | int32，默认 0 |
| `status` | integer | 否 | 0 待审、1 展示、2 驳回/下架，默认 0 |

##### 请求示例

```http
POST /api/v1/admin/links
Authorization: Bearer <site-token>
Content-Type: application/json

{"linkName":"示例友站","url":"https://example.com","avatar":null,"description":null,"sortOrder":0,"status":0}
```

##### 响应参数

`data`：[Link](#response-link)。

##### 响应示例

```http
Location: /api/v1/admin/links/1
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "linkId": "1", "linkName": "示例友站", "url": "https://example.com", "avatar": null, "description": null,
    "sortOrder": 0, "status": 0
  },
  "errors": []
}
```

默认待审 status=0；URL 和名称均无唯一键，不承诺重复 URL 返回 409。

---

<a id="api-74"></a>

#### 1.11.4 修改或审核友链

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/links/{linkId}` |
| 接口描述 | 修改或审核友链 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:link:update` |
| 并发校验 | 必填 If-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `linkId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `linkName` | string | 是 | 1..100，不要求唯一 |
| `url` | string | 是 | http(s) URL ≤2048，不要求唯一 |
| `avatar` | string&#124;null | 是，可 null | 仅外部 http(s)，默认 null |
| `description` | string&#124;null | 是，可 null | ≤255，默认 null |
| `sortOrder` | integer | 是 | int32，默认 0 |
| `status` | integer | 是 | 0 待审、1 展示、2 驳回/下架，默认 0 |

##### 请求示例

```http
PUT /api/v1/admin/links/1
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"linkName":"示例友站","url":"https://example.com","avatar":null,"description":null,"sortOrder":0,"status":0}
```

##### 响应参数

`data`：[Link](#response-link)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "linkId": "1", "linkName": "示例友站", "url": "https://example.com", "avatar": null, "description": null,
    "sortOrder": 0, "status": 1
  },
  "errors": []
}
```

status 同模型更新；仅保存 URL，不由服务器抓取。

---


<a id="admin-group-12"></a>

### 1.12 站点配置

<a id="api-77"></a>

#### 1.12.1 查询后台配置列表

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/configs` |
| 接口描述 | 查询后台配置列表 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:config:query` |

##### 请求参数

无业务参数；鉴权见访问要求。

##### 请求示例

```http
GET /api/v1/admin/configs
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[Config](#response-config) 数组。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": [
    {
      "configKey": "site",
      "configValue": {
        "title": "DriftingLeaves", "description": "记录与分享", "icp": "", "publicSecurityRecord": "",
        "startDate": null
      },
      "description": "站点信息", "isPublic": 1, "updateTime": "2026-10-29T02:00:00.000Z"
    }
  ],
  "errors": []
}
```

只支持 site/comment/message，configKey ASC；不提供任意建键接口。

---

<a id="api-78"></a>

#### 1.12.2 查询配置详情

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/configs/{configKey}` |
| 接口描述 | 查询配置详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:config:query` |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `configKey` | string | 是 | Path；仅 site/comment/message，未知键 404 |

##### 请求示例

```http
GET /api/v1/admin/configs/site
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[Config](#response-config)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "configKey": "site",
    "configValue": {
      "title": "DriftingLeaves", "description": "记录与分享", "icp": "", "publicSecurityRecord": "",
      "startDate": null
    },
    "description": "站点信息", "isPublic": 1, "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

site/comment/message；未知键 404。

---

<a id="api-79"></a>

#### 1.12.3 修改配置

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/configs/{configKey}` |
| 接口描述 | 修改配置 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:config:update` |
| 并发校验 | 必填 If-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `configKey` | string | 是 | Path；仅 site/comment/message，未知键 404 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `configValue` | object | 是 | site: title/description/icp/publicSecurityRecord/startDate；comment或message: enabled/reviewRequired；完整白名单对象，UTF-8 ≤16KiB |
| `description` | string&#124;null | 是，可 null | ≤255 字符 |
| `isPublic` | integer | 是 | site 为 0/1；comment/message 必须 0 |

##### 请求示例

```http
PUT /api/v1/admin/configs/site
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "configValue": {
    "title": "DriftingLeaves", "description": "记录与分享", "icp": "", "publicSecurityRecord": "",
    "startDate": null
  },
  "description": "站点信息", "isPublic": 1
}
```

##### 响应参数

`data`：[Config](#response-config)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "configKey": "site",
    "configValue": {
      "title": "DriftingLeaves", "description": "记录与分享", "icp": "", "publicSecurityRecord": "",
      "startDate": null
    },
    "description": "站点信息", "isPublic": 1, "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

完整替换值；site={title,description,icp,publicSecurityRecord,startDate}，comment/message={enabled,reviewRequired}且isPublic必须0；未知内外字段400。

---

<a id="admin-group-13"></a>

### 1.13 文件管理

<a id="api-80"></a>

#### 1.13.1 分页查询文件元数据

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/files` |
| 接口描述 | 分页查询文件元数据 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:file:query` |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `uploaderId` | ID | 否 | 可选，上传者筛选，不扩大 SELF |
| `mimeType` | string | 否 | 可选 ≤100，按已确认 MIME 类型筛选 |

##### 请求示例

```http
GET /api/v1/admin/files?page=1&pageSize=10
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：分页对象，`records[]` 为 [File](#response-file)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "fileId": "42", "uploaderId": "12", "originalName": "leaf.png", "mimeType": "image/png",
        "sizeBytes": "2048", "storageType": "local"
      }
    ]
  },
  "errors": []
}
```

SELF 仅本人；createTime DESC,fileId DESC。keyword 按 originalName 字面包含，mimeType 按服务端确认的 MIME 精确匹配；uploaderId 筛选不能扩大 SELF。

---

<a id="api-81"></a>

#### 1.13.2 查询文件元数据

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/files/{fileId}` |
| 接口描述 | 查询文件元数据 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:file:query` |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fileId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/admin/files/42
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[File](#response-file)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "fileId": "42", "uploaderId": "12", "originalName": "leaf.png", "mimeType": "image/png",
    "sizeBytes": "2048", "storageType": "local"
  },
  "errors": []
}
```

元数据不可修改，不返回 X-Revision；previewUrl 为需鉴权的后台路径。

---

<a id="api-82"></a>

#### 1.13.3 上传图片

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/files` |
| 接口描述 | 上传图片 |
| Content-Type | multipart/form-data |
| 访问要求 | 后台：`blog:file:upload` |
| 成功状态 | 201 Created |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | binary | 是 | 唯一文件字段，单个 PNG/JPEG，非空且 ≤10MiB，不收路径/上传者 |

##### 请求示例

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

##### 响应参数

`data`：[File](#response-file)。

##### 响应示例

```http
Location: /api/v1/admin/files/42
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "fileId": "42", "uploaderId": "12", "originalName": "leaf.png", "mimeType": "image/png",
    "sizeBytes": "2048", "storageType": "oss"
  },
  "errors": []
}
```

单文件非空，PNG/JPEG ≤10MiB；multipart ≤11MiB，内容识别与扩展名一致；尺寸与存储补偿见第 13 节。每个请求按用户和可信 IP 共用每 60 秒 10 次窗口，仅计一次；超限 429/RATE_LIMITED 并返回 Retry-After。当前默认启用OSS，新上传为oss；需由环境提供OSS凭据，缺失时启动失败，旧对象不迁移，响应不返回直链。

---

<a id="api-83"></a>

#### 1.13.4 预览后台原文件

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/files/{fileId}/content` |
| 接口描述 | 预览后台原文件 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:file:query` |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fileId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/admin/files/42/content
Authorization: Bearer <site-token>
```

##### 响应参数

| 字段/响应头 | 类型 | 说明 |
| --- | --- | --- |
| 响应体 | binary | PNG/JPEG 原始字节，不是 Result |
| `Content-Type` | string | 服务端确认的 image/png 或 image/jpeg |
| `Content-Length` | integer | 实际文件字节数 |
| `Content-Disposition` | string | inline；filename* 使用安全 UTF-8 编码 |
| `X-Content-Type-Options` | string | nosniff |
| `Cache-Control` | string | no-store；下架/撤销引用后重新鉴权 |

##### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: image/png
Content-Length: 2048
Content-Disposition: inline; filename*=UTF-8''leaf.png
X-Content-Type-Options: nosniff
Cache-Control: no-store

<PNG binary>
```

需要文件查询权限及其范围；前端 Bearer fetch 后使用临时 object URL，不把 Token 拼到 img URL。

---


<a id="admin-group-14"></a>

### 1.14 主人资料与社交链接

<a id="api-89"></a>

#### 1.14.1 查询后台主页资料

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/home/profile` |
| 接口描述 | 查询后台主页资料 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`home:profile:query`（ALL） |
| 并发校验 | 返回 X-Revision |

##### 请求参数

无业务参数；鉴权见访问要求。

##### 请求示例

```http
GET /api/v1/admin/home/profile
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[HomeProfile](#response-homeprofile)，未配置/无记录时可为 null。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "profileId": "1", "userId": "12", "nickname": "落叶", "tagline": "记录与分享", "description": null,
    "avatar": null
  },
  "errors": []
}
```

固定profileId=1。展示资料与登录资料独立，只有publicEmail可公开；禁止从sys_user.email自动补全。头像和URL遵守公开资源约束。未配置时data=null，仍返回代表空资源的X-Revision。

---

<a id="api-90"></a>

#### 1.14.2 设置主页资料

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/home/profile` |
| 接口描述 | 设置主页资料 |
| Content-Type | application/json |
| 访问要求 | 后台：`home:profile:update`（ALL） |
| 并发校验 | 必填 If-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `userId` | ID | 是 | 站点主人账号；必须存在且启用，仅后台管理者可选择 |
| `nickname` | S(64) | 是 | 公开昵称 |
| `tagline` | S0(100) | 是 | 个人标签或一句话介绍 |
| `description` | S(500)? | 是，可null | 公开简介，null清空 |
| `avatar` | URL? | 是，可null | 公开头像，外部HTTP(S)资源 |
| `website` | URL? | 是，可null | 个人网站 |
| `publicEmail` | string&#124;null | 是，可null | ≤254，合法联系邮箱；明确自愿公开，不自动读取登录邮箱 |
| `githubUrl` | URL? | 是，可null | GitHub主页 |
| `location` | S(100)? | 是，可null | 自愿公开的所在地 |

##### 请求示例

```http
PUT /api/v1/admin/home/profile
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "userId": "12", "nickname": "落叶", "tagline": "记录与分享", "description": null, "avatar": null,
  "website": "https://example.test", "publicEmail": null, "githubUrl": null, "location": null
}
```

##### 响应参数

`data`：[HomeProfile](#response-homeprofile)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "profileId": "1", "userId": "12", "nickname": "落叶", "tagline": "记录与分享", "description": null,
    "avatar": null
  },
  "errors": []
}
```

固定profileId=1。展示资料与登录资料独立，只有publicEmail可公开；禁止从sys_user.email自动补全。头像和URL遵守公开资源约束。首次设置也通过本PUT，先GET取得空资源版本；同事务检查所选用户、创建或完整替换，始终200。

---

<a id="api-92"></a>

#### 1.14.3 查询后台社交链接

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/home/social-links` |
| 接口描述 | 查询后台社交链接 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`home:social:query`（ALL） |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |
| `keyword` | S(100) | 否 | 名称/标题字面包含，转义LIKE通配符 |
| `status` | integer | 否 | 可选0/1；缺失返回全部状态 |

##### 请求示例

```http
GET /api/v1/admin/home/social-links?page=1&pageSize=10
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：分页对象，`records[]` 为 [Social](#response-social)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "socialId": "21", "name": "GitHub", "icon": "github", "url": "https://github.com/example",
        "sortOrder": 0, "status": 1
      }
    ]
  },
  "errors": []
}
```

排序sortOrder ASC,socialId ASC；筛选AND组合。

---

<a id="api-93"></a>

#### 1.14.4 查询社交链接详情

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/home/social-links/{socialId}` |
| 接口描述 | 查询社交链接详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`home:social:query`（ALL） |
| 并发校验 | 返回 X-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `socialId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/admin/home/social-links/21
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[Social](#response-social)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "socialId": "21", "name": "GitHub", "icon": "github", "url": "https://github.com/example", "sortOrder": 0,
    "status": 1
  },
  "errors": []
}
```

---

<a id="api-94"></a>

#### 1.14.5 创建社交链接

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/home/social-links` |
| 接口描述 | 创建社交链接 |
| Content-Type | application/json |
| 访问要求 | 后台：`home:social:create`（ALL） |
| 成功状态 | 201 Created |
| 并发校验 | 返回 X-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | S(64) | 是 | 社交平台或链接名称 |
| `icon` | string&#124;null | 是，可null | ≤2048；外部HTTP(S)图标URL或前端注册图标键，禁止任意HTML |
| `url` | URL | 是 | 外部HTTP(S)目标链接，邮箱链接可使用经过校验的mailto:邮箱 |
| `sortOrder` | int32 | 是 | 越小越靠前 |
| `status` | integer | 是 | 0隐藏 1展示 |

##### 请求示例

```http
POST /api/v1/admin/home/social-links
Authorization: Bearer <site-token>
Content-Type: application/json

{"name":"GitHub","icon":"github","url":"https://github.com/example","sortOrder":0,"status":1}
```

##### 响应参数

`data`：[Social](#response-social)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

##### 响应示例

```http
X-Revision: "<current-revision>"
Location: /api/v1/admin/home/social-links/21
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "socialId": "21", "name": "GitHub", "icon": "github", "url": "https://github.com/example", "sortOrder": 0,
    "status": 1
  },
  "errors": []
}
```

名称与URL没有唯一键，不承诺重复时409。

---

<a id="api-95"></a>

#### 1.14.6 修改社交链接

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/home/social-links/{socialId}` |
| 接口描述 | 修改社交链接 |
| Content-Type | application/json |
| 访问要求 | 后台：`home:social:update`（ALL） |
| 并发校验 | 必填 If-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `socialId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `name` | S(64) | 是 | 社交平台或链接名称 |
| `icon` | string&#124;null | 是，可null | ≤2048；外部HTTP(S)图标URL或前端注册图标键，禁止任意HTML |
| `url` | URL | 是 | 外部HTTP(S)目标链接，邮箱链接可使用经过校验的mailto:邮箱 |
| `sortOrder` | int32 | 是 | 越小越靠前 |
| `status` | integer | 是 | 0隐藏 1展示 |

##### 请求示例

```http
PUT /api/v1/admin/home/social-links/21
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"name":"GitHub","icon":"github","url":"https://github.com/example","sortOrder":0,"status":1}
```

##### 响应参数

`data`：[Social](#response-social)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "socialId": "21", "name": "GitHub", "icon": "github", "url": "https://github.com/example", "sortOrder": 0,
    "status": 1
  },
  "errors": []
}
```

完整替换；隐藏后公开入口立即不返回。

---


<a id="admin-group-15"></a>

### 1.15 简历经历与技能

<a id="api-98"></a>

#### 1.15.1 查询后台简历经历

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/cv/experiences` |
| 接口描述 | 查询后台简历经历 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`cv:experience:query`（ALL） |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |
| `experienceType` | integer | 否 | 0/1/2 |
| `keyword` | S(100) | 否 | 名称/标题字面包含，转义LIKE通配符 |
| `status` | integer | 否 | 可选0/1；缺失返回全部状态 |

##### 请求示例

```http
GET /api/v1/admin/cv/experiences?page=1&pageSize=10
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：分页对象，`records[]` 为 [Experience](#response-experience)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "experienceId": "31", "experienceType": 0, "title": "示例大学", "subtitle": "计算机科学", "logoUrl": null,
        "content": "学习与项目实践", "status": 1
      }
    ]
  },
  "errors": []
}
```

按公开入口同样规则排序，但包含隐藏记录。

---

<a id="api-99"></a>

#### 1.15.2 查询简历经历详情

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/cv/experiences/{experienceId}` |
| 接口描述 | 查询简历经历详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`cv:experience:query`（ALL） |
| 并发校验 | 返回 X-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `experienceId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/admin/cv/experiences/31
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[Experience](#response-experience)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "experienceId": "31", "experienceType": 0, "title": "示例大学", "subtitle": "计算机科学", "logoUrl": null,
    "content": "学习与项目实践", "status": 1
  },
  "errors": []
}
```

---

<a id="api-100"></a>

#### 1.15.3 创建简历经历

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/cv/experiences` |
| 接口描述 | 创建简历经历 |
| Content-Type | application/json |
| 访问要求 | 后台：`cv:experience:create`（ALL） |
| 成功状态 | 201 Created |
| 并发校验 | 返回 X-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `experienceType` | integer | 是 | 0教育 1工作/实习 2项目 |
| `title` | S(200) | 是 | 学校、公司或项目名称 |
| `subtitle` | S(200)? | 是，可null | 专业、职位或项目角色 |
| `logoUrl` | URL? | 是，可null | 外部HTTP(S)图标URL |
| `content` | string | 是 | 非空Markdown，UTF-8最多65535字节，与MySQL TEXT容量一致 |
| `startDate` | date | 是 | 合法YYYY-MM-DD，1000..9999年 |
| `endDate` | date&#124;null | 是，可null | 结束日期不得早于开始；null表示至今 |
| `sortOrder` | int32 | 是 | 越小越靠前 |
| `status` | integer | 是 | 0隐藏 1展示 |

##### 请求示例

```http
POST /api/v1/admin/cv/experiences
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "experienceType": 0, "title": "示例大学", "subtitle": "计算机科学", "logoUrl": null, "content": "学习与项目实践",
  "startDate": "2022-09-01", "endDate": null, "sortOrder": 0, "status": 1
}
```

##### 响应参数

`data`：[Experience](#response-experience)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

##### 响应示例

```http
X-Revision: "<current-revision>"
Location: /api/v1/admin/cv/experiences/31
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "experienceId": "31", "experienceType": 0, "title": "示例大学", "subtitle": "计算机科学", "logoUrl": null,
    "content": "学习与项目实践", "status": 1
  },
  "errors": []
}
```

endDate=null表示至今；非空不得早于startDate。内容使用CommonMark及现有HTML清理规则。

---

<a id="api-101"></a>

#### 1.15.4 修改简历经历

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/cv/experiences/{experienceId}` |
| 接口描述 | 修改简历经历 |
| Content-Type | application/json |
| 访问要求 | 后台：`cv:experience:update`（ALL） |
| 并发校验 | 必填 If-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `experienceId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `experienceType` | integer | 是 | 0教育 1工作/实习 2项目 |
| `title` | S(200) | 是 | 学校、公司或项目名称 |
| `subtitle` | S(200)? | 是，可null | 专业、职位或项目角色 |
| `logoUrl` | URL? | 是，可null | 外部HTTP(S)图标URL |
| `content` | string | 是 | 非空Markdown，UTF-8最多65535字节，与MySQL TEXT容量一致 |
| `startDate` | date | 是 | 合法YYYY-MM-DD，1000..9999年 |
| `endDate` | date&#124;null | 是，可null | 结束日期不得早于开始；null表示至今 |
| `sortOrder` | int32 | 是 | 越小越靠前 |
| `status` | integer | 是 | 0隐藏 1展示 |

##### 请求示例

```http
PUT /api/v1/admin/cv/experiences/31
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "experienceType": 0, "title": "示例大学", "subtitle": "计算机科学", "logoUrl": null, "content": "学习与项目实践",
  "startDate": "2022-09-01", "endDate": null, "sortOrder": 0, "status": 1
}
```

##### 响应参数

`data`：[Experience](#response-experience)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "experienceId": "31", "experienceType": 0, "title": "示例大学", "subtitle": "计算机科学", "logoUrl": null,
    "content": "学习与项目实践", "status": 1
  },
  "errors": []
}
```

完整替换，允许切换经历类型；仅有SELF范围拒绝。

---


<a id="api-104"></a>

#### 1.15.6 查询后台简历技能

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/cv/skills` |
| 接口描述 | 查询后台简历技能 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`cv:skill:query`（ALL） |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |
| `keyword` | S(100) | 否 | 名称/标题字面包含，转义LIKE通配符 |
| `status` | integer | 否 | 可选0/1；缺失返回全部状态 |

##### 请求示例

```http
GET /api/v1/admin/cv/skills?page=1&pageSize=10
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：分页对象，`records[]` 为 [Skill](#response-skill)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "skillId": "41", "name": "Java", "description": "后端开发", "icon": null, "sortOrder": 0, "status": 1
      }
    ]
  },
  "errors": []
}
```

sortOrder ASC,skillId ASC。

---

<a id="api-105"></a>

#### 1.15.7 查询技能详情

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/cv/skills/{skillId}` |
| 接口描述 | 查询技能详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`cv:skill:query`（ALL） |
| 并发校验 | 返回 X-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `skillId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/admin/cv/skills/41
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[Skill](#response-skill)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "skillId": "41", "name": "Java", "description": "后端开发", "icon": null, "sortOrder": 0, "status": 1
  },
  "errors": []
}
```

---

<a id="api-106"></a>

#### 1.15.8 创建简历技能

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/cv/skills` |
| 接口描述 | 创建简历技能 |
| Content-Type | application/json |
| 访问要求 | 后台：`cv:skill:create`（ALL） |
| 成功状态 | 201 Created |
| 并发校验 | 返回 X-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | S(64) | 是 | 技能名称 |
| `description` | S(500)? | 是，可null | 技能描述 |
| `icon` | string&#124;null | 是，可null | ≤2048；外部HTTP(S)URL或注册图标键 |
| `sortOrder` | int32 | 是 | 越小越靠前 |
| `status` | integer | 是 | 0隐藏 1展示 |

##### 请求示例

```http
POST /api/v1/admin/cv/skills
Authorization: Bearer <site-token>
Content-Type: application/json

{"name":"Java","description":"后端开发","icon":null,"sortOrder":0,"status":1}
```

##### 响应参数

`data`：[Skill](#response-skill)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

##### 响应示例

```http
X-Revision: "<current-revision>"
Location: /api/v1/admin/cv/skills/41
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "skillId": "41", "name": "Java", "description": "后端开发", "icon": null, "sortOrder": 0, "status": 1
  },
  "errors": []
}
```

后台全局资源，必须检查本次权限的ALL范围；目标不存在404。

---

<a id="api-107"></a>

#### 1.15.9 修改简历技能

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/cv/skills/{skillId}` |
| 接口描述 | 修改简历技能 |
| Content-Type | application/json |
| 访问要求 | 后台：`cv:skill:update`（ALL） |
| 并发校验 | 必填 If-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `skillId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `name` | S(64) | 是 | 技能名称 |
| `description` | S(500)? | 是，可null | 技能描述 |
| `icon` | string&#124;null | 是，可null | ≤2048；外部HTTP(S)URL或注册图标键 |
| `sortOrder` | int32 | 是 | 越小越靠前 |
| `status` | integer | 是 | 0隐藏 1展示 |

##### 请求示例

```http
PUT /api/v1/admin/cv/skills/41
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"name":"Java","description":"后端开发","icon":null,"sortOrder":0,"status":1}
```

##### 响应参数

`data`：[Skill](#response-skill)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "skillId": "41", "name": "Java", "description": "后端开发", "icon": null, "sortOrder": 0, "status": 1
  },
  "errors": []
}
```

完整替换。图标仅白名单键或外部HTTP(S)资源，不接受任意SVG/HTML。

---


<a id="admin-group-16"></a>

### 1.16 背景音乐

<a id="api-121"></a>

#### 1.16.1 分页查询后台音乐

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/music` |
| 接口描述 | 分页查询后台音乐 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:music:query`（ALL） |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |
| `keyword` | S(100) | 否 | 标题或作者字面包含 |
| `status` | integer | 否 | 0/1，可选 |

##### 请求示例

```http
GET /api/v1/admin/music?page=1&pageSize=10
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：分页对象，`records[]` 为 [Music](#response-music)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "musicId": "51", "title": "秋日", "artist": null, "durationSeconds": 180, "coverUrl": null,
        "audioUrl": "https://example.test/audio.mp3", "status": 1
      }
    ]
  },
  "errors": []
}
```

keyword匹配title或artist，筛选AND组合；sortOrder ASC,musicId ASC。全站资源，需ALL。

---

<a id="api-122"></a>

#### 1.16.2 查询音乐详情

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/music/{musicId}` |
| 接口描述 | 查询音乐详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:music:query`（ALL） |
| 并发校验 | 返回 X-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `musicId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/admin/music/51
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[Music](#response-music)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "musicId": "51", "title": "秋日", "artist": null, "durationSeconds": 180, "coverUrl": null,
    "audioUrl": "https://example.test/audio.mp3", "status": 1
  },
  "errors": []
}
```

---

<a id="api-123"></a>

#### 1.16.3 创建音乐

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/music` |
| 接口描述 | 创建音乐 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:music:create`（ALL） |
| 成功状态 | 201 Created |
| 并发校验 | 返回 X-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `title` | S(200) | 是 | 曲目标题 |
| `artist` | S(200)? | 是，可null | 作者或歌手 |
| `durationSeconds` | integer&#124;null | 是，可null | 0..4294967295秒，未知为null |
| `coverUrl` | URL? | 是，可null | 外部HTTP(S)封面 |
| `audioUrl` | URL | 是 | 外部HTTP(S)音频URL；保存不触发服务端下载 |
| `lyricUrl` | URL? | 是，可null | 歌词URL；无歌词时为null |
| `lyricType` | string&#124;null | 是，可null | lrc/json/txt；必须与lyricUrl同时为空或同时非空 |
| `sortOrder` | int32 | 是 | 越小越靠前 |
| `status` | integer | 是 | 0隐藏 1展示 |

##### 请求示例

```http
POST /api/v1/admin/music
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "title": "秋日", "artist": null, "durationSeconds": 180, "coverUrl": null,
  "audioUrl": "https://example.test/audio.mp3", "lyricUrl": null, "lyricType": null, "sortOrder": 0,
  "status": 1
}
```

##### 响应参数

`data`：[Music](#response-music)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

##### 响应示例

```http
X-Revision: "<current-revision>"
Location: /api/v1/admin/music/51
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "musicId": "51", "title": "秋日", "artist": null, "durationSeconds": 180, "coverUrl": null,
    "audioUrl": "https://example.test/audio.mp3", "status": 1
  },
  "errors": []
}
```

lyricUrl与lyricType同时为空或同时非空；durationSeconds可为null。使用独立上传服务的外部资源时，运营者自行确保URL可公开播放，不接受本站私密附件路径。

---

<a id="api-124"></a>

#### 1.16.4 修改音乐

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/music/{musicId}` |
| 接口描述 | 修改音乐 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:music:update`（ALL） |
| 并发校验 | 必填 If-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `musicId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `title` | S(200) | 是 | 曲目标题 |
| `artist` | S(200)? | 是，可null | 作者或歌手 |
| `durationSeconds` | integer&#124;null | 是，可null | 0..4294967295秒，未知为null |
| `coverUrl` | URL? | 是，可null | 外部HTTP(S)封面 |
| `audioUrl` | URL | 是 | 外部HTTP(S)音频URL；保存不触发服务端下载 |
| `lyricUrl` | URL? | 是，可null | 歌词URL；无歌词时为null |
| `lyricType` | string&#124;null | 是，可null | lrc/json/txt；必须与lyricUrl同时为空或同时非空 |
| `sortOrder` | int32 | 是 | 越小越靠前 |
| `status` | integer | 是 | 0隐藏 1展示 |

##### 请求示例

```http
PUT /api/v1/admin/music/51
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "title": "秋日", "artist": null, "durationSeconds": 180, "coverUrl": null,
  "audioUrl": "https://example.test/audio.mp3", "lyricUrl": null, "lyricType": null, "sortOrder": 0,
  "status": 1
}
```

##### 响应参数

`data`：[Music](#response-music)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "musicId": "51", "title": "秋日", "artist": null, "durationSeconds": 180, "coverUrl": null,
    "audioUrl": "https://example.test/audio.mp3", "status": 1
  },
  "errors": []
}
```

完整替换；隐藏后立即从公开列表消失，不等于删除外部存储对象。

---


<a id="admin-group-17"></a>

### 1.17 邮件订阅管理

<a id="api-131"></a>

#### 1.17.1 分页查询后台邮件订阅

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/rss-subscriptions` |
| 接口描述 | 分页查询后台邮件订阅 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:rss:query`（ALL） |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |
| `email` | S(254) | 否 | 邮箱字面包含 |
| `userId` | ID | 否 | 所属用户 |
| `status` | integer | 否 | 0待验证 1已订阅 2已退订 |

##### 请求示例

```http
GET /api/v1/admin/rss-subscriptions?page=1&pageSize=10
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：分页对象，`records[]` 为 [SubscriptionAdmin](#response-subscriptionadmin)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "userId": "12", "subscriptionId": "401", "email": "leaf@example.test", "status": 0,
        "verifiedAt": null, "subscribeTime": null
      }
    ]
  },
  "errors": []
}
```

需要ALL；createTime DESC,subscriptionId DESC。无未分页的全量邮箱导出接口。

---

<a id="api-132"></a>

#### 1.17.2 查询邮件订阅详情

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/rss-subscriptions/{subscriptionId}` |
| 接口描述 | 查询邮件订阅详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:rss:query`（ALL） |
| 并发校验 | 返回 X-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `subscriptionId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/admin/rss-subscriptions/401
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[SubscriptionAdmin](#response-subscriptionadmin)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "userId": "12", "subscriptionId": "401", "email": "leaf@example.test", "status": 0, "verifiedAt": null,
    "subscribeTime": null
  },
  "errors": []
}
```

---



<a id="admin-group-18"></a>

### 1.18 访客管理

<a id="api-138"></a>

#### 1.18.1 分页查询访客

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/visitors` |
| 接口描述 | 分页查询访客 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:visitor:query`（ALL） |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |
| `country` | S(64) | 否 | 精确匹配 |
| `province` | S(64) | 否 | 精确匹配 |
| `city` | S(64) | 否 | 精确匹配 |
| `isBlocked` | integer | 否 | 可选0/1，对持久化标记筛选 |

##### 请求示例

```http
GET /api/v1/admin/visitors?page=1&pageSize=10
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：分页对象，`records[]` 为 [Visitor](#response-visitor)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "visitorId": "501", "fingerprint": "d681988a-cbd0-4ef6-bdec-f84bfa234e16", "ipAddress": "192.0.2.1",
        "userAgent": null, "country": null, "province": null
      }
    ]
  },
  "errors": []
}
```

需ALL；lastVisitTime DESC,visitorId DESC。仅后台返回统计标识、IP与UA，不公开原始访客清单。

---

<a id="api-139"></a>

#### 1.18.2 查询访客详情

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/visitors/{visitorId}` |
| 接口描述 | 查询访客详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:visitor:query`（ALL） |
| 并发校验 | 返回 X-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `visitorId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/admin/visitors/501
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[Visitor](#response-visitor)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "visitorId": "501", "fingerprint": "d681988a-cbd0-4ef6-bdec-f84bfa234e16", "ipAddress": "192.0.2.1",
    "userAgent": null, "country": null, "province": null
  },
  "errors": []
}
```

X-Revision仅覆盖isBlocked/blockedUntil，排除会持续变化的最近访问/IP/UA；详情仍返回当前统计字段。

---




<a id="admin-group-19"></a>

### 1.19 浏览记录管理

<a id="api-143"></a>

#### 1.19.1 分页查询浏览记录

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/page-views` |
| 接口描述 | 分页查询浏览记录 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:view:query`（ALL） |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |
| `visitorId` | ID | 否 | 统计访客筛选 |
| `userId` | ID | 否 | 采集时登录用户筛选 |
| `articleId` | ID | 否 | 文章筛选 |
| `siteCode` | string | 否 | home/blog/cv |
| `pagePath` | S(2048) | 否 | 规范路径精确匹配 |
| `referer` | S(2048) | 否 | 脱敏来源字面包含 |
| `begin` | Time | 否 | viewTime含下界 |
| `end` | Time | 否 | viewTime不含上界 |

##### 请求示例

```http
GET /api/v1/admin/page-views?page=1&pageSize=10
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：分页对象，`records[]` 为 [PageView](#response-pageview)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "viewId": "601", "visitorId": "501", "userId": null, "articleId": null, "siteCode": "home",
        "pagePath": "/"
      }
    ]
  },
  "errors": []
}
```

需ALL；筛选AND组合，begin<end；viewTime DESC,viewId DESC，不输出任何认证凭据。

---


<a id="admin-group-20"></a>

### 1.20 统计报表

<a id="api-146"></a>

#### 1.20.1 查询后台统计总览

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/statistics/overview` |
| 接口描述 | 查询后台统计总览 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:statistics:query`（ALL） |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `begin` | date | 否 | 可选；与end同时出现；UTC日期含下界 |
| `end` | date | 否 | 可选；UTC日期含当日，最多367天，end不得早于begin |
| `siteCode` | string | 否 | 可选home/blog/cv，缺失统计全部站点 |

##### 请求示例

```http
GET /api/v1/admin/statistics/overview
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[AdminStatistics](#response-adminstatistics)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "pageViewCount": "120", "visitorCount": "35", "newVisitorCount": "4", "articleCount": "15",
    "publishedArticleCount": "12", "commentCount": "18"
  },
  "errors": []
}
```

需ALL；begin/end均不传时为UTC今天；日期条件只影响访问类指标，内容与待审核计数始终是当前快照，不将二者混称同一时段新增。

---

<a id="api-147"></a>

#### 1.20.2 查询浏览趋势

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/statistics/page-views` |
| 接口描述 | 查询浏览趋势 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:statistics:query`（ALL） |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `begin` | date | 否 | 可选；与end同时出现；UTC日期含下界 |
| `end` | date | 否 | 可选；UTC日期含当日，最多367天，end不得早于begin |
| `siteCode` | string | 否 | 可选home/blog/cv，缺失统计全部站点 |

##### 请求示例

```http
GET /api/v1/admin/statistics/page-views
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[ViewTrend](#response-viewtrend) 数组。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":[{"date":"2026-10-30","pageViewCount":"8","visitorCount":"5"}],"errors":[]}
```

需ALL；按UTC日期ASC补全每一天，无数据为字符串0。begin/end默认UTC今天；站点筛选不改变日边界。

---

<a id="api-148"></a>

#### 1.20.3 查询新访客趋势

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/statistics/visitors` |
| 接口描述 | 查询新访客趋势 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:statistics:query`（ALL） |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `begin` | date | 否 | 可选；与end同时出现；UTC日期含下界 |
| `end` | date | 否 | 可选；UTC日期含当日，最多367天，end不得早于begin |
| `siteCode` | string | 否 | 可选home/blog/cv，缺失统计全部站点 |

##### 请求示例

```http
GET /api/v1/admin/statistics/visitors
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[VisitorTrend](#response-visitortrend) 数组。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": [
    {
      "date": "2026-10-30", "newVisitorCount": "2", "cumulativeNewVisitorCount": "4"
    }
  ],
  "errors": []
}
```

需ALL；访客首次访问按blog_visitor.first_visit_time统计，siteCode筛选要求该访客区间内确实访问该站。cumulativeNewVisitorCount从本区间begin开始累加，不伪装为历史累计UV。

---

<a id="api-149"></a>

#### 1.20.4 查询访客省份分布

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/statistics/provinces` |
| 接口描述 | 查询访客省份分布 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:statistics:query`（ALL） |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `begin` | date | 否 | 可选；与end同时出现；UTC日期含下界 |
| `end` | date | 否 | 可选；UTC日期含当日，最多367天，end不得早于begin |
| `siteCode` | string | 否 | 可选home/blog/cv，缺失统计全部站点 |

##### 请求示例

```http
GET /api/v1/admin/statistics/provinces
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[ProvinceMetric](#response-provincemetric) 数组。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":[{"province":null,"visitorCount":"3"}],"errors":[]}
```

需ALL；以区间内出现过的visitorId去重，按访客当前province分组，未知为null，不按每条PV重复计访客。visitorCount DESC,province ASC；不是地理位置历史快照。

---

<a id="api-150"></a>

#### 1.20.5 查询文章访问排行

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/statistics/articles` |
| 接口描述 | 查询文章访问排行 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:statistics:query`（ALL） |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `begin` | date | 否 | 可选；与end同时出现；UTC日期含下界 |
| `end` | date | 否 | 可选；UTC日期含当日，最多367天，end不得早于begin |
| `limit` | integer | 否 | 默认10，1..100 |

##### 请求示例

```http
GET /api/v1/admin/statistics/articles
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[ArticleViewMetric](#response-articleviewmetric) 数组。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": [
    {
      "articleId": "101", "title": "第一片落叶", "slug": "first-leaf", "pageViewCount": "10"
    }
  ],
  "errors": []
}
```

需ALL；只统计blog站点且articleId非NULL的明细，排除已回收文章；允许含当前草稿/私密文章的历史访问记录，仅后台可见。pageViewCount DESC,articleId DESC。该排行表示区间浏览明细，区别于第7.11节累计热度榜。

---

<a id="admin-group-21"></a>

### 1.21 操作日志

<a id="api-151"></a>

#### 1.21.1 分页查询操作日志

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/operation-logs` |
| 接口描述 | 分页查询操作日志 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`sys:log:query`（ALL） |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |
| `userId` | ID | 否 | 操作人筛选 |
| `operationType` | S(32) | 否 | ASCII类型精确匹配 |
| `operationTarget` | S(100) | 否 | ASCII模块精确匹配 |
| `targetId` | ID | 否 | 目标资源 |
| `result` | integer | 否 | 0失败 1成功 |
| `begin` | Time | 否 | operationTime含下界 |
| `end` | Time | 否 | operationTime不含上界 |

##### 请求示例

```http
GET /api/v1/admin/operation-logs?page=1&pageSize=10
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：分页对象，`records[]` 为 [OperationLog](#response-operationlog)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "logId": "701", "userId": "12", "operationType": "update", "operationTarget": "home_profile",
        "targetId": "1", "permissionCode": "home:profile:update"
      }
    ]
  },
  "errors": []
}
```

筛选AND组合；begin<end；operationTime DESC,logId DESC。列表不展开operationData，详情向具有sys:log:query及ALL范围的管理员展示已记录的完整业务字段。

---

<a id="api-152"></a>

#### 1.21.2 查询操作日志详情

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/operation-logs/{logId}` |
| 接口描述 | 查询操作日志详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`sys:log:query`（ALL） |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `logId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/admin/operation-logs/701
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[OperationLogDetail](#response-operationlogdetail)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "logId": "701", "userId": "12", "operationType": "update", "operationTarget": "home_profile",
    "targetId": "1", "permissionCode": "home:profile:update"
  },
  "errors": []
}
```

日志不可编辑，无X-Revision；不返回原始HTTP请求体、异常堆栈、SQL或任何凭据。

---


<a id="admin-batch"></a>

### 1.22 后台统一操作（单条与批量）

本节共 29 个统一入口：原有 26 项替换对应旧单条管理写路由，另新增用户批量启用、禁用和删除 3 项。一次提交 1 项即为单条操作，多项即为批量操作，使用相同的权限、版本、事务与结果协议；不保留旧路径别名。详情查询、创建、完整编辑、用户角色/角色 grants、本人设置及本人互动接口不在合并范围。源码依据与验收要求见[统一操作开发约定](API_CONTRACT.md#batch-contract)。

#### 公共请求与结果

全部入口要求有效 Bearer、启用账号、`admin:access` 和各接口列出的业务权限，使用 `Content-Type: application/json`、`Cache-Control: no-store`。不接受 Query、通用 action、筛选条件或“全选所有查询结果”；仅操作 Body 中明确选中的同类 ID。删除使用 POST 动作路径，避免在 DELETE 中携带批量 JSON；对应的旧单条管理写入口已移除，单个目标也使用此协议。

以下请求模型的字段均在 Body，除明确可 null 的字段外均必填。只允许对应模型声明的字段，嵌套对象同样拒绝未知字段。

| 请求模型 | 完整结构与约束 |
| --- | --- |
| `BatchRevisionRequest` | `{items:[{id:ID,revision:string}]}`；用于原操作要求版本的批量入口 |
| `BatchIdsRequest` | `{ids:ID[]}`；用于评论/留言分支删除、文件、浏览记录和日志清理，不引入版本要求 |
| `BatchStatusRequest` | `{items:[{id:ID,revision:string}],status:integer}`；所有目标采用同一 status，合法值见各接口 |
| `BatchBlockRequest` | `{items:[{id:ID,revision:string}],blockedUntil:Time或null}`；blockedUntil 必须显式出现，null 表示永久，否则须晚于锁内检查时的当前 UTC |

数组长度为 1..100，ID 必须为合法十进制字符串，不能为 null 或重复；非法 ID、空数组、超过 100 项或重复 ID 返回 `400/VALIDATION_ERROR`，不自动去重或截断。该上限只限制显式选中的目标数，不截断分支删除的后代。

`items[].revision` 原样使用各自单条详情 GET 的 X-Revision，包含引号；格式为双引号包裹的 64 位小写十六进制摘要，不接受 `*`。列表没有逐条版本，提交前须读取详情并保留用户确认时的版本。请求不发送 If-Revision；发送该头返回 400，不能以单一版本替代逐条版本。未要求版本的模型不接受 revision 字段。权限和范围通过后，缺失或 null 的逐条版本返回 428，格式错误返回 400，过期返回 412。

除文件批量删除外，全部采用**一个数据库事务整批提交**。先确认全部目标的权限、范围、存在性、版本和业务状态，再写入；任一失败，数据库变更和成功审计整批回滚，返回普通错误 Result，不返回部分成功。一个目标越出 SELF 范围或不存在均为 404，不披露具体越权目标。字段错误路径可使用 `items[0].id`、`ids[1]`；权限、版本和状态错误不返回目标详情。审计、会话撤销、Redis、缓存及邮件边界见[开发约定](API_CONTRACT.md#batch-contract)。

| 结果模型 | HTTP 与完整 data 结构 |
| --- | --- |
| `BatchDeleted` | HTTP 200，`data:null`；全部所选目标及应级联的数据已清理，或文章已移入回收站 |
| `BatchVersions` | HTTP 200，`data:{items:[{id:ID,revision:string}]}`；顺序与请求相同，恰好每个目标一项；revision 为写入后的业务版本，同目标无变化可保持原版本 |
| `BatchFileResults` | HTTP 200，`data:{results:[{id:ID,httpStatus:integer,code:string,msg:string}]}`；顺序与请求相同，每个目标恰好一项，成功为 httpStatus=200/code=OK/msg=成功；失败沿用既有错误码和脱敏提示 |

`BatchVersions` 不返回正文、邮箱、私密留言或用户资料；按需另查原详情。删除没有新版本。客户端不能把 HTTP 200 的文件批次直接显示为全部成功，必须检查 results 中每项的 code。

请求示例中的摘要仅演示 JSON 转义格式，实际调用必须取目标自己的详情版本。批量审核示例同样适用于留言，status=2 表示驳回：

```http
PUT /api/v1/admin/comments/batch-review
Authorization: Bearer <site-token>
Content-Type: application/json

{"items":[{"id":"301","revision":"\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\""},{"id":"302","revision":"\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\""}],"status":1}
```

```json
{"code":"OK","msg":"成功","data":{"items":[{"id":"301","revision":"\"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\""},{"id":"302","revision":"\"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd\""}]},"errors":[]}
```

原删除业务要求版本的资源使用如下请求，不附加 status：

```http
POST /api/v1/admin/categories/batch-delete
Authorization: Bearer <site-token>
Content-Type: application/json

{"items":[{"id":"101","revision":"\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\""}]}
```

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

不要求版本的分支清理使用 ID 数组；同时选中父子节点时按分支并集清理，具体规则见对应接口：

```http
POST /api/v1/admin/messages/batch-delete
Authorization: Bearer <site-token>
Content-Type: application/json

{"ids":["401","402"]}
```

批量封禁沿用 V2 的明确到期时间，不沿用老项目固定封禁一天的默认值：

```http
PUT /api/v1/admin/visitors/batch-block
Authorization: Bearer <site-token>
Content-Type: application/json

{"items":[{"id":"501","revision":"\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\""}],"blockedUntil":null}
```

普通整批错误示例：HTTP 412，全部数据库写入回滚，不自动获取新版本重试：

```json
{"code":"REVISION_CONFLICT","msg":"资料已修改，请重新获取","data":null,"errors":[]}
```

所有入口均沿用[错误码](#errors)：未认证 401，无权限或缺少规定的 ALL 为 403，不存在/范围外 404，状态或引用冲突 409，旧版本 412，缺版本 428，请求超限 413，限流 429，数据库/Redis/存储不可用 503；未预期异常 500。权限检查不因目标已处于同状态而省略。超时或响应丢失先重新查询确认；删除后重放返回 404，旧版本重放返回 412，不承诺通用幂等键。

<a id="api-44"></a>

<a id="batch-article-status"></a>

#### 1.22.1 批量发布或下架文章

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/articles/batch-status` |
| 业务权限 | `blog:article:publish`，逐篇检查 ALL/SELF |
| 请求 → 响应 | `BatchStatusRequest`（status=1 发布、2 下架）→ `BatchVersions` |

逐篇继承[既有业务规则](API_CONTRACT.md#model-12-4)的正文、来源、关联和文件可用性校验。回收站文章不得操作；草稿不能直接下架。首次发布各自写服务端发布时间，重发保留原首次时间；首次公开发布通知仅在整批提交后触发，不因同状态重放重复发送。

<a id="api-45"></a>

<a id="batch-article-recycle"></a>

#### 1.22.2 批量将文章移入回收站

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/articles/batch-recycle` |
| 业务权限 | `blog:article:delete`，逐篇检查 ALL/SELF |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

继承[既有业务规则](API_CONTRACT.md#model-12-4)，仅设置 deletedAt，保留原状态、正文、标签、slug、评论和点赞，不删除文件。同目标已回收的处理沿用单条接口，仍先校验版本，不扩大为永久删除。

<a id="api-46"></a>

<a id="batch-article-restore"></a>

#### 1.22.3 批量恢复回收站文章

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/articles/batch-restore` |
| 业务权限 | `blog:article:restore`，逐篇检查 ALL/SELF |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchVersions` |

全部目标必须在回收站，否则整批 409。继承[既有业务规则](API_CONTRACT.md#model-12-4)，统一恢复为下架 status=2、deletedAt=null，保留首次发布时间，不自动公开或发送发布通知。

<a id="api-159"></a>

<a id="batch-article-permanent"></a>

#### 1.22.4 批量永久删除回收站文章

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/articles/batch-permanent-delete` |
| 业务权限 | 同时具备 `blog:article:delete`、`blog:article:purge`；两项范围分别逐篇检查 |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

全部目标须先回收，不能混入正常文章。按[既有业务规则](API_CONTRACT.md#model-12-4)清理正文、标签关联、点赞及完整评论树；保留分类/标签字典、文件对象、历史审计和浏览明细，浏览关联 articleId 置 null。整批不可恢复，不能改成循环提交多个永久删除请求。

<a id="api-52"></a>

<a id="batch-category-delete"></a>

#### 1.22.5 批量删除分类

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/categories/batch-delete` |
| 业务权限 | `blog:category:delete`；全局字典，沿用原权限规则 |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

按[既有业务规则](API_CONTRACT.md#model-12-5)将所选分类下文章的 categoryId 置 null，不删除文章；关联变化使文章业务版本失效，并在整批提交后清理依赖分类的公开缓存。

<a id="api-58"></a>

<a id="batch-tag-delete"></a>

#### 1.22.6 批量删除标签

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/tags/batch-delete` |
| 业务权限 | `blog:tag:delete`；全局字典，沿用原权限规则 |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

按[既有业务规则](API_CONTRACT.md#model-12-5)清理相应文章标签关联，不删除文章或其他标签；关联集合变化使文章业务版本失效，缓存清理覆盖文章列表/详情等原有依赖。

<a id="api-65"></a>

<a id="batch-comment-review"></a>

#### 1.22.7 批量审核评论

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/comments/batch-review` |
| 业务权限 | `blog:comment:review`；SELF 按每条评论所属文章的 authorId 检查 |
| 请求 → 响应 | `BatchStatusRequest`（status=1 通过、2 驳回）→ `BatchVersions` |

继承[既有业务规则](API_CONTRACT.md#model-12-6)，只改显式选中的节点，不递归审核后代。历史占位 status=3 不能审核。审核通过子节点不自动通过祖先，也不绕过公开父链条件；驳回父节点会隐藏整条分支，后代自身状态保持。

<a id="api-66"></a>

<a id="batch-comment-delete"></a>

#### 1.22.8 批量删除评论分支

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/comments/batch-delete` |
| 业务权限 | `blog:comment:moderate`；SELF 按每个目标所属文章的 authorId 检查 |
| 请求 → 响应 | `BatchIdsRequest` → `BatchDeleted` |

不要求版本，按[既有业务规则](API_CONTRACT.md#model-12-6)物理清理目标及任意作者、任意审核状态的全部层级回复，不生成占位。先核验所有显式 ID 存在且可访问，再求分支并集；父子同时选中不因先删父节点导致后续误报 404，重叠节点只清理一次。未选中的祖先和兄弟保留。

<a id="api-117"></a>

<a id="batch-message-review"></a>

#### 1.22.9 批量审核留言

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/messages/batch-review` |
| 业务权限 | `blog:message:review`（ALL） |
| 请求 → 响应 | `BatchStatusRequest`（status=1 通过、2 驳回）→ `BatchVersions` |

继承[既有业务规则](API_CONTRACT.md#model-12-10)的完整父链与私密性规则，占位不可复活；只变更所选节点。通过校验基于整批拟提交的最终状态：被选中且本次通过的祖先可满足子节点条件，未选中的隐藏祖先仍导致整批 409，不由请求数组顺序决定结果。实际从非通过变为通过的节点在整批提交后通知，不泄露悄悄话原文。

<a id="api-118"></a>

<a id="batch-message-delete"></a>

#### 1.22.10 批量删除留言分支

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/messages/batch-delete` |
| 业务权限 | `blog:message:moderate`（ALL） |
| 请求 → 响应 | `BatchIdsRequest` → `BatchDeleted` |

不要求版本，继承[既有业务规则](API_CONTRACT.md#model-12-10)，物理清理目标与全部后代，含悄悄话、其他作者回复和旧占位。全部显式 ID 先校验，再对重叠分支求并集并按子到父清理；任一原始目标不存在整批 404。关闭留言开关不阻止历史清理。

<a id="api-75"></a>

<a id="batch-link-delete"></a>

#### 1.22.11 批量删除友链

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/links/batch-delete` |
| 业务权限 | `blog:link:delete`（ALL） |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

继承[既有业务规则](API_CONTRACT.md#model-12-7)，允许清理待审、展示和下架的记录，不抓取或删除外部图标、站点资源。

<a id="api-84"></a>

<a id="batch-file-delete"></a>

#### 1.22.12 批量删除文件（逐项提交）

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/files/batch-delete` |
| 业务权限 | `blog:file:delete`，逐项检查上传者 ALL/SELF |
| 请求 → 响应 | `BatchIdsRequest` → `BatchFileResults` |

本接口是唯一的**逐项数据库事务**批量操作，不承诺所有文件共同回滚。请求结构和入口鉴权失败时使用普通错误 Result，尚未执行任何文件删除；开始处理后，按请求顺序逐项返回结果，不以首项错误掩盖后续状态。已完成的成功项不回滚，单项业务/存储失败后继续检查其余项；身份或权限失效后不再写入，剩余项分别记相应 401/403，仍保留每个请求 ID 的结果。

逐项继承[既有业务规则](API_CONTRACT.md#model-12-8)的锁、引用检查和失败恢复：任何文章（含草稿、私密、回收站）仍引用时为 409/RESOURCE_IN_USE；不存在/SELF 范围外为 404；存储失败为 503 并保留元数据。对象已删除但数据库提交失败时仍可能暂留元数据，沿用单条重试清理规则，不声称恢复了文件对象。不要求版本，不输出物理路径、对象键或引用文章的敏感信息。

```http
POST /api/v1/admin/files/batch-delete
Authorization: Bearer <site-token>
Content-Type: application/json

{"ids":["601","602"]}
```

```json
{"code":"OK","msg":"成功","data":{"results":[{"id":"601","httpStatus":200,"code":"OK","msg":"成功"},{"id":"602","httpStatus":409,"code":"RESOURCE_IN_USE","msg":"文件仍被文章引用"}]},"errors":[]}
```

请求超时或连接中断可能拿不到已完成项结果，先按原文件详情确认，不盲目重发全部 ID，也不能把重试时的 404 当成证明上次全部成功。

<a id="api-96"></a>

<a id="batch-social-delete"></a>

#### 1.22.13 批量删除社交链接

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/home/social-links/batch-delete` |
| 业务权限 | `home:social:delete`（ALL） |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

继承[既有业务规则](API_CONTRACT.md#model-12-9)，提交后清理各公开端共享的社交链接缓存；不修改主人账号或主人资料，不删除外部图标。

<a id="api-102"></a>

<a id="batch-experience-delete"></a>

#### 1.22.14 批量删除简历经历

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/cv/experiences/batch-delete` |
| 业务权限 | `cv:experience:delete`（ALL） |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

继承[既有业务规则](API_CONTRACT.md#model-12-9)，允许混选教育、工作和项目类型，提交后清理经历缓存，不修改其他经历或主人资料。

<a id="api-108"></a>

<a id="batch-skill-delete"></a>

#### 1.22.15 批量删除简历技能

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/cv/skills/batch-delete` |
| 业务权限 | `cv:skill:delete`（ALL） |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

继承[既有业务规则](API_CONTRACT.md#model-12-9)，提交后清理公开技能缓存，不删除外部图标或改写其他技能排序。

<a id="api-125"></a>

<a id="batch-music-delete"></a>

#### 1.22.16 批量删除音乐

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/music/batch-delete` |
| 业务权限 | `blog:music:delete`（ALL） |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

继承[既有业务规则](API_CONTRACT.md#model-12-11)，只删除曲目和歌词元数据，清理播放列表缓存，不下载或删除外部音频、歌词与封面。

<a id="api-133"></a>

<a id="batch-subscription-status"></a>

#### 1.22.17 批量管理邮件订阅状态

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/rss-subscriptions/batch-status` |
| 业务权限 | `blog:rss:update`（ALL） |
| 请求 → 响应 | `BatchStatusRequest`（status=1 激活、2 退订）→ `BatchVersions` |

继承[既有业务规则](API_CONTRACT.md#model-12-12)。激活须逐条确认当前邮箱已验证、所属账号启用，不补写 verifiedAt、不替换邮箱或触发确认邮件；任一不满足则整批 409。实际再次激活才开始新投递轮次，同状态不重写时间；退订及旧票据失效沿用原规则。数据库整批回滚不代表撤销 Redis 已完成的轮次变更，失败后重新查询确认。

<a id="api-134"></a>

<a id="batch-subscription-delete"></a>

#### 1.22.18 批量删除邮件订阅

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/rss-subscriptions/batch-delete` |
| 业务权限 | `blog:rss:delete`（ALL） |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

继承[既有业务规则](API_CONTRACT.md#model-12-12)，清理记录后旧确认/退订链接不能恢复该记录，不删除本站用户，也不把账号联系邮箱作为已验证订阅邮箱。

<a id="api-140"></a>

<a id="batch-visitor-block"></a>

#### 1.22.19 批量封禁访客统计标识

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/visitors/batch-block` |
| 业务权限 | `blog:visitor:block`（ALL） |
| 请求 → 响应 | `BatchBlockRequest` → `BatchVersions` |

继承[既有业务规则](API_CONTRACT.md#model-12-13)，所选标识采用同一 blockedUntil；不接受省略时间而默认封禁一天。仅限制这些统计标识的采集，不禁用本站账号，不阻止公开阅读。

<a id="api-141"></a>

<a id="batch-visitor-unblock"></a>

#### 1.22.20 批量解除访客统计限制

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/visitors/batch-unblock` |
| 业务权限 | `blog:visitor:unblock`（ALL） |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchVersions` |

继承[既有业务规则](API_CONTRACT.md#model-12-13)，将 isBlocked=0、blockedUntil=null；已解封同样先校验版本，不修改最近访问、地域或账号状态。

<a id="api-142"></a>

<a id="batch-visitor-delete"></a>

#### 1.22.21 批量删除访客统计记录

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/visitors/batch-delete` |
| 业务权限 | `blog:visitor:delete`（ALL） |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

继承[既有业务规则](API_CONTRACT.md#model-12-13)，浏览明细保留且 visitorId 置 null，因此 PV 不变、基于非 null 访客的 UV 可能减少；不删除浏览记录、不减少文章累计阅读、不删除本站用户。同一 fingerprint 后续采集可重新生成访客记录。

<a id="api-144"></a>

<a id="batch-page-view-delete"></a>

#### 1.22.22 批量清理浏览记录

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/page-views/batch-delete` |
| 业务权限 | `blog:view:delete`（ALL） |
| 请求 → 响应 | `BatchIdsRequest` → `BatchDeleted` |

不要求版本，按[既有业务规则](API_CONTRACT.md#model-12-13)删除所选明细并清理统计缓存；基于保留明细的 PV/UV 及报表随之重算，不反向扣减文章累计 viewCount，不删除访客。

<a id="api-153"></a>

<a id="batch-log-delete"></a>

#### 1.22.23 批量清理操作日志

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/operation-logs/batch-delete` |
| 业务权限 | `sys:log:delete`（ALL） |
| 请求 → 响应 | `BatchIdsRequest` → `BatchDeleted` |

不要求版本，沿用[既有业务规则](API_CONTRACT.md#model-12-14)的显式 ID 清理与审计要求；整批清理本身新增一条审计，随事务提交，不被本次目标集合删除，也不递归产生清理日志。不存在按条件清空所有日志或公开创建/编辑日志入口。

<a id="api-25"></a>

<a id="batch-role-delete"></a>

#### 1.22.24 批量删除角色

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/roles/batch-delete` |
| 业务权限 | `sys:role:delete`；全局资源，沿用原权限规则 |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

继承[既有业务规则](API_CONTRACT.md#model-12-2)，任何内置 admin/author/member 角色均令整批 409。清理用户角色、角色权限和角色菜单关联，撤销受影响用户会话并清理授权缓存，不删除用户、权限字典或菜单；整批最终结果必须保留有效管理员。撤销失败则数据库整批回滚，但已撤销的会话不恢复。

<a id="api-31"></a>

<a id="batch-permission-delete"></a>

#### 1.22.25 批量删除权限字典项

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/permissions/batch-delete` |
| 业务权限 | `sys:permission:delete`；全局资源，沿用原权限规则 |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

继承[既有业务规则](API_CONTRACT.md#model-12-2)，固定后端权限禁止删除（409/STATE_CONFLICT），被任意菜单引用时整批 409/RESOURCE_IN_USE；只级联角色权限关联，不自动删菜单或角色。统一检查整批最终管理员能力，撤销所有受影响用户会话、清理授权缓存；失败不留下部分字典删除。

<a id="api-36"></a>

<a id="batch-menu-delete"></a>

#### 1.22.26 批量删除菜单叶子节点

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/menus/batch-delete` |
| 业务权限 | `sys:menu:delete`；全局资源，沿用原权限规则 |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

继承[既有业务规则](API_CONTRACT.md#model-12-3)的叶子限制：以写入前锁定的整棵树判断，任一目标有子节点即整批 409/RESOURCE_IN_USE；即使子节点也在本次集合中，仍须先另批删除子节点，不隐式递归删除目录。只级联角色菜单关联，清理受影响菜单缓存，不删除权限或撤销接口权限。

<a id="batch-user-enable"></a>

#### 1.22.27 批量启用用户

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/users/batch-enable` |
| 业务权限 | `sys:user:update`；全局资源 |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchVersions`；HTTP 200 |

只将所选用户 status 改为 1，不修改资料或角色，不恢复已撤销的会话。已启用用户仍须校验版本，状态无变化时保留版本。不接受 status 或其他附加字段。

<a id="batch-user-disable"></a>

#### 1.22.28 批量禁用用户

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/users/batch-disable` |
| 业务权限 | `sys:user:update`；全局资源 |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchVersions`；HTTP 200 |

只将所选用户 status 改为 0，不修改资料或角色；实际从启用转为禁用时撤销该账号全部本站会话。同状态也须校验版本。按整批最终状态保护最后有效管理员，违反返回 `409/LAST_ADMIN`，整批回滚。允许选择操作者本人，须在主动撤销会话前完成全部校验及结果版本计算。

<a id="batch-user-delete"></a>

#### 1.22.29 批量删除用户（支持单个用户）

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/users/batch-delete` |
| 业务权限 | `sys:user:delete`；全局资源 |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted`；HTTP 200，data=null |

物理删除用户；一个用户也提交只有一项的 items，不另设单条删除入口。先锁固定管理员保护行，再按数值升序锁操作者及全部目标，复核权限、存在性及每项版本后才能写入。任一目标仍被文章（含回收站）、评论（含占位）、上传文件、主页资料或留言（含占位）引用，返回 `409/RESOURCE_IN_USE`，整批保留；须先按各模块规则处理引用，不能以删除用户为由自动删除内容。删除后的整批结果必须保留最后有效管理员，否则 `409/LAST_ADMIN`。

数据库按既有外键级联清理角色关联、OAuth 绑定、点赞及邮件订阅；浏览记录和操作日志保留，userId 置 null。删除前持有用户锁，提交前撤销所有目标会话；撤销或成功审计失败则数据库整批回滚，已撤销会话不恢复。审计记录所选 ID 和数量，不记录密码、Token、OAuth 身份。删除成功清理统计缓存；文章点赞计数沿用明细实时统计规则。

请求继承本节公共约束：items 为 1..100 项，不重复，ID 为十进制字符串，revision 为各自用户详情的 X-Revision，不发送 If-Revision。无权限为 403；目标不存在为 404；版本缺失/错误/过期分别为 428/400/412；数据库或 Redis 不可用为 503。任一失败不返回部分成功。

单个用户删除示例（批量时向 items 添加其余用户及各自版本）：

```http
POST /api/v1/admin/users/batch-delete
Authorization: Bearer <site-token>
Content-Type: application/json

{"items":[{"id":"12","revision":"\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\""}]}
```

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

---

<a id="client-blog"></a>

## 2. Blog 博客端

<a id="group-1"></a>

<a id="blog-group-1"></a>

### 2.1 认证与第三方授权

<a id="api-1"></a>

#### 2.1.1 注册账号

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/auth/register` |
| 接口描述 | 注册账号 |
| Content-Type | application/json |
| 访问要求 | 公开 |
| 成功状态 | 201 Created |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `X-Captcha-Id` | string | 是 | Header；见[验证码契约](#captcha) |
| `X-Captcha-Code` | string | 是 | Header；6 位数字，保留前导零 |
| `username` | string | 是 | 3..64 位 ASCII 字母、数字或下划线；全库不区分大小写唯一 |
| `password` | string | 是 | 8..64 个字符且 UTF-8 ≤72 字节；不 trim、不截断 |
| `nickname` | string | 是 | 1..64 字符 |
| `email` | string | 是 | 合法邮箱，≤254 字符，去首尾空白并转小写；注册后保存为账号邮箱 |
| `emailCode` | string | 是 | 对应用途邮件中的六位数字；五分钟有效，校验一次即失效 |

##### 请求示例

```http
POST /api/v1/blog/auth/register
X-Captcha-Id: <captchaId>
X-Captcha-Code: 012345
Content-Type: application/json

{"username":"leaf","password":"Example-password-2026","nickname":"落叶","email":"leaf@example.test","emailCode":"012345"}
```

##### 响应参数

`data`：[Me](#response-me)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "userId": "12", "username": "leaf", "nickname": "落叶", "email": "leaf@example.test", "avatar": null, "intro": null,
    "status": 1
  },
  "errors": []
}
```

先验证 REGISTER 用途的邮箱验证码；用户和 member 关联同事务创建；仅返回资料，不自动登录，不接受角色、状态或客户端 userId。用户名、邮箱或二者重复时，返回 [409/UNIQUE_CONFLICT 及对应字段提示](#user-unique-conflict)。

---

<a id="api-2"></a>

#### 2.1.2 账号密码登录

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/auth/login` |
| 接口描述 | 账号密码登录 |
| Content-Type | application/json |
| 访问要求 | 公开 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `X-Captcha-Id` | string | 是 | Header；见[验证码契约](#captcha) |
| `X-Captcha-Code` | string | 是 | Header；6 位数字，保留前导零 |
| `username` | string | 是 | 3..64 位 ASCII 字母、数字或下划线；全库不区分大小写唯一 |
| `password` | string | 是 | 8..64 个字符且 UTF-8 ≤72 字节；不 trim、不截断 |

##### 请求示例

```http
POST /api/v1/blog/auth/login
X-Captcha-Id: <captchaId>
X-Captcha-Code: 012345
Content-Type: application/json

{"username":"leaf","password":"Example-password-2026"}
```

##### 响应参数

`data`：[Session](#response-session)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "tokenType": "Bearer", "accessToken": "<site-token>", "expiresIn": 259200,
    "user": {
      "userId": "12", "username": "leaf", "nickname": "落叶", "email": null, "avatar": null, "intro": null,
      "status": 1
    }
  },
  "errors": []
}
```

用户名不存在、密码错误、纯第三方账号无密码统一返回 401/AUTH_FAILED；验证凭据后再区分禁用账号。持有用户行锁确认身份和状态后创建会话、更新 lastLoginTime，提交后才返回；数据库提交失败撤销刚签发 Token，Redis 创建失败返回 503。

---

<a id="api-3"></a>

#### 2.1.3 退出当前登录

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/auth/logout` |
| 接口描述 | 退出当前登录 |
| Content-Type | 无请求体 |
| 访问要求 | 幂等注销；无 Token 或已失效仍返回 200 |

##### 请求参数

无业务参数；鉴权见访问要求。

##### 请求示例

```http
POST /api/v1/blog/auth/logout
Authorization: Bearer <site-token>
```

##### 响应参数

`data` 固定为 null。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

仅撤销当前 Token；缺失或已失效也返回 200，不泄露会话状态。

---

<a id="api-4"></a>

#### 2.1.4 查询可用第三方平台

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/auth/oauth/providers` |
| 接口描述 | 查询可用第三方平台 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

##### 请求参数

无 Path、Query 或请求体参数；GET/无模型写操作不发送请求体。

##### 请求示例

```http
GET /api/v1/blog/auth/oauth/providers
```

##### 响应参数

`data`：[Provider](#response-provider) 数组。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":[{"source":"GITHUB","displayName":"GitHub"}],"errors":[]}
```

只列配置完整且启用的平台，示例 GITHUB 不代表已经开通；不返回 App ID/Secret。

---

<a id="api-5"></a>

#### 2.1.5 发起第三方登录或绑定

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/auth/oauth/{source}/authorize` |
| 接口描述 | 发起第三方登录或绑定 |
| Content-Type | application/json |
| 访问要求 | LOGIN 公开；BIND 登录本人 + 最近 300 秒认证；均需浏览器 Cookie/Origin |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `X-Captcha-Id` | string | LOGIN 是，BIND 否 | Header；见[验证码契约](#captcha) |
| `X-Captcha-Code` | string | LOGIN 是，BIND 否 | Header；6 位数字，保留前导零 |
| `source` | string | 是 | Path；服务端已启用平台的大写标识；未知或未启用 404 |
| `operation` | string | 是 | LOGIN 登录；BIND 绑定当前用户；不接收 redirectUri/userId/uuid |
| `__Host-dl-oauth-browser` | string | 是（发起时可由后端新设） | Cookie；Secure、HttpOnly、SameSite=Lax、Path=/，仅绑定浏览器，不是业务登录凭证 |
| `Origin` | string | 是 | Header；浏览器自动发送，必须匹配配置前端 origin；BIND/兑换附加身份要求见访问要求 |

##### 请求示例

```http
POST /api/v1/blog/auth/oauth/GITHUB/authorize
Cookie: __Host-dl-oauth-browser=<browser-id>
Origin: https://blog.example.com
X-Captcha-Id: <captchaId>
X-Captcha-Code: 012345
Content-Type: application/json

{"operation":"LOGIN"}
```

##### 响应参数

`data`：[OAuthAuthorizeResult](#response-oauthauthorizeresult)。

##### 响应示例

```http
Set-Cookie: __Host-dl-oauth-browser=<browser-id>; Max-Age=600; Path=/; Secure; HttpOnly; SameSite=Lax
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "authorizationUrl": "https://github.com/login/oauth/authorize?client_id=<configured>&state=<random>",
    "expiresIn": 300
  },
  "errors": []
}
```

LOGIN 公开且仅接受已绑定第三方身份；未绑定返回 OAUTH_NOT_BOUND，必须先通过邮箱注册，再登录绑定。BIND 需当前 Token 及最近 300 秒认证。两者均校验 Origin，并设置/读取 OAuth 浏览器 Cookie；绑定使用弹窗保留原 Token。

---

<a id="api-6"></a>

#### 2.1.6 第三方平台授权回调

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/auth/oauth/{source}/callback` |
| 接口描述 | 第三方平台授权回调 |
| Content-Type | 无请求体 |
| 访问要求 | 仅平台回调；校验 state、浏览器 Cookie、流程及原会话；不要求回调请求携带本站 Bearer |
| 成功状态 | 303（可信流程重定向） |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `source` | string | 是 | Path；服务端已启用平台的大写标识；未知或未启用 404 |
| `__Host-dl-oauth-browser` | string | 是（发起时可由后端新设） | Cookie；Secure、HttpOnly、SameSite=Lax、Path=/，仅绑定浏览器，不是业务登录凭证 |
| `state` | string | 是 | 与当前浏览器、平台和流程匹配，一次性 |
| `code` | string | 成功时 | 所选平台 GET 回调授权码；适配器负责平台实际参数 |
| `error` | string | 拒绝时 | 平台取消/错误标记，不透传平台说明 |
| `error_description` | string | 否 | 平台错误说明，仅允许带回；不交给业务、不写日志、不回显 |

##### 请求示例

```http
GET /api/v1/blog/auth/oauth/GITHUB/callback?code=<platform-code>&state=<random-state>
Cookie: __Host-dl-oauth-browser=<browser-id>
```

##### 响应参数

| 字段/响应头 | 类型 | 说明 |
| --- | --- | --- |
| `Location` | string | 固定前端完成页；成功 fragment 携带 ticket，可信失败携带白名单错误码 |
| `Cache-Control` | string | no-store |
| `Referrer-Policy` | string | no-referrer |
| 响应体 | — | 成功重定向不返回 Result；不可信/无效流程直接返回对应 HTTP JSON 错误 |

##### 响应示例

```http
HTTP/1.1 303 See Other
Location: https://blog.example.com/oauth/complete#ticket=<one-time-ticket>
Cache-Control: no-store
Referrer-Policy: no-referrer
```

平台顶层 GET 回调不携带本站 Bearer。后端凭 state、浏览器 Cookie 和 Redis 流程定位操作；BIND 再校验服务端保存的原会话仍有效且属于目标用户。可信流程成功后 303 到固定完成页，不在回调直接向浏览器发本站 Token。已消费的可信流程遇到数据库访问或事务提交失败时，303 到固定完成页的 `#error=SERVICE_UNAVAILABLE`，不签发成功票据，且不能重放本次 state。

---

<a id="api-7"></a>

#### 2.1.7 兑换一次性登录或绑定结果

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/auth/oauth/exchange` |
| 接口描述 | 兑换一次性登录或绑定结果 |
| Content-Type | application/json |
| 访问要求 | 同浏览器 Cookie；BIND 须原 Token；LOGIN 不需本站 Token |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ticket` | string | 是 | 回调得到的本站一次性票据；有效期 60 秒，不是平台 code 或本站 Token |
| `__Host-dl-oauth-browser` | string | 是（发起时可由后端新设） | Cookie；Secure、HttpOnly、SameSite=Lax、Path=/，仅绑定浏览器，不是业务登录凭证 |
| `Origin` | string | 是 | Header；浏览器自动发送，必须匹配配置前端 origin；BIND/兑换附加身份要求见访问要求 |

##### 请求示例

```http
POST /api/v1/blog/auth/oauth/exchange
Cookie: __Host-dl-oauth-browser=<browser-id>
Origin: https://blog.example.com
Content-Type: application/json

{"ticket":"<one-time-ticket>"}
```

##### 响应参数

`data`：[OAuthResult](#response-oauthresult)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "operation": "LOGIN",
    "session": {
      "tokenType": "Bearer", "accessToken": "<site-token>", "expiresIn": 259200,
      "user": {
        "userId": "12", "username": "leaf", "nickname": "落叶", "email": null, "avatar": null, "intro": null,
        "status": 1
      }
    }
  },
  "errors": []
}
```

BIND 成功分支（仍为 200，原 Token 必须有效，不返回新 Session）：

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "operation": "BIND",
    "binding": {
      "source": "GITHUB", "createTime": "2026-10-29T02:00:00.000Z"
    }
  },
  "errors": []
}
```

必须携带同浏览器 Cookie；LOGIN 返回 Session，BIND 必须带发起时的原 Token，仅返回 binding，不切换账号。票据有效期 60 秒且仅消费一次。

---

<a id="group-2"></a>

<a id="blog-group-2"></a>

### 2.2 本人资料与安全设置

<a id="api-8"></a>

#### 2.2.1 查询本人资料

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/me` |
| 接口描述 | 查询本人资料 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

##### 请求参数

无业务参数；鉴权见访问要求。

##### 请求示例

```http
GET /api/v1/blog/me
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[Me](#response-me)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "userId": "12", "username": "leaf", "nickname": "落叶", "email": null, "avatar": null, "intro": null,
    "status": 1
  },
  "errors": []
}
```

---

<a id="api-9"></a>

#### 2.2.2 修改本人资料

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PATCH /api/v1/blog/me/profile` |
| 接口描述 | 修改本人资料 |
| Content-Type | application/json |
| 访问要求 | 登录本人 |
| 并发校验 | 必填 If-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `nickname` | string | 否 | 提供时不可为 null，1..64 字符 |
| `email` | string&#124;null | 否 | 合法邮箱，≤254 字符，唯一；null 清空 |
| `avatar` | string&#124;null | 否 | 外部 http(s) URL ≤2048 字符；null 清空 |
| `intro` | string&#124;null | 否 | 纯文本 ≤500 字符；null 清空；四项至少提供一项 |

##### 请求示例

```http
PATCH /api/v1/blog/me/profile
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"nickname":"新落叶","email":null,"avatar":null,"intro":null}
```

##### 响应参数

`data`：[Me](#response-me)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "userId": "12", "username": "leaf", "nickname": "新落叶", "email": null, "avatar": null, "intro": null,
    "status": 1
  },
  "errors": []
}
```

至少一项；缺失保持，null 清空可空字段，可空字符串去首尾空白后为空也清空。username、status、角色、权限不可改。头像只接受外部 HTTP(S) 资源，不接受附件 API 路径及其等价绝对 URL（含路径编码、归一化后的形式）。

---

<a id="api-10"></a>

#### 2.2.3 设置或修改本站密码

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/blog/me/password` |
| 接口描述 | 设置或修改本站密码 |
| Content-Type | application/json |
| 访问要求 | 登录本人 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `newPassword` | string | 是 | 同注册密码规则 |
| `currentPassword` | string | 条件必填 | 已有密码时必填且必须验证；纯第三方无密码账号必须省略 |

##### 请求示例

```http
PUT /api/v1/blog/me/password
Authorization: Bearer <site-token>
Content-Type: application/json

{"newPassword":"New-example-password-2026","currentPassword":"Example-password-2026"}
```

##### 响应参数

`data` 固定为 null。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

最近认证；有密码验证 currentPassword，无密码必须由最近第三方登录证明身份。成功撤销本人全部会话。

---

<a id="api-11"></a>

#### 2.2.4 清除本站密码

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/blog/me/password` |
| 接口描述 | 清除本站密码 |
| Content-Type | application/json |
| 访问要求 | 登录本人 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `currentPassword` | string | 是 | 验证现有密码；无密码账号返回 STATE_CONFLICT |

##### 请求示例

```http
DELETE /api/v1/blog/me/password
Authorization: Bearer <site-token>
Content-Type: application/json

{"currentPassword":"Example-password-2026"}
```

##### 响应参数

`data` 固定为 null。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

最近认证并验证现密码；锁用户后确认至少保留一种可用第三方登录方式，成功撤销全部会话。

---

<a id="api-12"></a>

#### 2.2.5 查询本人有效角色与权限

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/me/capabilities` |
| 接口描述 | 查询本人有效角色与权限 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

##### 请求参数

无业务参数；鉴权见访问要求。

##### 请求示例

```http
GET /api/v1/blog/me/capabilities
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[Capabilities](#response-capabilities)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "roleCodes": [
      "member"
    ],
    "permissionCodes": [
      "blog:article:like"
    ],
    "dataScopes": {
      "blog:article:like": "SELF", "blog:comment:create": "SELF", "blog:comment:deleteOwn": "SELF",
      "blog:message:create": "SELF", "blog:message:deleteOwn": "SELF", "blog:message:updateOwn": "SELF"
    }
  },
  "errors": []
}
```

普通 member 也可调用；仅返回当前有效授权，角色/权限字典序，数据范围按每个权限计算。

---

<a id="api-13"></a>

#### 2.2.6 查询本人可访问菜单树

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/me/menus` |
| 接口描述 | 查询本人可访问菜单树 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

##### 请求参数

无业务参数；鉴权见访问要求。

##### 请求示例

```http
GET /api/v1/blog/me/menus
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[MenuNode](#response-menunode) 数组。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": [
    {
      "menuId": "100", "parentId": null, "menuCode": "content", "menuName": "内容管理", "menuType": "D",
      "routeName": "Content", "status": 1
    }
  ],
  "errors": []
}
```

无 admin:access 返回 []。菜单授权与权限求交集并检查祖先；返回隐藏但有权的路由和按钮，按钮不能生成前端路由。

---

<a id="api-14"></a>

#### 2.2.7 查询本人第三方绑定

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/me/oauth-bindings` |
| 接口描述 | 查询本人第三方绑定 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

##### 请求参数

无业务参数；鉴权见访问要求。

##### 请求示例

```http
GET /api/v1/blog/me/oauth-bindings
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[OAuthBinding](#response-oauthbinding) 数组。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":[{"source":"GITHUB","createTime":"2026-10-29T02:00:00.000Z"}],"errors":[]}
```

source 升序，只列当前配置完整且启用的平台绑定，仅返回平台和绑定时间，不暴露 uuid、绑定记录 ID 或第三方 Token。未启用或配置不完整的绑定记录仍保留在库中，不计为可用登录方式。

---

<a id="api-15"></a>

#### 2.2.8 解绑本人第三方账号

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/blog/me/oauth-bindings/{source}` |
| 接口描述 | 解绑本人第三方账号 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `source` | string | 是 | Path；服务端已启用平台的大写标识；未知或未启用 404 |

##### 请求示例

```http
DELETE /api/v1/blog/me/oauth-bindings/GITHUB
Authorization: Bearer <site-token>
```

##### 响应参数

`data` 固定为 null。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

最近认证；未绑定 404。锁同一用户，检查剩余有效密码或可用平台绑定；禁止解绑最后登录方式，成功撤销全部会话。

---

<a id="blog-group-3"></a>

### 2.3 文章与附件

<a id="api-37"></a>

#### 2.3.1 分页查询公开文章

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/articles` |
| 接口描述 | 分页查询公开文章 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；文章须公开 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `categoryId` | ID | 否 | 可选，分类筛选 |
| `tagId` | ID | 否 | 可选，标签筛选 |
| `authorId` | ID | 否 | 可选，作者筛选；与分类、标签、关键词和月份同时满足，不作为登录身份 |
| `month` | string | 否 | 可选，合法 YYYY-MM，年份 1000..9999；按 UTC 月份过滤 |

##### 请求示例

```http
GET /api/v1/blog/articles?page=1&pageSize=10
```

##### 响应参数

`data`：分页对象，`records[]` 为 [ArticlePublic](#response-articlepublic)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "articleId": "101",
        "author": {
          "userId": "12", "nickname": "落叶", "avatar": null
        },
        "category": null, "title": "第一片落叶", "slug": "first-leaf", "summary": "记录与分享"
      }
    ]
  },
  "errors": []
}
```

全部过滤 AND 组合；isTop DESC,publishTime DESC,articleId DESC。keyword 标题、摘要与Markdown原文字面包含，隐藏分类不暴露。

按分类、标签、关键词和月份查询的独立调用示例见 [7.12](#article-scenarios)。公开查询支持可选 `authorId`，与其他筛选按 AND 组合；完整约定见 [22.1](#article-author-query)。

---

<a id="api-38"></a>

#### 2.3.2 按 slug 查询公开文章详情

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/articles/{slug}` |
| 接口描述 | 按 slug 查询公开文章详情 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；文章须公开 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `slug` | string | 是 | Path；文章 slug ≤200，区分大小写 |

##### 请求示例

```http
GET /api/v1/blog/articles/first-leaf
```

##### 响应参数

`data`：[ArticlePublicDetail](#response-articlepublicdetail)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "articleId": "101",
    "author": {
      "userId": "12", "nickname": "落叶", "avatar": null
    },
    "category": null, "title": "第一片落叶", "slug": "first-leaf", "summary": "记录与分享"
  },
  "errors": []
}
```

每次成功原子增加 view_count；失败不计数，这是阅读次数而非独立访客。仅输出清理后的HTML；字数/阅读时长、上下篇和相关推荐见 [文章导航与阅读统计](API_CONTRACT.md#model-12-15)，所有关联文章也必须PUBLIC。

已实现的[详情缓存](#public-cache)只复用公开内容片段，命中仍执行上述成功计数及当前PUBLIC校验；详情与[公开GET限流](#request-protection)同样适用于缓存命中，不引入访客去重口径。

---

<a id="api-39"></a>

#### 2.3.3 分页查询文章月归档

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/archives` |
| 接口描述 | 分页查询文章月归档 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；文章须公开 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |

##### 请求示例

```http
GET /api/v1/blog/archives?page=1&pageSize=10
```

##### 响应参数

`data`：分页对象，`records[]` 为 [Archive](#response-archive)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "month": "2026-10", "articleCount": "1"
      }
    ]
  },
  "errors": []
}
```

仅统计 PUBLIC；UTC 月份 YYYY-MM，month DESC。点击月份复用文章列表 month。

---

<a id="api-87"></a>

#### 2.3.4 查询文章热门榜

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/article-rankings` |
| 接口描述 | 查询文章热门榜 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `period` | string | 否 | site全站/month本月，默认site |
| `metric` | string | 否 | view浏览/like点赞，默认view |
| `limit` | integer | 否 | 默认5，1..20 |

##### 请求示例

```http
GET /api/v1/blog/article-rankings?period=month&metric=view&limit=5
```

##### 响应参数

`data`：[HotArticle](#response-hotarticle) 数组。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": [
    {
      "articleId": "101", "title": "第一片落叶", "slug": "first-leaf", "summary": "记录与分享", "coverUrl": null,
      "viewCount": "120"
    }
  ],
  "errors": []
}
```

保留旧站4个榜单的业务，用period与metric枚举参数表达。month按UTC本月首次发布时间过滤候选文章，再按当前累计view_count或点赞明细COUNT排序；不是本月新增浏览/点赞。排序hotValue DESC,publishTime DESC,articleId DESC，所有候选必须PUBLIC，不增加浏览计数。使用独立article-rankings路径，不占用既有文章slug。

---

<a id="article-scenarios"></a>

#### 7.12 文章业务场景速查

本节是既有接口的业务用法，不增加路由。分类、标签、作者筛选分别用于分类页、标签页和作者文章管理；无需为同一分页能力再定义平行的查询接口。

| 业务场景 | 完整调用示例 | 状态与规则 |
| --- | --- | --- |
| 按分类查询公开文章 | `GET /api/v1/blog/articles?categoryId=1&page=1&pageSize=10` | 已实现；分类 ID 来自 [公开分类列表](#api-47)，隐藏/不存在分类返回空页 |
| 按标签查询公开文章 | `GET /api/v1/blog/articles?tagId=1&page=1&pageSize=10` | 已实现；标签 ID 来自 [公开标签列表](#api-53)，不存在或没有公开文章的标签返回空页 |
| 搜索文章 | `GET /api/v1/blog/articles?keyword=Java&page=1&pageSize=10` | 已实现；标题、摘要、Markdown 原文字面包含，`%`、`_` 不作为 SQL 通配符 |
| 查看某月归档文章 | `GET /api/v1/blog/articles?month=2026-10&page=1&pageSize=10` | 已实现；月份来自 [归档列表](#api-39)，按 UTC 月份过滤 |
| 分类、标签和搜索组合 | `GET /api/v1/blog/articles?categoryId=1&tagId=1&keyword=Java&page=1&pageSize=10` | 已实现；所有条件 AND 组合，total 使用相同过滤 |
| 后台按作者查询文章 | `GET /api/v1/admin/articles?authorId=12&page=1&pageSize=10` | 已实现；需要 `blog:article:query`，SELF 查其他作者返回空页，不能扩大范围 |
| 管理本人草稿 | `GET /api/v1/admin/articles?status=0&trash=0&page=1&pageSize=10` | 已实现；SELF 自动限定本人，ALL 角色可追加 `authorId` 定位作者 |
| 查看下架文章 | `GET /api/v1/admin/articles?status=2&trash=0&page=1&pageSize=10` | 已实现；受 query 范围限制，前台不展示 |
| 删除文章 | `POST /api/v1/admin/articles/batch-recycle` | 已实现；[统一回收](#api-45)，Body 提交 items（含 id=101 和详情 revision），移入回收站 |
| 查询回收站 | `GET /api/v1/admin/articles?trash=1&page=1&pageSize=10` | 已实现；仍按 query 范围过滤，不与正常文章混查 |
| 恢复文章 | `POST /api/v1/admin/articles/batch-restore` | 已实现；[统一恢复](#api-46)，Body 提交 items（含 id=101 和回收站详情 revision），恢复为下架，不自动公开 |
| 后台预览草稿或下架内容 | `GET /api/v1/admin/articles/101` | 已实现；[7.5](#api-41) 返回 Markdown 与安全 HTML，公开详情不会因 Token 放宽可见性 |
| 阅读上下篇和同分类推荐 | `GET /api/v1/blog/articles/first-leaf` | 已实现；[7.2](#api-38) 返回 prevArticle、nextArticle、relatedArticles，同分类推荐最多 6 条；详情成功会增加阅读量 |
| 前台按作者查询公开文章 | `GET /api/v1/blog/articles?authorId=12&page=1&pageSize=10` | 已实现；[22.1](#article-author-query)，与其他筛选按 AND 组合 |

删除流程：在对应权限范围内获取后台详情及 X-Revision → 将 ID 与版本放入 items 数组提交统一回收接口 → 从正常列表移除；回收站若要恢复，重新取详情取得最新版本。缺少版本返回 428，版本过期返回 412。回收站文章仍占用 slug，恢复时沿用原 slug；永久删除的单独设计见 [22.7](#api-159)。

分类/标签页目前可从分页字典列表取得 ID，再调用上述文章列表。直接打开 `/categories/{slug}` 或 `/tags/{slug}` 时定位单个字典的补充设计见 [22.4](#api-156)、[22.5](#api-157)，无需让客户端遍历所有字典分页。

---

<a id="api-59"></a>

#### 2.3.5 分页查询文章公开评论

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/articles/{articleId}/comments` |
| 接口描述 | 分页查询文章公开评论 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；文章须公开 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..50 |
| `parentId` | ID | 否 | 可选，同篇文章的可见父评论；缺失仅根节点 |

##### 请求示例

```http
GET /api/v1/blog/articles/101/comments?page=1&pageSize=10
```

##### 响应参数

`data`：分页对象，`records[]` 为 [CommentPublic](#response-commentpublic)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "commentId": "201", "articleId": "101", "parentId": null, "content": "感谢分享。", "status": 1,
        "createTime": "2026-10-29T02:00:00.000Z"
      }
    ]
  },
  "errors": []
}
```

每页最多 50；不传 parentId 为根节点，传入只查直接回复；createTime DESC,commentId DESC。祖先 0/2 隐藏整支；3 占位 content 固定且 author=null。replyCount 只计可见直接子节点，包含可展开的占位，不计更深层后代。

---

<a id="api-60"></a>

#### 2.3.6 发表评论或回复

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/articles/{articleId}/comments` |
| 接口描述 | 发表评论或回复 |
| Content-Type | application/json |
| 访问要求 | 登录 + `blog:comment:create`；目标文章须 PUBLIC |
| 成功状态 | 201 Created |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `X-Captcha-Id` | string | 是 | Header；见[验证码契约](#captcha) |
| `X-Captcha-Code` | string | 是 | Header；6 位数字，保留前导零 |
| `articleId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `content` | string | 是 | trim 后 1..2000 字符纯文本 |
| `parentId` | ID&#124;null | 否 | 默认 null；非 null 须同篇文章、已通过且祖先可见 |

##### 请求示例

```http
POST /api/v1/blog/articles/101/comments
Authorization: Bearer <site-token>
X-Captcha-Id: <captchaId>
X-Captcha-Code: 012345
Content-Type: application/json

{"content":"感谢分享。","parentId":null}
```

##### 响应参数

`data`：[CommentOwn](#response-commentown)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "commentId": "201", "articleId": "101", "userId": "12", "parentId": null, "content": "感谢分享。", "status": 0
  },
  "errors": []
}
```

需要 blog:comment:create；文章 PUBLIC，站点和文章评论开关均开。审核策略由服务端决定 0/1，管理员回复也不自动免审。

---

<a id="api-67"></a>

#### 2.3.7 查询本人点赞状态

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/articles/{articleId}/like` |
| 接口描述 | 查询本人点赞状态 |
| Content-Type | 无请求体 |
| 访问要求 | 登录且启用，仅操作本人；目标文章须 PUBLIC |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/blog/articles/101/like
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[LikeState](#response-likestate)。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":{"liked":false,"likeCount":"0"},"errors":[]}
```

须登录本人但不要求 like 权限；文章仍须 PUBLIC。

---

<a id="api-68"></a>

#### 2.3.8 点赞文章

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/blog/articles/{articleId}/like` |
| 接口描述 | 点赞文章 |
| Content-Type | 无请求体 |
| 访问要求 | 登录 + `blog:article:like`；目标文章须 PUBLIC |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
PUT /api/v1/blog/articles/101/like
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[LikeState](#response-likestate)。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":{"liked":true,"likeCount":"1"},"errors":[]}
```

复合主键去重，重复 PUT 保持 liked=true，不是 toggle，不收 userId。

---

<a id="api-69"></a>

#### 2.3.9 取消文章点赞

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/blog/articles/{articleId}/like` |
| 接口描述 | 取消文章点赞 |
| Content-Type | 无请求体 |
| 访问要求 | 登录 + `blog:article:like`；目标文章须 PUBLIC |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
DELETE /api/v1/blog/articles/101/like
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[LikeState](#response-likestate)。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":{"liked":false,"likeCount":"0"},"errors":[]}
```

不存在也成功且 liked=false；下架后不开放此路径，既有明细保留。

---

<a id="api-85"></a>

#### 2.3.10 读取公开文章附件

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/articles/{articleId}/files/{fileId}/content` |
| 接口描述 | 读取公开文章附件 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；文章须公开 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `fileId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/blog/articles/101/files/42/content
```

##### 响应参数

| 字段/响应头 | 类型 | 说明 |
| --- | --- | --- |
| 响应体 | binary | PNG/JPEG 原始字节，不是 Result |
| `Content-Type` | string | 服务端确认的 image/png 或 image/jpeg |
| `Content-Length` | integer | 实际文件字节数 |
| `Content-Disposition` | string | inline；filename* 使用安全 UTF-8 编码 |
| `X-Content-Type-Options` | string | nosniff |
| `Cache-Control` | string | no-store；下架/撤销引用后重新鉴权 |

##### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: image/png
Content-Length: 2048
Content-Disposition: inline; filename*=UTF-8''leaf.png
X-Content-Type-Options: nosniff
Cache-Control: no-store

<PNG binary>
```

文章 PUBLIC 且正文/封面实际引用该文件；非公开即使携带 Token 也 404。

---

<a id="article-author-query"></a>

#### 2.3.11 前台按作者查询公开文章（扩展既有接口）

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/articles`，新增可选 `authorId` |
| 接口描述 | 作者主页分页读取该作者的公开文章，并可与分类、标签、关键词和月份组合 |
| Content-Type | 无请求体 |
| 访问要求 | 公开，只查询 PUBLIC 文章 |
| 实施状态 | 已实现参数扩展，不新增路由计数 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `authorId` | ID | 否 | 作者 userId，省略则不按作者过滤；仅筛选，不作为操作者身份 |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |
| `categoryId` | ID | 否 | 与作者条件同时满足；隐藏/不存在分类返回空页 |
| `tagId` | ID | 否 | 与作者条件同时满足 |
| `keyword` | S(100) | 否 | 标题、摘要、Markdown 原文字面包含 |
| `month` | string | 否 | 合法 YYYY-MM，年份 1000..9999；UTC 月份 |

##### 请求示例

```http
GET /api/v1/blog/articles?authorId=12&categoryId=1&tagId=1&page=1&pageSize=10
```

##### 响应参数

`data` 仍为 [7.1 的 Page<ArticlePublic>](#api-37)，完整字段、可空性均不变；total 与 records 必须使用同一作者及组合条件。排序仍为 `isTop DESC,publishTime DESC,articleId DESC`。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":{"page":1,"pageSize":10,"total":"0","totalPages":"0","records":[]},"errors":[]}
```

作者 ID 可取自现有文章的 `author.userId`，也可取自下文作者列表/详情。合法但不存在或没有 PUBLIC 文章的作者返回空页；不是用户详情接口，不泄露草稿数、私密文章数或账号存在性。格式无效的 ID 返回 400。作者文章页可先查询作者详情展示简介，再分页查询文章；不能调用后台用户详情作为公开资料来源。

---

<a id="blog-group-4"></a>

### 2.4 作者主页

<a id="api-154"></a>

#### 2.4.1 分页查询公开作者

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/authors` |
| 接口描述 | 作者发现页及作者筛选器，只列出至少有一篇 PUBLIC 文章的作者 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |
| 实施状态 | 已实现 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |

##### 请求示例

```http
GET /api/v1/blog/authors?page=1&pageSize=10
```

##### 响应参数

`data`：分页对象，`records[]` 为 [AuthorPublic](#response-authorpublic)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "userId": "12", "nickname": "落叶", "avatar": null, "intro": "记录与分享", "articleCount": "3",
        "lastPublishTime": "2026-10-30T02:00:00.000Z"
      }
    ]
  },
  "errors": []
}
```

按 `articleCount DESC,userId ASC` 排序，列表、数量和总作者数在同一只读快照中计算；不是按 author 角色或后台权限枚举用户。最后一篇 PUBLIC 文章下架/转私密/回收后，该作者从公开作者列表消失。不得返回 username、email、密码、OAuth 绑定、lastLoginTime、角色、权限或管理员标志。

---

<a id="api-155"></a>

#### 2.4.2 查询作者公开主页资料

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/authors/{userId}` |
| 接口描述 | 公开作者主页的资料区与公开文章数 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；目标须至少有一篇 PUBLIC 文章 |
| 实施状态 | 已实现 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userId` | ID | 是 | Path；作者用户 ID，不是当前登录用户身份 |

##### 请求示例

```http
GET /api/v1/blog/authors/12
```

##### 响应参数

`data` 为 AuthorPublic，完整字段与约束见 [22.2](#api-154)：userId、nickname、avatar、intro、articleCount、lastPublishTime。主页文章不内嵌、不截断，使用 [22.1](#article-author-query) 独立分页。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "userId": "12", "nickname": "落叶", "avatar": null, "intro": "记录与分享", "articleCount": "3",
    "lastPublishTime": "2026-10-30T02:00:00.000Z"
  },
  "errors": []
}
```

不存在和没有 PUBLIC 文章统一返回 `404/NOT_FOUND`，管理员 Token 不放宽。读取不改变用户最近登录时间或文章阅读量。不同请求间可能发生下架，因此主页成功后文章列表仍可能为空；不承诺跨请求快照。

---

<a id="blog-group-5"></a>

### 2.5 分类与标签

<a id="api-47"></a>

#### 2.5.1 分页查询公开分类

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/categories` |
| 接口描述 | 分页查询公开分类 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；文章须公开 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |

##### 请求示例

```http
GET /api/v1/blog/categories?page=1&pageSize=10
```

##### 响应参数

`data`：分页对象，`records[]` 为 [CategoryPublic](#response-categorypublic)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "categoryId": "1", "categoryName": "技术", "slug": "tech", "description": null, "sortOrder": 0
      }
    ]
  },
  "errors": []
}
```

仅 status=1；sortOrder ASC,categoryId ASC。

---

<a id="api-53"></a>

#### 2.5.2 分页查询公开标签

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/tags` |
| 接口描述 | 分页查询公开标签 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；文章须公开 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |

##### 请求示例

```http
GET /api/v1/blog/tags?page=1&pageSize=10
```

##### 响应参数

`data`：分页对象，`records[]` 为 [TagPublic](#response-tagpublic)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "tagId": "1", "tagName": "Java", "slug": "java"
      }
    ]
  },
  "errors": []
}
```

标签无 status；tagId ASC。

---

<a id="api-156"></a>

#### 2.5.3 按 slug 查询公开分类详情

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/categories/{slug}` |
| 接口描述 | 直接打开分类页时解析 slug，取得分类资料、ID 和公开文章数 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；分类 status=1 |
| 实施状态 | 已实现 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `slug` | string | 是 | Path；ASCII `[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*`，最长 100，区分大小写 |

##### 请求示例

```http
GET /api/v1/blog/categories/tech
```

##### 响应参数

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data.categoryId` | ID | 非 null，供文章列表 categoryId 筛选 |
| `data.categoryName` | S(64) | 分类名称 |
| `data.slug` | string | 当前 slug |
| `data.description` | S(255)? | 分类说明，可 null |
| `data.sortOrder` | integer | int32，字典排序值 |
| `data.articleCount` | string | 该分类当前 PUBLIC 文章数，允许 "0" |

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "categoryId": "1", "categoryName": "技术", "slug": "tech", "description": null, "sortOrder": 0,
    "articleCount": "3"
  },
  "errors": []
}
```

不存在或隐藏分类统一 `404/NOT_FOUND`，包括携带后台 Token 的情况；展示中但没有公开文章的分类返回详情及 articleCount="0"。取得 ID 后调用 `GET /api/v1/blog/articles?categoryId=1`，文章数口径与该列表一致。只新增此详情模型的 articleCount，不默认为既有分类列表增加字段。slug 改名后旧值 404，不新增重定向历史。

---

<a id="api-157"></a>

#### 2.5.4 按 slug 查询公开标签详情

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/tags/{slug}` |
| 接口描述 | 直接打开标签页时解析 slug，取得标签 ID 和公开文章数 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；标签无 status 字段 |
| 实施状态 | 已实现 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `slug` | string | 是 | Path；ASCII `[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*`，最长 100，区分大小写 |

##### 请求示例

```http
GET /api/v1/blog/tags/java
```

##### 响应参数

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data.tagId` | ID | 标签 ID，供文章列表 tagId 筛选 |
| `data.tagName` | S(64) | 标签名称 |
| `data.slug` | string | 当前 slug |
| `data.articleCount` | string | 当前关联的不同 PUBLIC 文章数，允许 "0" |

##### 响应示例

```json
{"code":"OK","msg":"成功","data":{"tagId":"1","tagName":"Java","slug":"java","articleCount":"2"},"errors":[]}
```

不存在返回 `404/NOT_FOUND`；已存在但没有公开文章返回详情及 articleCount="0"。取得 ID 后调用 `GET /api/v1/blog/articles?tagId=1`；数量与该列表使用同一 PUBLIC 条件。只新增此详情模型的 articleCount，不改变既有标签列表字段；slug 改名后旧值 404。分类/标签详情都不返回后台 X-Revision 或非公开文章计数。

---

<a id="blog-group-6"></a>

### 2.6 留言板与审核

<a id="api-109"></a>

#### 2.6.1 分页读取可见留言或回复

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/messages` |
| 接口描述 | 分页读取可见留言或回复 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；可选Bearer，携带时必须校验有效会话和账号状态 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..50 |
| `parentId` | ID | 否 | 缺失仅根节点；提供时仅该节点的直接回复 |

##### 请求示例

```http
GET /api/v1/blog/messages?page=1&pageSize=10
```

##### 响应参数

`data`：分页对象，`records[]` 为 [MessagePublic](#response-messagepublic)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "messageId": "301", "parentId": null,
        "author": {
          "userId": "12", "nickname": "落叶", "avatar": null
        },
        "content": "你好，欢迎交流。", "htmlContent": "<p>你好，欢迎交流。</p>", "contentFormat": 0, "status": 1
      }
    ]
  },
  "errors": []
}
```

匿名只读取审核通过的公开节点和可见删除占位；合法会话可额外读取本人待审/驳回节点及当前私密线程允许的内容。完整祖先链必须可见，total只计当前层可见节点。createTime ASC,messageId ASC。不可见父节点404。

---

<a id="api-110"></a>

#### 2.6.2 发表或回复留言

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/messages` |
| 接口描述 | 发表或回复留言 |
| Content-Type | application/json |
| 访问要求 | 登录 + `blog:message:create`；始终限制本人归属 |
| 成功状态 | 201 Created |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `X-Captcha-Id` | string | 是 | Header；见[验证码契约](#captcha) |
| `X-Captcha-Code` | string | 是 | Header；6 位数字，保留前导零 |
| `parentId` | ID&#124;null | 是，可null | 直接回复目标；根留言为null，回复创建后不可改 |
| `content` | S(2000) | 是 | 去首尾空白后非空；Markdown保留原文内部空白 |
| `contentFormat` | integer | 是 | 0纯文本 1Markdown |
| `isSecret` | integer | 是 | 0公开 1悄悄话；私密回复必须继承完整父链限制 |
| `notifyReply` | integer | 是 | 0/1；见留言通知边界 |

##### 请求示例

```http
POST /api/v1/blog/messages
Authorization: Bearer <site-token>
X-Captcha-Id: <captchaId>
X-Captcha-Code: 012345
Content-Type: application/json

{"parentId":null,"content":"你好，欢迎交流。","contentFormat":0,"isSecret":0,"notifyReply":0}
```

##### 响应参数

`data`：[MessagePrivate](#response-messageprivate)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "messageId": "301", "parentId": null,
    "author": {
      "userId": "12", "nickname": "落叶", "avatar": null
    },
    "content": "你好，欢迎交流。", "htmlContent": "<p>你好，欢迎交流。</p>", "contentFormat": 0, "status": 0
  },
  "errors": []
}
```

作者固定为当前用户。先锁message配置，再锁父链，检查enabled和父链可见性；按reviewRequired确定0/1，普通管理员经此前台入口也不自动免审。客户端不能写作者、昵称、rootId、status、isAdminReply或HTML。 创建成功不设置Location，通过响应ID及本人/后台详情入口定位。

---

<a id="blog-group-7"></a>

### 2.7 本人评论

<a id="api-61"></a>

#### 2.7.1 分页查询本人评论

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/me/comments` |
| 接口描述 | 分页查询本人评论 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..50 |
| `articleId` | ID | 否 | 可选，文章筛选 |
| `status` | integer | 否 | 可选，0/1/2/3；含义见该业务状态约定 |

##### 请求示例

```http
GET /api/v1/blog/me/comments?page=1&pageSize=10
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：分页对象，`records[]` 为 [CommentOwn](#response-commentown)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "commentId": "201", "articleId": "101", "userId": "12", "parentId": null, "content": "感谢分享。",
        "status": 0
      }
    ]
  },
  "errors": []
}
```

每页最多 50；createTime DESC,commentId DESC。下架文章的本人记录仍可管理，不返回文章正文。

---

<a id="api-62"></a>

#### 2.7.2 删除本人评论

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/blog/me/comments/{commentId}` |
| 接口描述 | 删除本人评论 |
| Content-Type | 无请求体 |
| 访问要求 | 登录 + `blog:comment:deleteOwn`；仅本人 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `commentId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
DELETE /api/v1/blog/me/comments/201
Authorization: Bearer <site-token>
```

##### 响应参数

`data` 固定为 null。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

目标评论必须为本人，deleteOwn不受ALL扩大；核对归属后在同一事务中物理删除目标和全部层级子回复，子回复不按作者或审核状态过滤。祖先和兄弟分支保留，不生成删除占位；重复删除或目标不存在返回404。旧status=3占位可再次删除。

---

<a id="blog-group-8"></a>

### 2.8 本人留言

<a id="api-111"></a>

#### 2.8.1 分页查询本人留言

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/me/messages` |
| 接口描述 | 分页查询本人留言 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |
| `status` | integer | 否 | 0/1/2/3 |

##### 请求示例

```http
GET /api/v1/blog/me/messages?page=1&pageSize=10
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：分页对象，`records[]` 为 [MessagePrivate](#response-messageprivate)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "messageId": "301", "parentId": null,
        "author": {
          "userId": "12", "nickname": "落叶", "avatar": null
        },
        "content": "你好，欢迎交流。", "htmlContent": "<p>你好，欢迎交流。</p>", "contentFormat": 0, "status": 1
      }
    ]
  },
  "errors": []
}
```

仅user_id=当前用户，包括待审、驳回、私密与删除占位；createTime DESC,messageId DESC。无额外后台查询权限要求。

---

<a id="api-112"></a>

#### 2.8.2 查询本人留言详情

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/me/messages/{messageId}` |
| 接口描述 | 查询本人留言详情 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |
| 并发校验 | 返回 X-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `messageId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
GET /api/v1/blog/me/messages/301
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[MessagePrivate](#response-messageprivate)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "messageId": "301", "parentId": null,
    "author": {
      "userId": "12", "nickname": "落叶", "avatar": null
    },
    "content": "你好，欢迎交流。", "htmlContent": "<p>你好，欢迎交流。</p>", "contentFormat": 0, "status": 1
  },
  "errors": []
}
```

他人留言一律404；用于编辑前取得X-Revision。查询删除占位仅返回占位，不还原已清除内容。

---

<a id="api-113"></a>

#### 2.8.3 编辑本人留言

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/blog/me/messages/{messageId}` |
| 接口描述 | 编辑本人留言 |
| Content-Type | application/json |
| 访问要求 | 登录 + `blog:message:updateOwn`；始终限制本人归属 |
| 并发校验 | 必填 If-Revision |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `messageId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `X-Captcha-Id` | string | 是 | Header；见[验证码契约](#captcha) |
| `X-Captcha-Code` | string | 是 | Header；6 位数字，提交后需重新获取 |
| `content` | S(2000) | 是 | 去首尾空白后非空；Markdown保留原文内部空白 |
| `contentFormat` | integer | 是 | 0纯文本 1Markdown |
| `notifyReply` | integer | 是 | 0/1；见留言通知边界 |

##### 请求示例

```http
PUT /api/v1/blog/me/messages/301
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
X-Captcha-Id: <captchaId>
X-Captcha-Code: 012345
Content-Type: application/json

{"content":"你好，欢迎交流。","contentFormat":0,"notifyReply":0}
```

##### 响应参数

`data`：[MessagePrivate](#response-messageprivate)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

##### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "messageId": "301", "parentId": null,
    "author": {
      "userId": "12", "nickname": "落叶", "avatar": null
    },
    "content": "你好，欢迎交流。", "htmlContent": "<p>你好，欢迎交流。</p>", "contentFormat": 0, "status": 0
  },
  "errors": []
}
```

完整替换可编辑的content/contentFormat/notifyReply，不接受parentId、isSecret或status；私密范围和父关系创建后不可放宽。编辑写editedAt并重新审核；删除占位409。message.enabled=false时拒绝编辑，保留查询和删除。

---

<a id="api-114"></a>

#### 2.8.4 删除本人留言

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/blog/me/messages/{messageId}` |
| 接口描述 | 删除本人留言 |
| Content-Type | 无请求体 |
| 访问要求 | 登录 + `blog:message:deleteOwn`；始终限制本人归属 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `messageId` | ID | 是 | Path；十进制正整数 ID 字符串 |

##### 请求示例

```http
DELETE /api/v1/blog/me/messages/301
Authorization: Bearer <site-token>
```

##### 响应参数

`data` 固定为 null。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

无If-Revision，与本人评论删除一致；核对目标归属后在同一事务中物理删除目标及全部层级子回复，包括其他作者、私密和各审核状态的后代，不保留占位。祖先和兄弟分支保留；他人、目标不存在或重复删除返回404。旧status=3占位可再次删除。

---

<a id="blog-group-9"></a>

### 2.9 本人点赞记录

<a id="api-158"></a>

#### 2.9.1 分页查询本人点赞的文章

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/me/liked-articles` |
| 接口描述 | 个人中心回看自己点赞过、当前仍公开的文章 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人且账号启用，无额外后台权限 |
| 实施状态 | 已实现 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |

##### 请求示例

```http
GET /api/v1/blog/me/liked-articles?page=1&pageSize=10
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：分页对象，`records[]` 为 [LikedArticle](#response-likedarticle)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "likedAt": "2026-10-31T02:00:00.000Z",
        "article": {
          "articleId": "101",
          "author": {
            "userId": "12", "nickname": "落叶", "avatar": null
          },
          "category": null, "title": "第一片落叶", "slug": "first-leaf", "summary": "记录与分享"
        }
      }
    ]
  },
  "errors": []
}
```

userId 只从当前会话取得，拒绝客户端 userId、authorId 等未声明参数，不允许查询他人的点赞记录。按 `likedAt DESC,articleId DESC` 排序，total 与 records 使用相同条件；不受文章置顶影响。文章下架/转私密/回收后不出现在列表，也不返回隐藏占位或标题；若恢复公开且点赞明细仍在，可重新出现。

取消点赞复用 [9.11](#api-69)；取消后列表移除，再点赞以新明细时间排序。重复点赞不刷新 likedAt。读取不改变点赞、阅读或浏览采集；未登录/会话失效返回 401，不降级为匿名空页。

---

<a id="blog-group-10"></a>

### 2.10 本人邮件订阅

<a id="api-126"></a>

#### 2.10.1 查询本人邮件订阅

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/me/rss-subscription` |
| 接口描述 | 查询本人邮件订阅 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

##### 请求参数

无业务参数；鉴权见访问要求。

##### 请求示例

```http
GET /api/v1/blog/me/rss-subscription
Authorization: Bearer <site-token>
```

##### 响应参数

`data`：[Subscription](#response-subscription)，未配置/无记录时可为 null。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "subscriptionId": "401", "email": "leaf@example.test", "status": 0, "verifiedAt": null,
    "subscribeTime": null, "unsubscribeTime": null
  },
  "errors": []
}
```

仅当前会话用户；未创建时data=null。返回真实状态，不把待验证视为已订阅；不返回确认或退订票据。

---

<a id="api-127"></a>

#### 2.10.2 申请或重发本人邮件订阅

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/blog/me/rss-subscription` |
| 接口描述 | 使用当前登录账号邮箱申请或重发邮件订阅 |
| Content-Type | application/json |
| 访问要求 | 登录 + `blog:rss:subscribe`；始终限制本人归属 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `email` | string | 是 | 合法邮箱≤254；trim并转小写后必须等于当前登录用户的 `sys_user.email`；不得指定其他邮箱 |

##### 请求示例

```http
PUT /api/v1/blog/me/rss-subscription
Authorization: Bearer <site-token>
Content-Type: application/json

{"email":"leaf@example.test"}
```

##### 响应参数

`data`：[Subscription](#response-subscription)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "subscriptionId": "401", "email": "leaf@example.test", "status": 0, "verifiedAt": null,
    "subscribeTime": null, "unsubscribeTime": null
  },
  "errors": []
}
```

订阅页面的邮箱只读，固定显示当前登录账号邮箱，不能在订阅表单中修改。服务端锁定当前用户后读取并规范化 `sys_user.email`，请求邮箱不一致返回400/VALIDATION_ERROR，账号未设置邮箱返回409/STATE_CONFLICT，均不修改订阅或发信；需要先在个人资料中设置账号邮箱。实际保存和发信地址由服务端账号资料决定，不能通过篡改请求订阅其他邮箱。

首次申请，或账号邮箱修改后重新申请且与历史订阅邮箱不同时，写status=0、verifiedAt/subscribeTime/unsubscribeTime=null，旧邮箱推送立即停止；同一已激活邮箱幂等，不重复发信。历史订阅不会仅因资料修改而自动迁移，须用当前账号邮箱重新申请并收信确认。待验证邮箱重试会重发确认邮件，限流返回429。确认票据24小时有效，绑定用户、subscriptionId、当前email及本次申请版本。SMTP/Redis失败503，已提交待验证记录可保留，通过重发恢复；不返回已激活成功。账号与邮箱唯一冲突409，不挪用其他用户订阅。

---

<a id="api-128"></a>

#### 2.10.3 确认本人订阅邮箱

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/me/rss-subscription/confirm` |
| 接口描述 | 确认本人订阅邮箱 |
| Content-Type | application/json |
| 访问要求 | 登录 + `blog:rss:subscribe`；始终限制本人归属 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ticket` | S(512) | 是 | 从确认邮件完成页读取的不透明票据；不得放日志 |

##### 请求示例

```http
POST /api/v1/blog/me/rss-subscription/confirm
Authorization: Bearer <site-token>
Content-Type: application/json

{"ticket":"confirmation-ticket-example"}
```

##### 响应参数

`data`：[Subscription](#response-subscription)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "subscriptionId": "401", "email": "leaf@example.test", "status": 1,
    "verifiedAt": "2026-10-30T02:00:00.000Z", "subscribeTime": "2026-10-30T02:00:00.000Z",
    "unsubscribeTime": null
  },
  "errors": []
}
```

需已登录的同一本站用户；可在另一个浏览器登录同一账号确认，不依赖旧访客存储。锁用户及订阅，验证当前pending状态、邮箱和申请版本，订阅邮箱还须与当前账号邮箱规范化后一致，Redis原子消费后设置verifiedAt、subscribeTime、status=1。过期/复用/错账号、账号邮箱已变化或已被新申请替换统一400/SUBSCRIPTION_TICKET_INVALID。数据库失败回滚，已消费票据需重新申请；不把Redis与MySQL描述为共同事务。

---

<a id="api-129"></a>

#### 2.10.4 取消本人邮件订阅

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/blog/me/rss-subscription` |
| 接口描述 | 取消本人邮件订阅 |
| Content-Type | 无请求体 |
| 访问要求 | 登录 + `blog:rss:unsubscribe`；始终限制本人归属 |

##### 请求参数

无业务参数；鉴权见访问要求。

##### 请求示例

```http
DELETE /api/v1/blog/me/rss-subscription
Authorization: Bearer <site-token>
```

##### 响应参数

`data` 固定为 null。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

身份只取会话，无If-Revision。将本人记录置status=2并写unsubscribeTime；不存在或已退订幂等200。保留已验证邮箱信息，重订需走申请及确认。后台ALL不扩大到他人。

---

<a id="blog-group-11"></a>

### 2.11 友链

<a id="api-70"></a>

#### 2.11.1 分页查询公开友链

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/links` |
| 接口描述 | 分页查询公开友链 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；文章须公开 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |

##### 请求示例

```http
GET /api/v1/blog/links?page=1&pageSize=10
```

##### 响应参数

`data`：分页对象，`records[]` 为 [LinkPublic](#response-linkpublic)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "linkId": "1", "linkName": "示例友站", "url": "https://example.com", "avatar": null, "description": null,
        "sortOrder": 0
      }
    ]
  },
  "errors": []
}
```

仅 status=1，sortOrder ASC,linkId ASC。

---

<a id="blog-group-12"></a>

### 2.12 站点配置

<a id="api-76"></a>

#### 2.12.1 查询公开站点配置

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/configs` |
| 接口描述 | 查询公开站点配置 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；文章须公开 |

##### 请求参数

无 Path、Query 或请求体参数；GET/无模型写操作不发送请求体。

##### 请求示例

```http
GET /api/v1/blog/configs
```

##### 响应参数

`data`：[Config](#response-config) 数组。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": [
    {
      "configKey": "site",
      "configValue": {
        "title": "DriftingLeaves", "description": "记录与分享", "icp": "", "publicSecurityRecord": "",
        "startDate": null
      },
      "description": "站点信息", "isPublic": 1, "updateTime": "2026-10-29T02:00:00.000Z"
    }
  ],
  "errors": []
}
```

仅允许公开的 site 且 isPublic=1；服务端过滤，configKey ASC。

---

<a id="blog-group-13"></a>

### 2.13 主人资料与社交链接

<a id="blog-api-88"></a>

#### 2.13.1 查询站点主人公开资料

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/profile` |
| 接口描述 | 查询站点主人公开资料 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

##### 请求参数

无业务参数。

##### 请求示例

```http
GET /api/v1/blog/profile
```

##### 响应参数

`data`：[HomeProfilePublic](#response-homeprofilepublic)，未配置/无记录时可为 null。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "nickname": "落叶", "tagline": "记录与分享", "description": null, "avatar": null,
    "website": "https://example.test", "publicEmail": null
  },
  "errors": []
}
```

固定profileId=1。展示资料与登录资料独立，只有publicEmail可公开；禁止从sys_user.email自动补全。头像和URL遵守公开资源约束。未配置时200、data=null；博客和CV复用本入口。

---

<a id="blog-api-91"></a>

#### 2.13.2 查询公开社交链接

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/social-links` |
| 接口描述 | 查询公开社交链接 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

##### 请求参数

无业务参数。

##### 请求示例

```http
GET /api/v1/blog/social-links
```

##### 响应参数

`data`：[SocialPublic](#response-socialpublic) 数组。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": [
    {
      "socialId": "21", "name": "GitHub", "icon": "github", "url": "https://github.com/example",
      "sortOrder": 0
    }
  ],
  "errors": []
}
```

仅status=1，sortOrder ASC,socialId ASC；无数据[]。mailto仅接受单一合法邮箱，不接受额外查询参数或控制字符。

---

<a id="blog-group-14"></a>

### 2.14 背景音乐

<a id="api-120"></a>

#### 2.14.1 分页查询公开播放列表

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/music` |
| 接口描述 | 分页查询公开播放列表 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |

##### 请求示例

```http
GET /api/v1/blog/music?page=1&pageSize=10
```

##### 响应参数

`data`：分页对象，`records[]` 为 [MusicPublic](#response-musicpublic)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "musicId": "51", "title": "秋日", "artist": null, "durationSeconds": 180, "coverUrl": null,
        "audioUrl": "https://example.test/audio.mp3"
      }
    ]
  },
  "errors": []
}
```

仅status=1，sortOrder ASC,musicId ASC；不由服务端代理或抓取音频。是否有歌词由lyricUrl判断，不重复存储hasLyric。

---

<a id="blog-group-15"></a>

### 2.15 邮件订阅管理

<a id="api-130"></a>

#### 2.15.1 通过邮件链接退订

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/rss-subscriptions/unsubscribe` |
| 接口描述 | 通过邮件链接退订 |
| Content-Type | application/json |
| 访问要求 | 公开 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ticket` | S(512) | 是 | 仅限本订阅退订的不可猜票据 |

##### 请求示例

```http
POST /api/v1/blog/rss-subscriptions/unsubscribe
Content-Type: application/json

{"ticket":"unsubscribe-ticket-example"}
```

##### 响应参数

`data` 固定为 null。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

这是显式公开的票据能力接口；不需要Bearer，也不颁发会话。票据存Redis，有效30天，绑定subscriptionId、email、verifiedAt和本轮subscribeTime；只能将该订阅改为退订。相同有效票据重复退订200，邮箱或订阅轮次变化后400/SUBSCRIPTION_TICKET_INVALID。邮件链接指向固定前端完成页#unsubscribeTicket=...，页面清除fragment并由用户确认后POST；邮件扫描器GET不改变状态。不接受email/userId作为替代凭据。

---

<a id="blog-group-16"></a>

### 2.16 RSS 与站点地图

<a id="api-135"></a>

#### 2.16.1 读取公开RSS Feed

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/rss.xml` |
| 接口描述 | 读取公开RSS Feed |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

##### 请求参数

无业务参数。

##### 请求示例

```http
GET /api/v1/blog/rss.xml
```

##### 响应参数

成功输出XML；错误仍遵守第14章HTTP与Result契约。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| 响应体 | XML | 原始XML文档，不包Result |
| Content-Type（Header） | string | 本接口声明的XML媒体类型；错误仍为JSON Result |

##### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/rss+xml; charset=UTF-8
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0"><channel><title>DriftingLeaves</title><link>https://blog.example.test/</link><description>记录与分享</description><item><title>第一片落叶</title><link>https://blog.example.test/articles/first-leaf</link><guid isPermaLink="true">https://blog.example.test/articles/first-leaf</guid><pubDate>Fri, 30 Oct 2026 02:00:00 GMT</pubDate><description>记录与分享</description></item></channel></rss>
```

成功Content-Type=application/rss+xml; charset=UTF-8，返回RSS2.0而非Result。取最近20篇PUBLIC文章，publishTime DESC,articleId DESC；guid使用稳定本站文章URL，link使用规范slug，pubDate为RFC822日期。description输出转义后的摘要，不输出Markdown、私密正文或邮箱。配置的公开博客基址由app.sites.blog-base-url提供，文章前端规范路径为/articles/{slug}，不能信任任意Host头。

---

<a id="api-136"></a>

#### 2.16.2 读取公开站点地图

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/sitemap.xml` |
| 接口描述 | 读取公开站点地图 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 可选，正整数；仅供sitemapindex的子图链接使用 |

##### 请求示例

```http
GET /api/v1/blog/sitemap.xml
```

##### 响应参数

成功输出XML；错误仍遵守第14章HTTP与Result契约。

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| 响应体 | XML | 原始XML文档，不包Result |
| Content-Type（Header） | string | 本接口声明的XML媒体类型；错误仍为JSON Result |

##### 响应示例

```http
HTTP/1.1 200 OK
Content-Type: application/xml; charset=UTF-8
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"><url><loc>https://blog.example.test/articles/first-leaf</loc><lastmod>2026-10-30T02:00:00.000Z</lastmod></url></urlset>
```

成功application/xml; charset=UTF-8，不包Result。只输出站点公开入口、PUBLIC文章及可公开分类/标签URL；使用安全XML编码，lastmod取公开资源更新时间，UTC格式。超过单文件50000条或未压缩50MiB时返回sitemapindex；同一路径支持page正整数查询读取有界子图，未知页404。不得悄悄截断已发布URL。子图链接使用app.sites.api-base-url（含/api/v1）加/blog/sitemap.xml?page=...；公开入口使用已配置home/blog/cv基址，分类/标签前端路径为/categories/{slug}与/tags/{slug}。任何缓存均需随发布、下架、回收、可见性或slug变化失效，不能继续暴露非PUBLIC条目。

---

<a id="blog-group-17"></a>

### 2.17 统计报表

<a id="api-145"></a>

#### 2.17.1 查询公开博客统计

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/statistics` |
| 接口描述 | 查询公开博客统计 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

##### 请求参数

无业务参数。

##### 请求示例

```http
GET /api/v1/blog/statistics
```

##### 响应参数

`data`：[PublicStatistics](#response-publicstatistics)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "articleCount": "12", "categoryCount": "3", "tagCount": "5", "pageViewCount": "120",
    "todayPageViewCount": "8", "visitorCount": "35"
  },
  "errors": []
}
```

仅公开汇总，不接受任意userId或日期探索后台数据；当前未公开文章/分类及待审、私密互动不进入内容汇总。不输出IP、邮箱或UA。当前[公开统计缓存](#public-cache)允许访问PV/UV最多延迟5分钟，内容发布/下架及统计清理主动失效，UTC跨日不复用昨日键。

---

<a id="blog-group-18"></a>

### 2.18 浏览统计采集

<a id="api-137"></a>

#### 2.18.1 记录站点页面浏览

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/analytics/page-views` |
| 接口描述 | 记录站点页面浏览 |
| Content-Type | application/json |
| 访问要求 | 公开；可选Bearer，携带时必须校验有效会话和账号状态 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fingerprint` | string | 是 | ASCII字母数字或连字符16..150；浏览器随机统计标识，可伪造，不能作为身份 |
| `siteCode` | string | 是 | home/blog/cv；服务端与明确配置的来源站点核验 |
| `pagePath` | S(2048) | 是 | 规范站内路径，必须以/起始，剔除fragment及全部query |
| `pageTitle` | S(200)? | 是，可null | 展示标题，不作为鉴权或资源定位依据 |
| `referer` | URL? | 是，可null | 来源URL；服务端再次脱敏并去掉query/fragment |
| `articleId` | ID&#124;null | 是，可null | 仅blog文章详情可传；需PUBLIC且与pagePath的规范slug匹配 |

##### 请求示例

```http
POST /api/v1/blog/analytics/page-views
Content-Type: application/json

{
  "fingerprint": "d681988a-cbd0-4ef6-bdec-f84bfa234e16", "siteCode": "home", "pagePath": "/",
  "pageTitle": "DriftingLeaves", "referer": null, "articleId": null
}
```

##### 响应参数

`data`：[TrackingResult](#response-trackingresult)。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":{"recorded":true},"errors":[]}
```

IP/UA、viewTime和userId由服务端读取；不接受visitorId、用户ID、会话ID或Token字段。携带Bearer时必须有效，匿名不创建sys_user。事务写访客首次/最近时间及一条page_view后返回recorded=true；失败503不伪造已记录。该POST不增加blog_article.view_count，文章成功详情读取仍按7.2计一次，避免双计。受限统计标识返回403/VISITOR_BLOCKED，只影响此采集入口；可伪造标识不能用于阻止公开阅读。重复POST可能产生多条浏览，无通用幂等键。

已实现的[采集限流](#request-protection)在访客创建、地域解析与明细写入前执行；429不得写入数据或返回recorded=true。fingerprint只增加统计维度限制，不能代替独立IP限流；本POST响应不缓存。

`siteCode` 必须等于 `blog`，否则返回 400/VALIDATION_ERROR；后续仍校验 Origin 与配置的站点路径。

---

<a id="blog-group-19"></a>

### 2.19 实时在线人数

<a id="api-160"></a>

#### 2.19.1 申请一次性在线票据

| 项目 | 约定 |
| --- | --- |
| 接口 | `POST /api/v1/blog/analytics/online/tickets` |
| 身份 | 公开、可选 Bearer；携带时必须属于启用账号的有效本站会话 |
| 成功 | HTTP 201，`Cache-Control: no-store`，无版本头 |
| 请求体 | `fingerprint`：ASCII `[A-Za-z0-9-]{16,150}`；`siteCode`：`home/blog/cv`；`pagePath`：S(2048)站内路径，不能包含查询/fragment |
| 来源 | 必须提供 Origin，在 `app.auth.allowed-origins` 内且匹配 `app.sites` 的站点与最长路径前缀 |
| 参数 | 三个 Body 字段均必填；拒绝查询参数、未知/重复字段和类型错误 |
| 限流 | 60秒每可信IP120次、每站点/Origin/fingerprint统计键30次；429带Retry-After |

```http
POST /api/v1/blog/analytics/online/tickets
Origin: https://blog.example.test
Authorization: Bearer <site-token>
Content-Type: application/json

{"fingerprint":"d681988a-cbd0-4ef6-bdec-f84bfa234e16","siteCode":"blog","pagePath":"/"}
```

匿名请求省略 Authorization；Token 的持久化和刷新恢复遵守通用协议，仅经 HTTP Header 发送。示例 Origin 必须在部署中明确允许。

```json
{"code":"OK","msg":"成功","data":{"ticket":"<43-character-single-use-ticket>","expiresIn":30},"errors":[]}
```

票据随机且只用于一次 join，Redis TTL 30 秒，申请本身不增加人数。无效会话401、禁用账号403/ACCOUNT_DISABLED、来源/站点不匹配403/FORBIDDEN；Redis或身份查询不可用503，不降级匿名签发。

`siteCode` 必须等于 `blog`，否则返回 400/VALIDATION_ERROR；后续仍校验 Origin 与配置的站点路径。

---

<a id="online-websocket"></a>

#### 2.19.2 加入原生 WebSocket 并接收快照

`ws(s)://<API主机>/api/v1/blog/ws/online`；从既有 API 地址推导，只映射协议并追加 `/blog/ws/online`。握手只允许精确 GET Upgrade 和明确 Origin，拒绝查询参数、Authorization 和子协议传身份；普通 GET 返回400。Cookie 不证明身份。建连后五秒内发送：

```json
{"type":"join","ticket":"<43-character-single-use-ticket>"}
```

票据绑定 Origin，原会话与启用账号在 join 时重查，完整载荷比较后原子消费；同一连接只能加入一次。成功立即收到快照，后续人数变化广播：

```json
{"type":"online.count","code":"OK","msg":"成功","data":{"onlineCount":"8","userCount":"3","guestCount":"5","serverTime":"2026-11-01T02:00:00.000Z"},"errors":[]}
```

三项人数均为非负、无前导零的十进制字符串。登录账号按本站 userId 去重；匿名按已经核验的站点、Origin、fingerprint 去重，同键存在有效登录连接时不重复计访客。`onlineCount=userCount+guestCount`。不返回用户、IP、连接清单或凭据。

客户端每25秒发送 `{"type":"ping"}`，服务端单发 `{"type":"pong","code":"OK","msg":"成功","data":null,"errors":[]}`。10秒等不到pong则关闭旧连接并重签票据；服务端75秒未收到合法心跳即过期，每5秒回收。每25秒重查原登录会话与账号，正常依赖条件下撤销最晚约30秒移除；不会因认证超过300秒误踢，也不续期Token。

单消息最多1KiB，只接受声明的JSON文本字段；二进制/未知/重复字段、重复join、加入前ping等拒绝。错误消息 `type=error`、`data=null`；其余包络保持 `code/msg/errors`。票据无效用 `ONLINE_TICKET_INVALID`，身份失效用 `UNAUTHENTICATED/ACCOUNT_DISABLED`，协议错误用 `VALIDATION_ERROR`，频率超限用 `RATE_LIMITED`，依赖失败用 `SERVICE_UNAVAILABLE`。关闭码分别为1008（策略）、1009（超大帧）、4000（心跳）、1011（服务失败）、1000（主动离开）。协议错停止自动重试，频率超限至少等待60秒；其他断线使用1/2/5/10/30秒退避及少量随机延迟，HTTP429尊重Retry-After。

Blog连接由实际 `view/Layout/index.vue` 管理，站点统计卡片只读取快照；密码/OAuth的 `acceptSession` 和 `clearSession` 统一重连。初始显示连接中，失败显示中断，不把错误或旧快照当作实时0人数。Home/Cv仅匿名登记、没有人数展示；三个页脚和后台均不展示。

仅接受当前端签发且 Origin 匹配的票据；跨端使用返回 ONLINE_TICKET_INVALID。

---

<a id="client-home"></a>

## 3. Home 主页端

<a id="home-group-1"></a>

### 3.1 站点配置

<a id="home-api-76"></a>

#### 3.1.1 查询公开站点配置

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/home/configs` |
| 接口描述 | 查询公开站点配置 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；文章须公开 |

##### 请求参数

无 Path、Query 或请求体参数；GET/无模型写操作不发送请求体。

##### 请求示例

```http
GET /api/v1/home/configs
```

##### 响应参数

`data`：[Config](#response-config) 数组。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": [
    {
      "configKey": "site",
      "configValue": {
        "title": "DriftingLeaves", "description": "记录与分享", "icp": "", "publicSecurityRecord": "",
        "startDate": null
      },
      "description": "站点信息", "isPublic": 1, "updateTime": "2026-10-29T02:00:00.000Z"
    }
  ],
  "errors": []
}
```

仅允许公开的 site 且 isPublic=1；服务端过滤，configKey ASC。

---

<a id="home-group-2"></a>

### 3.2 主人资料与社交链接

<a id="api-88"></a>

#### 3.2.1 查询站点主人公开资料

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/home/profile` |
| 接口描述 | 查询站点主人公开资料 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

##### 请求参数

无业务参数。

##### 请求示例

```http
GET /api/v1/home/profile
```

##### 响应参数

`data`：[HomeProfilePublic](#response-homeprofilepublic)，未配置/无记录时可为 null。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "nickname": "落叶", "tagline": "记录与分享", "description": null, "avatar": null,
    "website": "https://example.test", "publicEmail": null
  },
  "errors": []
}
```

固定profileId=1。展示资料与登录资料独立，只有publicEmail可公开；禁止从sys_user.email自动补全。头像和URL遵守公开资源约束。未配置时200、data=null；博客和CV复用本入口。

---

<a id="api-91"></a>

#### 3.2.2 查询公开社交链接

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/home/social-links` |
| 接口描述 | 查询公开社交链接 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

##### 请求参数

无业务参数。

##### 请求示例

```http
GET /api/v1/home/social-links
```

##### 响应参数

`data`：[SocialPublic](#response-socialpublic) 数组。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": [
    {
      "socialId": "21", "name": "GitHub", "icon": "github", "url": "https://github.com/example",
      "sortOrder": 0
    }
  ],
  "errors": []
}
```

仅status=1，sortOrder ASC,socialId ASC；无数据[]。mailto仅接受单一合法邮箱，不接受额外查询参数或控制字符。

---

<a id="home-group-3"></a>

### 3.3 浏览统计采集

<a id="home-api-137"></a>

#### 3.3.1 记录站点页面浏览

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/home/analytics/page-views` |
| 接口描述 | 记录站点页面浏览 |
| Content-Type | application/json |
| 访问要求 | 公开；可选Bearer，携带时必须校验有效会话和账号状态 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fingerprint` | string | 是 | ASCII字母数字或连字符16..150；浏览器随机统计标识，可伪造，不能作为身份 |
| `siteCode` | string | 是 | home/home/cv；服务端与明确配置的来源站点核验 |
| `pagePath` | S(2048) | 是 | 规范站内路径，必须以/起始，剔除fragment及全部query |
| `pageTitle` | S(200)? | 是，可null | 展示标题，不作为鉴权或资源定位依据 |
| `referer` | URL? | 是，可null | 来源URL；服务端再次脱敏并去掉query/fragment |
| `articleId` | ID&#124;null | 是，可null | 仅blog文章详情可传；需PUBLIC且与pagePath的规范slug匹配 |

##### 请求示例

```http
POST /api/v1/home/analytics/page-views
Content-Type: application/json

{
  "fingerprint": "d681988a-cbd0-4ef6-bdec-f84bfa234e16", "siteCode": "home", "pagePath": "/",
  "pageTitle": "DriftingLeaves", "referer": null, "articleId": null
}
```

##### 响应参数

`data`：[TrackingResult](#response-trackingresult)。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":{"recorded":true},"errors":[]}
```

IP/UA、viewTime和userId由服务端读取；不接受visitorId、用户ID、会话ID或Token字段。携带Bearer时必须有效，匿名不创建sys_user。事务写访客首次/最近时间及一条page_view后返回recorded=true；失败503不伪造已记录。该POST不增加blog_article.view_count，文章成功详情读取仍按7.2计一次，避免双计。受限统计标识返回403/VISITOR_BLOCKED，只影响此采集入口；可伪造标识不能用于阻止公开阅读。重复POST可能产生多条浏览，无通用幂等键。

已实现的[采集限流](#request-protection)在访客创建、地域解析与明细写入前执行；429不得写入数据或返回recorded=true。fingerprint只增加统计维度限制，不能代替独立IP限流；本POST响应不缓存。

`siteCode` 必须等于 `home`，否则返回 400/VALIDATION_ERROR；后续仍校验 Origin 与配置的站点路径。

---

<a id="home-group-4"></a>

### 3.4 实时在线人数

<a id="home-api-160"></a>

#### 3.4.1 申请一次性在线票据

| 项目 | 约定 |
| --- | --- |
| 接口 | `POST /api/v1/home/analytics/online/tickets` |
| 身份 | 公开、可选 Bearer；携带时必须属于启用账号的有效本站会话 |
| 成功 | HTTP 201，`Cache-Control: no-store`，无版本头 |
| 请求体 | `fingerprint`：ASCII `[A-Za-z0-9-]{16,150}`；`siteCode`：`home/home/cv`；`pagePath`：S(2048)站内路径，不能包含查询/fragment |
| 来源 | 必须提供 Origin，在 `app.auth.allowed-origins` 内且匹配 `app.sites` 的站点与最长路径前缀 |
| 参数 | 三个 Body 字段均必填；拒绝查询参数、未知/重复字段和类型错误 |
| 限流 | 60秒每可信IP120次、每站点/Origin/fingerprint统计键30次；429带Retry-After |

```http
POST /api/v1/home/analytics/online/tickets
Origin: https://home.example.test
Authorization: Bearer <site-token>
Content-Type: application/json

{"fingerprint":"d681988a-cbd0-4ef6-bdec-f84bfa234e16","siteCode":"home","pagePath":"/"}
```

匿名请求省略 Authorization；Token 的持久化和刷新恢复遵守通用协议，仅经 HTTP Header 发送。示例 Origin 必须在部署中明确允许。

```json
{"code":"OK","msg":"成功","data":{"ticket":"<43-character-single-use-ticket>","expiresIn":30},"errors":[]}
```

票据随机且只用于一次 join，Redis TTL 30 秒，申请本身不增加人数。无效会话401、禁用账号403/ACCOUNT_DISABLED、来源/站点不匹配403/FORBIDDEN；Redis或身份查询不可用503，不降级匿名签发。

`siteCode` 必须等于 `home`，否则返回 400/VALIDATION_ERROR；后续仍校验 Origin 与配置的站点路径。

---

<a id="home-online-websocket"></a>

#### 3.4.2 加入原生 WebSocket 并接收快照

`ws(s)://<API主机>/api/v1/home/ws/online`；从既有 API 地址推导，只映射协议并追加 `/home/ws/online`。握手只允许精确 GET Upgrade 和明确 Origin，拒绝查询参数、Authorization 和子协议传身份；普通 GET 返回400。Cookie 不证明身份。建连后五秒内发送：

```json
{"type":"join","ticket":"<43-character-single-use-ticket>"}
```

票据绑定 Origin，原会话与启用账号在 join 时重查，完整载荷比较后原子消费；同一连接只能加入一次。成功立即收到快照，后续人数变化广播：

```json
{"type":"online.count","code":"OK","msg":"成功","data":{"onlineCount":"8","userCount":"3","guestCount":"5","serverTime":"2026-11-01T02:00:00.000Z"},"errors":[]}
```

三项人数均为非负、无前导零的十进制字符串。登录账号按本站 userId 去重；匿名按已经核验的站点、Origin、fingerprint 去重，同键存在有效登录连接时不重复计访客。`onlineCount=userCount+guestCount`。不返回用户、IP、连接清单或凭据。

客户端每25秒发送 `{"type":"ping"}`，服务端单发 `{"type":"pong","code":"OK","msg":"成功","data":null,"errors":[]}`。10秒等不到pong则关闭旧连接并重签票据；服务端75秒未收到合法心跳即过期，每5秒回收。每25秒重查原登录会话与账号，正常依赖条件下撤销最晚约30秒移除；不会因认证超过300秒误踢，也不续期Token。

单消息最多1KiB，只接受声明的JSON文本字段；二进制/未知/重复字段、重复join、加入前ping等拒绝。错误消息 `type=error`、`data=null`；其余包络保持 `code/msg/errors`。票据无效用 `ONLINE_TICKET_INVALID`，身份失效用 `UNAUTHENTICATED/ACCOUNT_DISABLED`，协议错误用 `VALIDATION_ERROR`，频率超限用 `RATE_LIMITED`，依赖失败用 `SERVICE_UNAVAILABLE`。关闭码分别为1008（策略）、1009（超大帧）、4000（心跳）、1011（服务失败）、1000（主动离开）。协议错停止自动重试，频率超限至少等待60秒；其他断线使用1/2/5/10/30秒退避及少量随机延迟，HTTP429尊重Retry-After。

Blog连接由实际 `view/Layout/index.vue` 管理，站点统计卡片只读取快照；密码/OAuth的 `acceptSession` 和 `clearSession` 统一重连。初始显示连接中，失败显示中断，不把错误或旧快照当作实时0人数。Home/Cv仅匿名登记、没有人数展示；三个页脚和后台均不展示。

仅接受当前端签发且 Origin 匹配的票据；跨端使用返回 ONLINE_TICKET_INVALID。

---

<a id="client-cv"></a>

## 4. Cv 简历端

<a id="cv-group-1"></a>

### 4.1 站点配置

<a id="cv-api-76"></a>

#### 4.1.1 查询公开站点配置

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/cv/configs` |
| 接口描述 | 查询公开站点配置 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；文章须公开 |

##### 请求参数

无 Path、Query 或请求体参数；GET/无模型写操作不发送请求体。

##### 请求示例

```http
GET /api/v1/cv/configs
```

##### 响应参数

`data`：[Config](#response-config) 数组。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": [
    {
      "configKey": "site",
      "configValue": {
        "title": "DriftingLeaves", "description": "记录与分享", "icp": "", "publicSecurityRecord": "",
        "startDate": null
      },
      "description": "站点信息", "isPublic": 1, "updateTime": "2026-10-29T02:00:00.000Z"
    }
  ],
  "errors": []
}
```

仅允许公开的 site 且 isPublic=1；服务端过滤，configKey ASC。

---

<a id="cv-group-2"></a>

### 4.2 主人资料与社交链接

<a id="cv-api-88"></a>

#### 4.2.1 查询站点主人公开资料

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/cv/profile` |
| 接口描述 | 查询站点主人公开资料 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

##### 请求参数

无业务参数。

##### 请求示例

```http
GET /api/v1/cv/profile
```

##### 响应参数

`data`：[HomeProfilePublic](#response-homeprofilepublic)，未配置/无记录时可为 null。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "nickname": "落叶", "tagline": "记录与分享", "description": null, "avatar": null,
    "website": "https://example.test", "publicEmail": null
  },
  "errors": []
}
```

固定profileId=1。展示资料与登录资料独立，只有publicEmail可公开；禁止从sys_user.email自动补全。头像和URL遵守公开资源约束。未配置时200、data=null；博客和CV复用本入口。

---

<a id="cv-api-91"></a>

#### 4.2.2 查询公开社交链接

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/cv/social-links` |
| 接口描述 | 查询公开社交链接 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

##### 请求参数

无业务参数。

##### 请求示例

```http
GET /api/v1/cv/social-links
```

##### 响应参数

`data`：[SocialPublic](#response-socialpublic) 数组。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": [
    {
      "socialId": "21", "name": "GitHub", "icon": "github", "url": "https://github.com/example",
      "sortOrder": 0
    }
  ],
  "errors": []
}
```

仅status=1，sortOrder ASC,socialId ASC；无数据[]。mailto仅接受单一合法邮箱，不接受额外查询参数或控制字符。

---

<a id="cv-group-3"></a>

### 4.3 简历经历与技能

<a id="api-97"></a>

#### 4.3.1 查询公开简历经历

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/cv/experiences` |
| 接口描述 | 查询公开简历经历 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |
| `experienceType` | integer | 否 | 0教育 1工作/实习 2项目 |

##### 请求示例

```http
GET /api/v1/cv/experiences?page=1&pageSize=10
```

##### 响应参数

`data`：分页对象，`records[]` 为 [ExperiencePublic](#response-experiencepublic)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "experienceId": "31", "experienceType": 0, "title": "示例大学", "subtitle": "计算机科学", "logoUrl": null,
        "startDate": "2022-09-01"
      }
    ]
  },
  "errors": []
}
```

仅status=1；sortOrder ASC,startDate DESC,experienceId DESC。不返回原始Markdown。

---

<a id="api-103"></a>

#### 4.3.2 查询公开简历技能

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/cv/skills` |
| 接口描述 | 查询公开简历技能 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |

##### 请求示例

```http
GET /api/v1/cv/skills?page=1&pageSize=10
```

##### 响应参数

`data`：分页对象，`records[]` 为 [SkillPublic](#response-skillpublic)；分页字段见 [0.2](#pagination)。

##### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "skillId": "41", "name": "Java", "description": "后端开发", "icon": null, "sortOrder": 0
      }
    ]
  },
  "errors": []
}
```

仅status=1；sortOrder ASC,skillId ASC。description用于熟练度等文字说明，没有数据库未定义的评分列。

---

<a id="cv-group-4"></a>

### 4.4 浏览统计采集

<a id="cv-api-137"></a>

#### 4.4.1 记录站点页面浏览

##### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/cv/analytics/page-views` |
| 接口描述 | 记录站点页面浏览 |
| Content-Type | application/json |
| 访问要求 | 公开；可选Bearer，携带时必须校验有效会话和账号状态 |

##### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fingerprint` | string | 是 | ASCII字母数字或连字符16..150；浏览器随机统计标识，可伪造，不能作为身份 |
| `siteCode` | string | 是 | home/cv/cv；服务端与明确配置的来源站点核验 |
| `pagePath` | S(2048) | 是 | 规范站内路径，必须以/起始，剔除fragment及全部query |
| `pageTitle` | S(200)? | 是，可null | 展示标题，不作为鉴权或资源定位依据 |
| `referer` | URL? | 是，可null | 来源URL；服务端再次脱敏并去掉query/fragment |
| `articleId` | ID&#124;null | 是，可null | 仅blog文章详情可传；需PUBLIC且与pagePath的规范slug匹配 |

##### 请求示例

```http
POST /api/v1/cv/analytics/page-views
Content-Type: application/json

{
  "fingerprint": "d681988a-cbd0-4ef6-bdec-f84bfa234e16", "siteCode": "home", "pagePath": "/",
  "pageTitle": "DriftingLeaves", "referer": null, "articleId": null
}
```

##### 响应参数

`data`：[TrackingResult](#response-trackingresult)。

##### 响应示例

```json
{"code":"OK","msg":"成功","data":{"recorded":true},"errors":[]}
```

IP/UA、viewTime和userId由服务端读取；不接受visitorId、用户ID、会话ID或Token字段。携带Bearer时必须有效，匿名不创建sys_user。事务写访客首次/最近时间及一条page_view后返回recorded=true；失败503不伪造已记录。该POST不增加blog_article.view_count，文章成功详情读取仍按7.2计一次，避免双计。受限统计标识返回403/VISITOR_BLOCKED，只影响此采集入口；可伪造标识不能用于阻止公开阅读。重复POST可能产生多条浏览，无通用幂等键。

已实现的[采集限流](#request-protection)在访客创建、地域解析与明细写入前执行；429不得写入数据或返回recorded=true。fingerprint只增加统计维度限制，不能代替独立IP限流；本POST响应不缓存。

`siteCode` 必须等于 `cv`，否则返回 400/VALIDATION_ERROR；后续仍校验 Origin 与配置的站点路径。

---

<a id="cv-group-5"></a>

### 4.5 实时在线人数

<a id="cv-api-160"></a>

#### 4.5.1 申请一次性在线票据

| 项目 | 约定 |
| --- | --- |
| 接口 | `POST /api/v1/cv/analytics/online/tickets` |
| 身份 | 公开、可选 Bearer；携带时必须属于启用账号的有效本站会话 |
| 成功 | HTTP 201，`Cache-Control: no-store`，无版本头 |
| 请求体 | `fingerprint`：ASCII `[A-Za-z0-9-]{16,150}`；`siteCode`：`home/cv/cv`；`pagePath`：S(2048)站内路径，不能包含查询/fragment |
| 来源 | 必须提供 Origin，在 `app.auth.allowed-origins` 内且匹配 `app.sites` 的站点与最长路径前缀 |
| 参数 | 三个 Body 字段均必填；拒绝查询参数、未知/重复字段和类型错误 |
| 限流 | 60秒每可信IP120次、每站点/Origin/fingerprint统计键30次；429带Retry-After |

```http
POST /api/v1/cv/analytics/online/tickets
Origin: https://cv.example.test
Authorization: Bearer <site-token>
Content-Type: application/json

{"fingerprint":"d681988a-cbd0-4ef6-bdec-f84bfa234e16","siteCode":"cv","pagePath":"/"}
```

匿名请求省略 Authorization；Token 的持久化和刷新恢复遵守通用协议，仅经 HTTP Header 发送。示例 Origin 必须在部署中明确允许。

```json
{"code":"OK","msg":"成功","data":{"ticket":"<43-character-single-use-ticket>","expiresIn":30},"errors":[]}
```

票据随机且只用于一次 join，Redis TTL 30 秒，申请本身不增加人数。无效会话401、禁用账号403/ACCOUNT_DISABLED、来源/站点不匹配403/FORBIDDEN；Redis或身份查询不可用503，不降级匿名签发。

`siteCode` 必须等于 `cv`，否则返回 400/VALIDATION_ERROR；后续仍校验 Origin 与配置的站点路径。

---

<a id="cv-online-websocket"></a>

#### 4.5.2 加入原生 WebSocket 并接收快照

`ws(s)://<API主机>/api/v1/cv/ws/online`；从既有 API 地址推导，只映射协议并追加 `/cv/ws/online`。握手只允许精确 GET Upgrade 和明确 Origin，拒绝查询参数、Authorization 和子协议传身份；普通 GET 返回400。Cookie 不证明身份。建连后五秒内发送：

```json
{"type":"join","ticket":"<43-character-single-use-ticket>"}
```

票据绑定 Origin，原会话与启用账号在 join 时重查，完整载荷比较后原子消费；同一连接只能加入一次。成功立即收到快照，后续人数变化广播：

```json
{"type":"online.count","code":"OK","msg":"成功","data":{"onlineCount":"8","userCount":"3","guestCount":"5","serverTime":"2026-11-01T02:00:00.000Z"},"errors":[]}
```

三项人数均为非负、无前导零的十进制字符串。登录账号按本站 userId 去重；匿名按已经核验的站点、Origin、fingerprint 去重，同键存在有效登录连接时不重复计访客。`onlineCount=userCount+guestCount`。不返回用户、IP、连接清单或凭据。

客户端每25秒发送 `{"type":"ping"}`，服务端单发 `{"type":"pong","code":"OK","msg":"成功","data":null,"errors":[]}`。10秒等不到pong则关闭旧连接并重签票据；服务端75秒未收到合法心跳即过期，每5秒回收。每25秒重查原登录会话与账号，正常依赖条件下撤销最晚约30秒移除；不会因认证超过300秒误踢，也不续期Token。

单消息最多1KiB，只接受声明的JSON文本字段；二进制/未知/重复字段、重复join、加入前ping等拒绝。错误消息 `type=error`、`data=null`；其余包络保持 `code/msg/errors`。票据无效用 `ONLINE_TICKET_INVALID`，身份失效用 `UNAUTHENTICATED/ACCOUNT_DISABLED`，协议错误用 `VALIDATION_ERROR`，频率超限用 `RATE_LIMITED`，依赖失败用 `SERVICE_UNAVAILABLE`。关闭码分别为1008（策略）、1009（超大帧）、4000（心跳）、1011（服务失败）、1000（主动离开）。协议错停止自动重试，频率超限至少等待60秒；其他断线使用1/2/5/10/30秒退避及少量随机延迟，HTTP429尊重Retry-After。

Blog连接由实际 `view/Layout/index.vue` 管理，站点统计卡片只读取快照；密码/OAuth的 `acceptSession` 和 `clearSession` 统一重连。初始显示连接中，失败显示中断，不把错误或旧快照当作实时0人数。Home/Cv仅匿名登记、没有人数展示；三个页脚和后台均不展示。

仅接受当前端签发且 Origin 匹配的票据；跨端使用返回 ONLINE_TICKET_INVALID。

---

## 共享业务规则

以下规则适用于上述对应业务，不改变各端路径和权限。历史章节锚点继续保留，便于既有链接查阅。

<a id="group-7"></a>

### 7. 文章与归档

前台仅展示公开文章；后台提供草稿、编辑、发布/下架、回收与恢复。常用筛选和删除流程见 [7.12](#article-scenarios)。

<a id="group-8"></a>

### 8. 分类与标签

分类与标签是全局字典。删除分类会清空文章分类，删除标签会清理关联，文章与正文保留。分类隐藏时不在前台字典与分类筛选中暴露，文章仍可公开。

<a id="group-9"></a>

### 9. 评论审核与点赞

评论为纯文本，审核后展示；删除同时物理移除目标和全部子回复，不生成占位。评论创建按用户+IP共用 60 秒 10 次窗口，点赞/取消按用户+IP共用 60 秒 60 次窗口。点赞计数取当前明细，公开评论数只含可见且通过的评论。

<a id="group-10"></a>

### 10. 友链与站点配置

友链仅 status=1 时公开，按 sortOrder/linkId 排序。配置仅支持 site/comment/message：site 按公开标记展示，comment/message 始终私密；不提供任意新增/删除配置键。展示 URL 只用外部 HTTP(S)，不接受本站附件路径。

<a id="group-11"></a>

### 11. 文件与附件

支持图片上传、元数据查询、预览与删除；元数据不可编辑。文件访问由文章公开状态、实际引用及相应权限决定，见 [13.4](#file-consistency)。

<a id="group-15"></a>

### 15. 主页展示与在线简历

主页、博客侧栏与简历共用站点主人资料；公开接口仅返回展示字段。后台操作要求对应权限的 ALL 范围，不因站点主人身份自动赋权。

<a id="group-16"></a>

### 16. 留言板、本人留言与审核

留言与文章评论独立；按 parentId 逐层分页，不一次展开整棵线程。本人接口只取会话身份；私密父链、审核与分支删除规则见 [13.3](#business)。

<a id="group-17"></a>

### 17. 背景音乐与播放列表

维护站点曲目元数据；音频、封面、歌词使用外部 HTTP(S) URL。现有图片上传接口不支持音频或歌词上传，也不提供播放资源代理。

<a id="group-18"></a>

### 18. 邮件订阅、RSS Feed 与站点地图

邮件订阅仅能使用当前登录账号邮箱，页面只读且服务端强制核对，仍须收信确认；RSS 与站点地图公开读取，不要求订阅邮箱。订阅操作不修改账号登录邮箱。

<a id="group-19"></a>

### 19. 访客、浏览记录与统计报表

统计标识与本站账号分开。封禁统计标识只限制采集，不等于禁用账号。页面 PV/UV 与文章累计阅读数口径不同，日期按 UTC。

<a id="group-20"></a>

### 20. 操作日志

查询与清理日志均需对应权限的 ALL 范围。清理操作自身会写入审计，不以 admin 角色名绕过授权。

<a id="group-22"></a>

### 22. 文章发现与内容管理补充

**本章后端已实现**：6 个新路由与 1 项前台作者筛选扩展，合计 159 个已实现路由。文章常用调用见 [7.12](#article-scenarios)。本章 GET 不增加阅读量，公开条件与既有接口相同；前端页面尚未建设。

<a id="group-23"></a>

### 23. 实时在线人数

Home、Blog、Cv 建立在线连接参与全站合计，只有 Blog 的“站点统计”卡片展示三项人数。Admin 不参与。首期仅单后端实例，在线成员不写 MySQL，不改变 PV/UV 或文章阅读数。完整去重、消息和生命周期约定见[在线说明](实时在线人数接口规划.md)。

<a id="models"></a>

## 12. 响应模型

以下表格列出完整业务字段，字段名相对于单个业务对象；分页位于 `data.records[]`，数组位于 `data[]`，单对象位于 `data`。分页公共字段见 [0.2](#pagination)，可写字段仍以各接口请求表为准。输入模型、详细状态与开发约束见 [接口开发约定](API_CONTRACT.md#models)。

<a id="response-me"></a>

### 12.1 Me

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `username` | string | 登录名 |
| `nickname` | string | 展示昵称 |
| `email` | string&#124;null | 未验证联系邮箱，可 null |
| `avatar` | string&#124;null | 外部头像 URL，可 null |
| `intro` | string&#124;null | 个人简介，可 null |
| `status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `hasPassword` | boolean | 是否设置有效本站密码，不返回哈希 |
| `lastLoginTime` | Time | 最近登录 UTC 时间，未登录可 null |
| `lastLoginIp` | string&#124;null | 最近成功登录的完整 IP，仅管理端可见；历史缺失为 null |
| `lastLoginLocation` | string&#124;null | 最近登录的省市或国家，回环为本地 |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |

<a id="response-session"></a>

### 12.2 Session

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `tokenType` | string | 固定 Bearer |
| `accessToken` | string | 本站不透明 Token，仅成功登录返回 |
| `expiresIn` | integer | 当前端的会话有效秒数：Blog 为 259200（3 天），Admin 为 604800（7 天） |
| `user` | object | 本人资料 Me |
| `user.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `user.username` | string | 登录名 |
| `user.nickname` | string | 展示昵称 |
| `user.email` | string&#124;null | 未验证联系邮箱，可 null |
| `user.avatar` | string&#124;null | 外部头像 URL，可 null |
| `user.intro` | string&#124;null | 个人简介，可 null |
| `user.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `user.hasPassword` | boolean | 是否设置有效本站密码，不返回哈希 |
| `user.lastLoginTime` | Time | 最近登录 UTC 时间，未登录可 null |
| `user.createTime` | Time | 创建 UTC 时间 |
| `user.updateTime` | Time | 修改 UTC 时间 |

<a id="response-provider"></a>

### 12.3 Provider

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `source` | string | 已配置且启用的平台标识 |
| `displayName` | string | 平台展示名称 |

<a id="response-oauthauthorizeresult"></a>

### 12.4 OAuthAuthorizeResult

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `authorizationUrl` | string | 服务端生成的第三方授权 URL，前端不可自定回调地址 |
| `expiresIn` | integer | 剩余有效秒数 |

<a id="response-oauthresult"></a>

### 12.5 OAuthResult

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `operation` | string | LOGIN 或 BIND |
| `session` | object | LOGIN 分支的本站 Session |
| `session.tokenType` | string | 固定 Bearer |
| `session.accessToken` | string | 本站不透明 Token，仅成功登录返回 |
| `session.expiresIn` | integer | 按兑换端签发：Blog 为 259200（3 天），Admin 为 604800（7 天） |
| `session.user` | object | 本人资料 Me |
| `session.user.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `session.user.username` | string | 登录名 |
| `session.user.nickname` | string | 展示昵称 |
| `session.user.email` | string&#124;null | 未验证联系邮箱，可 null |
| `session.user.avatar` | string&#124;null | 外部头像 URL，可 null |
| `session.user.intro` | string&#124;null | 个人简介，可 null |
| `session.user.status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `session.user.hasPassword` | boolean | 是否设置有效本站密码，不返回哈希 |
| `session.user.lastLoginTime` | Time | 最近登录 UTC 时间，未登录可 null |
| `session.user.createTime` | Time | 创建 UTC 时间 |
| `session.user.updateTime` | Time | 修改 UTC 时间 |
| `binding` | OAuthBinding | 仅 BIND 分支，含 source/createTime；与 session 二选一 |

<a id="response-capabilities"></a>

### 12.6 Capabilities

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `roleCodes` | array | 当前有效角色码，字典序 |
| `permissionCodes` | array | 当前有效权限码，字典序 |
| `dataScopes` | object | 权限码到 ALL/SELF 的映射，不是单个用户全局范围 |

<a id="response-menunode"></a>

### 12.7 MenuNode

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `menuId` | ID | 菜单 ID |
| `parentId` | ID&#124;null | 父节点 ID，根为 null |
| `menuCode` | string | 稳定菜单编码 |
| `menuName` | string | 菜单名 |
| `menuType` | string | D 目录、M 页面、B 按钮 |
| `routeName` | string | 前端路由名，按钮为 null |
| `path` | string | 内部前端路径，非 API 路径，按钮为 null |
| `component` | string | 前端组件白名单键，按钮为 null |
| `icon` | string | 图标白名单键，可 null |
| `permissionId` | ID&#124;null | 权限 ID；目录节点为 null |
| `sortOrder` | integer | int32 升序排序值 |
| `isVisible` | integer | 0 隐藏、1 显示；隐藏不撤权 |
| `keepAlive` | integer | 仅 M 页面可为 1 |
| `status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |
| `permissionCode` | string&#124;null | 稳定接口权限码，目录节点为 null |
| `children` | array | 子菜单，叶子 []，同 MenuNode 递归结构 |
| `children[].menuId` | ID | 菜单 ID |
| `children[].parentId` | ID | 父节点 ID，根为 null |
| `children[].menuCode` | string | 稳定菜单编码 |
| `children[].menuName` | string | 菜单名 |
| `children[].menuType` | string | D 目录、M 页面、B 按钮 |
| `children[].routeName` | string | 前端路由名，按钮为 null |
| `children[].path` | string | 内部前端路径，非 API 路径，按钮为 null |
| `children[].component` | string | 前端组件白名单键，按钮为 null |
| `children[].icon` | string | 图标白名单键，可 null |
| `children[].permissionId` | ID | 权限 ID；目录节点为 null |
| `children[].sortOrder` | integer | int32 升序排序值 |
| `children[].isVisible` | integer | 0 隐藏、1 显示；隐藏不撤权 |
| `children[].keepAlive` | integer | 仅 M 页面可为 1 |
| `children[].status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `children[].createTime` | Time | 创建 UTC 时间 |
| `children[].updateTime` | Time | 修改 UTC 时间 |
| `children[].permissionCode` | string | 稳定接口权限码，目录节点为 null |
| `children[].children` | array | 子菜单，叶子 []，同 MenuNode 递归结构 |

<a id="response-oauthbinding"></a>

### 12.8 OAuthBinding

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `source` | string | 已配置且启用的平台标识 |
| `createTime` | Time | 创建 UTC 时间 |

<a id="response-useradmin"></a>

### 12.9 UserAdmin（3.1）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `username` | string | 登录名 |
| `nickname` | string | 展示昵称 |
| `email` | string&#124;null | 未验证联系邮箱，可 null |
| `avatar` | string&#124;null | 外部头像 URL，可 null |
| `intro` | string&#124;null | 个人简介，可 null |
| `status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `hasPassword` | boolean | 是否设置有效本站密码，不返回哈希 |
| `lastLoginTime` | Time | 最近登录 UTC 时间，未登录可 null |
| `lastLoginIp` | string&#124;null | 最近成功登录的完整 IP，仅管理端可见；历史缺失为 null |
| `lastLoginLocation` | string&#124;null | 最近登录的省市或国家，回环为本地 |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |
| `roleIds` | ID[] | 已分配角色 ID 集合，包含禁用角色 |

<a id="response-useradmin-2"></a>

### 12.10 UserAdmin（3.3）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `username` | string | 登录名 |
| `nickname` | string | 展示昵称 |
| `email` | string&#124;null | 未验证联系邮箱，可 null |
| `avatar` | string&#124;null | 外部头像 URL，可 null |
| `intro` | string&#124;null | 个人简介，可 null |
| `status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `hasPassword` | boolean | 是否设置有效本站密码，不返回哈希 |
| `lastLoginTime` | string&#124;null | 最近登录 UTC 时间，未登录可 null |
| `lastLoginIp` | string&#124;null | 最近成功登录的完整 IP，仅管理端可见；历史缺失为 null |
| `lastLoginLocation` | string&#124;null | 最近登录的省市或国家，回环为本地 |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |
| `roleIds` | ID[] | 已分配角色 ID 集合，包含禁用角色 |

<a id="response-role"></a>

### 12.11 Role

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `roleId` | ID | 角色 ID |
| `roleCode` | string | 稳定角色码，不带 ROLE_ 前缀 |
| `roleName` | string | 角色名称 |
| `description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
| `dataScope` | string | ALL 或 SELF；按当前操作权限计算 |
| `status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |
| `permissionIds` | ID[] | 授权的权限 ID，升序，含禁用项 |
| `menuIds` | ID[] | 授权的菜单 ID，升序，含禁用项 |

<a id="response-permission"></a>

### 12.12 Permission

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `permissionId` | ID | 权限 ID |
| `permissionCode` | string | 稳定接口权限码 |
| `permissionName` | string | 权限显示名 |
| `module` | string | 权限模块 |
| `description` | string&#124;null | 说明，可 null；空白归一为 null |
| `status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |

<a id="response-menu"></a>

### 12.13 Menu

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `menuId` | ID | 菜单 ID |
| `parentId` | ID | 父节点 ID，根为 null |
| `menuCode` | string | 稳定菜单编码 |
| `menuName` | string | 菜单名 |
| `menuType` | string | D 目录、M 页面、B 按钮 |
| `routeName` | string | 前端路由名，按钮为 null |
| `path` | string | 内部前端路径，非 API 路径，按钮为 null |
| `component` | string | 前端组件白名单键，按钮为 null |
| `icon` | string | 图标白名单键，可 null |
| `permissionId` | ID | 权限 ID；目录节点为 null |
| `sortOrder` | integer | int32 升序排序值 |
| `isVisible` | integer | 0 隐藏、1 显示；隐藏不撤权 |
| `keepAlive` | integer | 仅 M 页面可为 1 |
| `status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |

<a id="response-articlepublic"></a>

### 12.14 ArticlePublic

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `articleId` | ID | 文章 ID |
| `author` | object | 作者公开资料；删除占位评论为 null |
| `author.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `author.nickname` | string | 展示昵称 |
| `author.avatar` | string&#124;null | 外部头像 URL，可 null |
| `category` | object&#124;null | 当前分类，未设置或前台隐藏时 null |
| `title` | string | 文章或站点标题 |
| `slug` | string | 稳定 URL 标识，区分大小写 |
| `summary` | string | 文章摘要，可空串 |
| `coverUrl` | string&#124;null | 封面 URL，可 null |
| `articleType` | integer | 1 原创、2 转载、3 翻译 |
| `originalUrl` | string&#124;null | 原文 URL，可 null |
| `isTop` | integer | 0 普通、1 置顶 |
| `allowComment` | integer | 文章是否允许评论；服务端还检查站点开关 |
| `viewCount` | string | 浏览计数字符串 |
| `likeCount` | string | 当前点赞明细计数字符串 |
| `commentCount` | string | 当前可见且通过的评论计数字符串 |
| `publishTime` | Time | 首次发布时间，未发布可 null |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |
| `tags` | array | 标签对象数组，tagId 升序 |

<a id="response-articlepublicdetail"></a>

### 12.15 ArticlePublicDetail

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `articleId` | ID | 文章 ID |
| `author` | object | 作者公开资料；删除占位评论为 null |
| `author.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `author.nickname` | string | 展示昵称 |
| `author.avatar` | string&#124;null | 外部头像 URL，可 null |
| `category` | object&#124;null | 当前分类，未设置或前台隐藏时 null |
| `title` | string | 文章或站点标题 |
| `slug` | string | 稳定 URL 标识，区分大小写 |
| `summary` | string | 文章摘要，可空串 |
| `coverUrl` | string&#124;null | 封面 URL，可 null |
| `articleType` | integer | 1 原创、2 转载、3 翻译 |
| `originalUrl` | string&#124;null | 原文 URL，可 null |
| `isTop` | integer | 0 普通、1 置顶 |
| `allowComment` | integer | 文章是否允许评论；服务端还检查站点开关 |
| `viewCount` | string | 浏览计数字符串 |
| `likeCount` | string | 当前点赞明细计数字符串 |
| `commentCount` | string | 当前可见且通过的评论计数字符串 |
| `publishTime` | Time | 首次发布时间，未发布可 null |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |
| `tags` | array | 标签对象数组，tagId 升序 |
| `htmlContent` | string | 服务端渲染并清理的 HTML |
| `wordCount` | string | 可见正文派生字数，见 [文章导航与阅读统计](API_CONTRACT.md#model-12-15) |
| `readingMinutes` | integer | max(1,ceil(wordCount/300)) |
| `prevArticle` / `nextArticle` | ArticleNavigation&#124;null | 更早/更晚的相邻PUBLIC文章 |
| `prevArticle.articleId` / `nextArticle.articleId` | ID | 非空导航的文章ID |
| `prevArticle.title` / `nextArticle.title` | string | 非空导航的标题 |
| `prevArticle.slug` / `nextArticle.slug` | string | 非空导航的slug |
| `relatedArticles` | ArticleNavigation[] | 最多6篇同分类PUBLIC文章，元素也只有articleId/title/slug |

<a id="response-archive"></a>

### 12.16 Archive

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `month` | string | UTC 年月 YYYY-MM |
| `articleCount` | string | 该月公开文章数，字符串 |

<a id="response-articleadminsummary"></a>

### 12.17 ArticleAdminSummary

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `articleId` | ID | 文章 ID |
| `authorId` | ID | 文章作者 ID |
| `author` | object | 作者公开资料；删除占位评论为 null |
| `author.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `author.nickname` | string | 展示昵称 |
| `category` | object&#124;null | 当前分类，未设置或前台隐藏时 null |
| `title` | string | 文章或站点标题 |
| `slug` | string | 稳定 URL 标识，区分大小写 |
| `summary` | string | 文章摘要，可空串 |
| `coverUrl` | string&#124;null | 封面 URL，可 null |
| `articleType` | integer | 1 原创、2 转载、3 翻译 |
| `originalUrl` | string&#124;null | 原文 URL，可 null |
| `status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `visibility` | integer | 1 公开、2 私密 |
| `isTop` | integer | 0 普通、1 置顶 |
| `allowComment` | integer | 文章是否允许评论；服务端还检查站点开关 |
| `viewCount` | string | 浏览计数字符串 |
| `likeCount` | string | 当前点赞明细计数字符串 |
| `commentCount` | string | 当前可见且通过的评论计数字符串 |
| `publishTime` | string&#124;null | 首次发布时间，未发布可 null |
| `deletedAt` | string&#124;null | 回收时间，正常为 null |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |
| `tags` | array | 标签对象数组，tagId 升序 |

<a id="response-articleadmindetail"></a>

### 12.18 ArticleAdminDetail（7.5）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `articleId` | ID | 文章 ID |
| `authorId` | ID | 文章作者 ID |
| `author` | object | 作者公开资料；删除占位评论为 null |
| `author.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `author.nickname` | string | 展示昵称 |
| `category` | object&#124;null | 当前分类，未设置或前台隐藏时 null |
| `title` | string | 文章或站点标题 |
| `slug` | string | 稳定 URL 标识，区分大小写 |
| `summary` | string | 文章摘要，可空串 |
| `coverUrl` | string&#124;null | 封面 URL，可 null |
| `articleType` | integer | 1 原创、2 转载、3 翻译 |
| `originalUrl` | string&#124;null | 原文 URL，可 null |
| `status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `visibility` | integer | 1 公开、2 私密 |
| `isTop` | integer | 0 普通、1 置顶 |
| `allowComment` | integer | 文章是否允许评论；服务端还检查站点开关 |
| `viewCount` | string | 浏览计数字符串 |
| `likeCount` | string | 当前点赞明细计数字符串 |
| `commentCount` | string | 当前可见且通过的评论计数字符串 |
| `publishTime` | string&#124;null | 首次发布时间，未发布可 null |
| `deletedAt` | string&#124;null | 回收时间，正常为 null |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |
| `tags` | array | 标签对象数组，tagId 升序 |
| `categoryId` | ID&#124;null | 分类 ID，可 null |
| `tagIds` | ID[] | 标签 ID 数组，升序 |
| `markdownContent` | string | Markdown 源文，仅后台详情返回 |
| `htmlContent` | string | 服务端渲染并清理的 HTML |

<a id="response-articleadmindetail-2"></a>

### 12.19 ArticleAdminDetail（7.8）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `articleId` | ID | 文章 ID |
| `authorId` | ID | 文章作者 ID |
| `author` | object | 作者公开资料；删除占位评论为 null |
| `author.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `author.nickname` | string | 展示昵称 |
| `category` | object&#124;null | 当前分类，未设置或前台隐藏时 null |
| `title` | string | 文章或站点标题 |
| `slug` | string | 稳定 URL 标识，区分大小写 |
| `summary` | string | 文章摘要，可空串 |
| `coverUrl` | string&#124;null | 封面 URL，可 null |
| `articleType` | integer | 1 原创、2 转载、3 翻译 |
| `originalUrl` | string&#124;null | 原文 URL，可 null |
| `status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `visibility` | integer | 1 公开、2 私密 |
| `isTop` | integer | 0 普通、1 置顶 |
| `allowComment` | integer | 文章是否允许评论；服务端还检查站点开关 |
| `viewCount` | string | 浏览计数字符串 |
| `likeCount` | string | 当前点赞明细计数字符串 |
| `commentCount` | string | 当前可见且通过的评论计数字符串 |
| `publishTime` | Time | 首次发布时间，未发布可 null |
| `deletedAt` | string&#124;null | 回收时间，正常为 null |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |
| `tags` | array | 标签对象数组，tagId 升序 |
| `categoryId` | ID&#124;null | 分类 ID，可 null |
| `tagIds` | ID[] | 标签 ID 数组，升序 |
| `markdownContent` | string | Markdown 源文，仅后台详情返回 |
| `htmlContent` | string | 服务端渲染并清理的 HTML |

<a id="response-hotarticle"></a>

### 12.20 HotArticle

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `articleId` | ID | PUBLIC文章ID |
| `title` | string | 标题 |
| `slug` | string | URL标识 |
| `summary` | string | 摘要 |
| `coverUrl` | URL? | 外部公开封面 |
| `viewCount` | string | 文章累计阅读数 |
| `likeCount` | string | 当前点赞明细数 |
| `hotValue` | string | 按metric选择的累计阅读数或点赞数 |
| `publishTime` | Time | 首次发布时间 |

<a id="response-categorypublic"></a>

### 12.21 CategoryPublic

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `categoryId` | ID | 分类 ID，可 null |
| `categoryName` | string | 分类名称 |
| `slug` | string | 稳定 URL 标识，区分大小写 |
| `description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
| `sortOrder` | integer | int32 升序排序值 |

<a id="response-category"></a>

### 12.22 Category

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `categoryId` | ID | 分类 ID，可 null |
| `categoryName` | string | 分类名称 |
| `slug` | string | 稳定 URL 标识，区分大小写 |
| `description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
| `sortOrder` | integer | int32 升序排序值 |
| `status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |

<a id="response-tagpublic"></a>

### 12.23 TagPublic

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `tagId` | ID | 标签 ID |
| `tagName` | string | 标签名称 |
| `slug` | string | 稳定 URL 标识，区分大小写 |

<a id="response-tag"></a>

### 12.24 Tag

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `tagId` | ID | 标签 ID |
| `tagName` | string | 标签名称 |
| `slug` | string | 稳定 URL 标识，区分大小写 |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |

<a id="response-commentpublic"></a>

### 12.25 CommentPublic

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `commentId` | ID | 评论 ID |
| `articleId` | ID | 文章 ID |
| `parentId` | ID&#124;null | 父节点 ID，根为 null |
| `content` | string | 纯文本评论；占位固定“该评论已删除” |
| `location` | string&#124;null | 发布时的省市或国家；回环为本地，旧记录或占位可 null |
| `userAgentOs` | string&#124;null | 从请求 User-Agent 识别的操作系统类型，未知或占位为 null |
| `userAgentBrowser` | string&#124;null | 浏览器类型，未知或占位为 null；不公开原始 User-Agent/IP |
| `status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |
| `author` | object | 作者公开资料；删除占位评论为 null |
| `author.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `author.nickname` | string | 展示昵称 |
| `author.avatar` | string&#124;null | 外部头像 URL，可 null |
| `replyCount` | string | 当前可见直接回复数，字符串 |

<a id="response-commentown"></a>

### 12.26 CommentOwn

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `commentId` | ID | 评论 ID |
| `articleId` | ID | 文章 ID |
| `userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `parentId` | ID&#124;null | 父节点 ID，根为 null |
| `content` | string | 纯文本评论；占位固定“该评论已删除” |
| `location` | string&#124;null | 发布时的省市或国家；回环为本地，旧记录或占位可 null |
| `userAgentOs` | string&#124;null | 从请求 User-Agent 识别的操作系统类型，未知或占位为 null |
| `userAgentBrowser` | string&#124;null | 浏览器类型，未知或占位为 null；不公开原始 User-Agent/IP |
| `status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |

<a id="response-commentadmin"></a>

### 12.27 CommentAdmin

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `commentId` | ID | 评论 ID |
| `articleId` | ID | 文章 ID |
| `userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `parentId` | ID&#124;null | 父节点 ID，根为 null |
| `content` | string | 纯文本评论；占位固定“该评论已删除” |
| `location` | string&#124;null | 发布时的省市或国家；回环为本地，旧记录或占位可 null |
| `userAgentOs` | string&#124;null | 从请求 User-Agent 识别的操作系统类型，未知或占位为 null |
| `userAgentBrowser` | string&#124;null | 浏览器类型，未知或占位为 null；不公开原始 User-Agent/IP |
| `status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |
| `author` | object | 作者公开资料；删除占位评论为 null |
| `author.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `author.nickname` | string | 展示昵称 |
| `author.avatar` | string&#124;null | 外部头像 URL，可 null |
| `articleTitle` | string | 所属文章标题 |

<a id="response-likestate"></a>

### 12.28 LikeState

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `liked` | boolean | 当前用户是否点赞 |
| `likeCount` | string | 当前点赞明细计数字符串 |

<a id="response-linkpublic"></a>

### 12.29 LinkPublic

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `linkId` | ID | 友链 ID |
| `linkName` | string | 友链名称 |
| `url` | string | 外部 http(s) 地址 |
| `avatar` | string&#124;null | 外部头像 URL，可 null |
| `description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
| `sortOrder` | integer | int32 升序排序值 |

<a id="response-link"></a>

### 12.30 Link

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `linkId` | ID | 友链 ID |
| `linkName` | string | 友链名称 |
| `url` | string | 外部 http(s) 地址 |
| `avatar` | string&#124;null | 外部头像 URL，可 null |
| `description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
| `sortOrder` | integer | int32 升序排序值 |
| `status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |

<a id="response-config"></a>

### 12.31 Config

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `configKey` | string | site、comment 或 message |
| `configValue` | object | 严格白名单配置对象，具体形状见配置模型 |
| `configValue.title` | string | 文章或站点标题 |
| `configValue.description` | string | 说明，可 null；site 内 description 可为空串 |
| `configValue.icp` | string | 备案文本，可空串 |
| `configValue.publicSecurityRecord` | string | site公安备案文本，可空串 |
| `configValue.startDate` | date&#124;null | site建站日期，未设置为null |
| `configValue.enabled` | boolean | 仅comment/message对象的业务开关 |
| `configValue.reviewRequired` | boolean | 仅comment/message对象，是否默认审核；不向公开接口输出 |
| `description` | string | 说明，可 null；site 内 description 可为空串 |
| `isPublic` | integer | 0 私有、1 可公开，公开接口还校验键白名单 |
| `updateTime` | Time | 修改 UTC 时间 |

<a id="response-file"></a>

### 12.32 File

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `fileId` | ID | 文件 ID |
| `uploaderId` | ID | 本站上传者 ID |
| `originalName` | string | 安全化展示文件名，无路径 |
| `mimeType` | string | 服务端识别的 image/png 或 image/jpeg |
| `sizeBytes` | string | 文件实际字节数字符串 |
| `storageType` | string | 当前默认 oss，历史 local 按原元数据读取 |
| `createTime` | Time | 创建 UTC 时间 |
| `previewUrl` | string | 需 Bearer 的后台文件内容路径 |

<a id="response-homeprofilepublic"></a>

### 12.33 HomeProfilePublic

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `nickname` | S(64) | 公开昵称 |
| `tagline` | S0(100) | 个人标签或一句话介绍 |
| `description` | S(500)? | 公开简介，null清空 |
| `avatar` | URL? | 公开头像，外部HTTP(S)资源 |
| `website` | URL? | 个人网站 |
| `publicEmail` | string&#124;null | ≤254，合法联系邮箱；明确自愿公开，不自动读取登录邮箱 |
| `githubUrl` | URL? | GitHub主页 |
| `location` | S(100)? | 自愿公开的所在地 |

<a id="response-homeprofile"></a>

### 12.34 HomeProfile

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `profileId` | ID | 固定为1 |
| `userId` | ID | 站点主人账号；必须存在且启用，仅后台管理者可选择 |
| `nickname` | S(64) | 公开昵称 |
| `tagline` | S0(100) | 个人标签或一句话介绍 |
| `description` | S(500)? | 公开简介，null清空 |
| `avatar` | URL? | 公开头像，外部HTTP(S)资源 |
| `website` | URL? | 个人网站 |
| `publicEmail` | string&#124;null | ≤254，合法联系邮箱；明确自愿公开，不自动读取登录邮箱 |
| `githubUrl` | URL? | GitHub主页 |
| `location` | S(100)? | 自愿公开的所在地 |
| `createTime` | Time | 创建时间，UTC |
| `updateTime` | Time | 最后更新时间，UTC |

<a id="response-socialpublic"></a>

### 12.35 SocialPublic

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `socialId` | ID | 社交链接ID |
| `name` | S(64) | 社交平台或链接名称 |
| `icon` | string&#124;null | ≤2048；外部HTTP(S)图标URL或前端注册图标键，禁止任意HTML |
| `url` | URL | 外部HTTP(S)目标链接，邮箱链接可使用经过校验的mailto:邮箱 |
| `sortOrder` | int32 | 越小越靠前 |

<a id="response-social"></a>

### 12.36 Social

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `socialId` | ID | 社交链接ID |
| `name` | S(64) | 社交平台或链接名称 |
| `icon` | string&#124;null | ≤2048；外部HTTP(S)图标URL或前端注册图标键，禁止任意HTML |
| `url` | URL | 外部HTTP(S)目标链接，邮箱链接可使用经过校验的mailto:邮箱 |
| `sortOrder` | int32 | 越小越靠前 |
| `status` | integer | 0隐藏 1展示 |
| `createTime` | Time | 创建时间，UTC |
| `updateTime` | Time | 最后更新时间，UTC |

<a id="response-experiencepublic"></a>

### 12.37 ExperiencePublic

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `experienceId` | ID | 经历ID |
| `experienceType` | integer | 0教育 1工作/实习 2项目 |
| `title` | S(200) | 学校、公司或项目名称 |
| `subtitle` | S(200)? | 专业、职位或项目角色 |
| `logoUrl` | URL? | 外部HTTP(S)图标URL |
| `startDate` | date | 合法YYYY-MM-DD，1000..9999年 |
| `endDate` | date&#124;null | 结束日期不得早于开始；null表示至今 |
| `sortOrder` | int32 | 越小越靠前 |
| `htmlContent` | string | 安全渲染HTML |

<a id="response-experience"></a>

### 12.38 Experience

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `experienceId` | ID | 经历ID |
| `experienceType` | integer | 0教育 1工作/实习 2项目 |
| `title` | S(200) | 学校、公司或项目名称 |
| `subtitle` | S(200)? | 专业、职位或项目角色 |
| `logoUrl` | URL? | 外部HTTP(S)图标URL |
| `content` | string | 非空Markdown，UTF-8最多65535字节，与MySQL TEXT容量一致 |
| `startDate` | date | 合法YYYY-MM-DD，1000..9999年 |
| `endDate` | date&#124;null | 结束日期不得早于开始；null表示至今 |
| `sortOrder` | int32 | 越小越靠前 |
| `status` | integer | 0隐藏 1展示 |
| `createTime` | Time | 创建时间，UTC |
| `updateTime` | Time | 最后更新时间，UTC |

<a id="response-skillpublic"></a>

### 12.39 SkillPublic

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `skillId` | ID | 技能ID |
| `name` | S(64) | 技能名称 |
| `description` | S(500)? | 技能描述 |
| `icon` | string&#124;null | ≤2048；外部HTTP(S)URL或注册图标键 |
| `sortOrder` | int32 | 越小越靠前 |

<a id="response-skill"></a>

### 12.40 Skill

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `skillId` | ID | 技能ID |
| `name` | S(64) | 技能名称 |
| `description` | S(500)? | 技能描述 |
| `icon` | string&#124;null | ≤2048；外部HTTP(S)URL或注册图标键 |
| `sortOrder` | int32 | 越小越靠前 |
| `status` | integer | 0隐藏 1展示 |
| `createTime` | Time | 创建时间，UTC |
| `updateTime` | Time | 最后更新时间，UTC |

<a id="response-messagepublic"></a>

### 12.41 MessagePublic

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `messageId` | ID | 留言ID |
| `parentId` | ID&#124;null | 直接回复目标；根留言为null |
| `author` | object&#124;null | 当前用户公开资料；删除占位时为null |
| `author.userId` | ID | 本站用户ID |
| `author.nickname` | string | 当前昵称 |
| `author.avatar` | URL&#124;null | 当前公开头像 |
| `content` | string | 原文；删除占位固定为该留言已删除 |
| `htmlContent` | string | 安全渲染HTML；纯文本转义并保留换行 |
| `contentFormat` | integer | 0纯文本 1Markdown |
| `isSecret` | integer | 0公开 1悄悄话 |
| `status` | integer | 0待审核 1通过 2驳回 3删除占位 |
| `editedAt` | Time&#124;null | 最近作者编辑时间，未编辑为null |
| `isOwn` | boolean | 按当前会话计算，匿名恒为false |
| `createTime` | Time | 创建时间，UTC |
| `updateTime` | Time | 最后更新时间，UTC |

<a id="response-messageprivate"></a>

### 12.42 MessagePrivate

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `messageId` | ID | 留言ID |
| `parentId` | ID&#124;null | 直接回复目标；根留言为null |
| `author` | object&#124;null | 当前用户公开资料；删除占位时为null |
| `author.userId` | ID | 本站用户ID |
| `author.nickname` | string | 当前昵称 |
| `author.avatar` | URL&#124;null | 当前公开头像 |
| `content` | string | 原文；删除占位固定为该留言已删除 |
| `htmlContent` | string | 安全渲染HTML；纯文本转义并保留换行 |
| `contentFormat` | integer | 0纯文本 1Markdown |
| `isSecret` | integer | 0公开 1悄悄话 |
| `status` | integer | 0待审核 1通过 2驳回 3删除占位 |
| `editedAt` | Time&#124;null | 最近作者编辑时间，未编辑为null |
| `isOwn` | boolean | 按当前会话计算，匿名恒为false |
| `createTime` | Time | 创建时间，UTC |
| `updateTime` | Time | 最后更新时间，UTC |
| `notifyReply` | integer | 0不通知 1请求回复通知；不是邮箱验证凭据 |

<a id="response-musicpublic"></a>

### 12.43 MusicPublic

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `musicId` | ID | 音乐ID |
| `title` | S(200) | 曲目标题 |
| `artist` | S(200)? | 作者或歌手 |
| `durationSeconds` | integer&#124;null | 0..4294967295秒，未知为null |
| `coverUrl` | URL? | 外部HTTP(S)封面 |
| `audioUrl` | URL | 外部HTTP(S)音频URL；保存不触发服务端下载 |
| `lyricUrl` | URL? | 歌词URL；无歌词时为null |
| `lyricType` | string&#124;null | lrc/json/txt；必须与lyricUrl同时为空或同时非空 |
| `sortOrder` | int32 | 越小越靠前 |

<a id="response-music"></a>

### 12.44 Music

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `musicId` | ID | 音乐ID |
| `title` | S(200) | 曲目标题 |
| `artist` | S(200)? | 作者或歌手 |
| `durationSeconds` | integer&#124;null | 0..4294967295秒，未知为null |
| `coverUrl` | URL? | 外部HTTP(S)封面 |
| `audioUrl` | URL | 外部HTTP(S)音频URL；保存不触发服务端下载 |
| `lyricUrl` | URL? | 歌词URL；无歌词时为null |
| `lyricType` | string&#124;null | lrc/json/txt；必须与lyricUrl同时为空或同时非空 |
| `sortOrder` | int32 | 越小越靠前 |
| `status` | integer | 0隐藏 1展示 |
| `createTime` | Time | 创建时间，UTC |
| `updateTime` | Time | 最后更新时间，UTC |

<a id="response-subscription"></a>

### 12.45 Subscription

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `subscriptionId` | ID | 订阅ID |
| `email` | string | 订阅邮箱，≤254；服务端trim并转小写 |
| `status` | integer | 0待验证 1已订阅 2已退订 |
| `verifiedAt` | Time&#124;null | 当前邮箱验证成功时间 |
| `subscribeTime` | Time&#124;null | 最近激活时间 |
| `unsubscribeTime` | Time&#124;null | 最近退订时间 |
| `createTime` | Time | 创建时间，UTC |
| `updateTime` | Time | 最后更新时间，UTC |

<a id="response-subscriptionadmin"></a>

### 12.46 SubscriptionAdmin

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | ID | 所属本站用户 |
| `subscriptionId` | ID | 订阅ID |
| `email` | string | 订阅邮箱，≤254；服务端trim并转小写 |
| `status` | integer | 0待验证 1已订阅 2已退订 |
| `verifiedAt` | Time&#124;null | 当前邮箱验证成功时间 |
| `subscribeTime` | Time&#124;null | 最近激活时间 |
| `unsubscribeTime` | Time&#124;null | 最近退订时间 |
| `createTime` | Time | 创建时间，UTC |
| `updateTime` | Time | 最后更新时间，UTC |

<a id="response-trackingresult"></a>

### 12.47 TrackingResult

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `recorded` | boolean | 数据库成功记录本次浏览后为true，不返回访客会话 |

<a id="response-visitor"></a>

### 12.48 Visitor

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `visitorId` | ID | 统计访客ID，不是登录身份 |
| `fingerprint` | string | ASCII统计标识，不可作为鉴权凭据 |
| `ipAddress` | string&#124;null | 最近IP，仅后台可见 |
| `userAgent` | string&#124;null | ≤1024，最近User-Agent |
| `country` | string&#124;null | 国家 |
| `province` | string&#124;null | 省份 |
| `city` | string&#124;null | 城市 |
| `isBlocked` | integer | 数据库封禁标记0/1；实际生效还需检查blockedUntil |
| `blockedUntil` | Time&#124;null | 封禁结束，已封禁且null表示永久 |
| `firstVisitTime` | Time | 首次访问时间 |
| `lastVisitTime` | Time | 最近访问时间 |
| `createTime` | Time | 创建时间，UTC |
| `updateTime` | Time | 最后更新时间，UTC |

<a id="response-pageview"></a>

### 12.49 PageView

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `viewId` | ID | 浏览记录ID |
| `visitorId` | ID&#124;null | 统计访客，清理后可空 |
| `userId` | ID&#124;null | 采集时服务端确认的本站用户，匿名或清理后为空 |
| `articleId` | ID&#124;null | 通过公开文章与规范路径核验的文章ID |
| `siteCode` | string | home/blog/cv |
| `pagePath` | S(2048) | 不含敏感查询参数的规范路径 |
| `pageTitle` | S(200)? | 页面标题 |
| `referer` | URL? | 已脱敏的来源URL |
| `ipAddress` | string&#124;null | 仅后台可见 |
| `userAgent` | string&#124;null | ≤1024 |
| `viewTime` | Time | 采集时间 |

<a id="response-publicstatistics"></a>

### 12.50 PublicStatistics

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `articleCount` | string | PUBLIC文章数量 |
| `categoryCount` | string | 可展示且至少含一篇PUBLIC文章的分类数 |
| `tagCount` | string | 至少关联一篇PUBLIC文章的标签数 |
| `pageViewCount` | string | blog站点保留浏览明细条数；不等于累计文章阅读数 |
| `todayPageViewCount` | string | UTC当日blog浏览明细数 |
| `visitorCount` | string | blog浏览明细中不同非NULL visitorId数量 |

<a id="response-adminstatistics"></a>

### 12.51 AdminStatistics

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pageViewCount` | string | 筛选日期内保留浏览明细条数 |
| `visitorCount` | string | 筛选日期内不同非NULL visitorId；不按IP或账号去重 |
| `newVisitorCount` | string | firstVisitTime在日期区间且符合站点访问条件的访客数 |
| `articleCount` | string | 当前未回收文章数，不受访问日期筛选 |
| `publishedArticleCount` | string | 当前PUBLIC文章数 |
| `commentCount` | string | 当前非删除占位评论数，含待审/驳回 |
| `messageCount` | string | 当前非删除占位留言数，含私密/待审/驳回 |
| `pendingCommentCount` | string | 当前status=0的评论数 |
| `pendingMessageCount` | string | 当前status=0的留言数 |

<a id="response-viewtrend"></a>

### 12.52 ViewTrend

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `date` | date | UTC自然日，区间内逐日补零 |
| `pageViewCount` | string | 该日浏览明细条数 |
| `visitorCount` | string | 该日不同非NULL visitorId数 |

<a id="response-visitortrend"></a>

### 12.53 VisitorTrend

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `date` | date | UTC自然日 |
| `newVisitorCount` | string | 当天首次访问的访客数 |
| `cumulativeNewVisitorCount` | string | 仅所选区间内从begin累计的新访客数；不是全历史总量 |

<a id="response-provincemetric"></a>

### 12.54 ProvinceMetric

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `province` | string&#124;null | 最新访客省份；null表示未知 |
| `visitorCount` | string | 区间内访问的不同非NULL visitorId数 |

<a id="response-articleviewmetric"></a>

### 12.55 ArticleViewMetric

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `articleId` | ID | 未回收文章ID |
| `title` | string | 当前标题 |
| `slug` | string | 当前slug |
| `pageViewCount` | string | 日期范围内文章浏览明细数，不是view_count累计值 |

<a id="response-operationlog"></a>

### 12.56 OperationLog

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `logId` | ID | 操作日志ID |
| `userId` | ID&#124;null | 操作人；物理清理后为空 |
| `operationType` | string | ≤32，ASCII操作类型 |
| `operationTarget` | string | ≤100，ASCII模块或资源 |
| `targetId` | ID&#124;null | 目标数字ID，其他情况为空 |
| `permissionCode` | string&#124;null | 本次检查的权限码快照 |
| `result` | integer | 0失败 1成功 |
| `errorMessage` | string&#124;null | ≤500，脱敏错误摘要 |
| `ipAddress` | string&#124;null | 操作IP |
| `operationTime` | Time | 操作时间 |

<a id="response-operationlogdetail"></a>

### 12.57 OperationLogDetail

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `logId` | ID | 操作日志ID |
| `userId` | ID&#124;null | 操作人；物理清理后为空 |
| `operationType` | string | ≤32，ASCII操作类型 |
| `operationTarget` | string | ≤100，ASCII模块或资源 |
| `targetId` | ID&#124;null | 目标数字ID，其他情况为空 |
| `permissionCode` | string&#124;null | 本次检查的权限码快照 |
| `result` | integer | 0失败 1成功 |
| `errorMessage` | string&#124;null | ≤500，脱敏错误摘要 |
| `ipAddress` | string&#124;null | 操作IP |
| `operationTime` | Time | 操作时间 |
| `operationData` | object&#124;null | 按业务方法参数名组织的操作数据，业务字段保留完整原值，不掩码或截断；历史未记录时为null。密码和登录票据不记录，文件上传仅记录文件名、类型和字节数，不含文件二进制 |

<a id="response-authorpublic"></a>

### 12.58 AuthorPublic

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | ID | 作者 ID，可用于前台 authorId 筛选 |
| `nickname` | S(64) | 当前公开昵称 |
| `avatar` | URL? | 当前公开头像，无则 null |
| `intro` | S(500)? | 当前公开个人简介，无则 null |
| `articleCount` | string | 当前 PUBLIC 文章数的十进制字符串，不含草稿/下架/回收/私密文章 |
| `lastPublishTime` | Time | 当前 PUBLIC 文章中最大的首次发布时间 |

<a id="response-likedarticle"></a>

### 12.59 LikedArticle

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `likedAt` | Time | 本人当前点赞明细的 createTime |
| `article` | ArticlePublic | 完整字段及可空性沿用 [7.1](#api-37)，不含正文或后台字段 |

---

<a id="business"></a>

## 13. 业务补充说明

<a id="oauth-flow"></a>

### 13.1 OAuth 登录与绑定

1. 查询可用平台，调用 authorize（`credentials: "include"`）；LOGIN 可整页跳转，BIND 携带当前 Token 并打开弹窗保留原页面。
2. 平台回调由后端校验 state、浏览器 Cookie 和流程；state 有效期 300 秒，一次性使用。BIND 还检查原会话与最近认证。
3. 后端 303 跳到配置的固定完成页，fragment 仅放 ticket，不放本站或平台 Token；ticket 有效期 60 秒、限同浏览器一次兑换。
4. 完成页优先处理票据，不先恢复旧会话。LOGIN 读取并移除 ticket 后调用 exchange，得到对应端有效期的本站 Session 并持久化；BIND 弹窗以精确 origin 的 postMessage 把 ticket 交给原页面，原页面携带原 Token 兑换绑定结果，弹窗不恢复身份或清除原页面的持久凭据。

来源值使用后端启用的平台码，例如 GITHUB；只列配置完整的平台。不得自定回调地址、以昵称/邮箱合并账号或转移他人绑定。保留至少一种可用登录方式；BIND 原页面/会话丢失后重新登录确认绑定结果。完整流程见 [OAuth 开发约定](API_CONTRACT.md#oauth-flow)。

### 13.2 文章可见性与删除

公开文章（PUBLIC）须同时满足：已发布 `status=1`、公开 `visibility=1`、未回收、首次发布时间非空且不晚于当前 UTC。所有前台列表、详情、搜索、互动、Feed、地图及附件文章上下文共用此条件，管理员 Token 不扩大前台可见范围。

隐藏分类不隐藏文章，文章的 category 返回 null；按隐藏/不存在分类或不存在标签筛选返回空页。公开列表所有条件 AND 组合；keyword 为标题、摘要与 Markdown 原文字面包含。前后台均支持 authorId 筛选，不能扩大 PUBLIC 条件或当前后台权限范围。

普通删除只移入回收站，保留正文、标签、互动和 slug；恢复为下架，不自动公开。永久删除见 [22.7](#api-159)，须先回收并同时满足 delete 与 purge 权限范围。详情包含上下篇及最多 6 条同分类公开推荐，无须额外查询接口。

### 13.3 互动、订阅与统计

评论为纯文本，有隐藏祖先的评论分支不公开。留言的悄悄话沿完整父链限制可见性，管理员通过后台读取，编辑重新待审。评论与留言删除均在事务中物理移除目标及全部子回复，不生成占位；已有旧占位兼容读取，可通过本人或后台删除入口清除。相关规则见 [评论](API_CONTRACT.md#comment-rules) 与 [留言](API_CONTRACT.md#message-rules)。

订阅邮箱独立确认，不把账号联系邮箱当作已验证邮箱。首次公开发布及留言实际审核通过后再通知；发送失败不回滚已提交业务，进程内通知不保证跨宕机投递。RSS/站点地图公开读取，不依赖邮件订阅。

累计文章阅读数与页面浏览采集分别计算；浏览记录清理影响保留明细的 PV/UV，不能当作文章累计阅读回退。统计日期按 UTC，时间区间与补零规则见 [统计约定](API_CONTRACT.md#analytics-rules)。

<a id="file-consistency"></a>

### 13.4 文件引用与访问

先创建草稿取得 articleId，再上传并在正文/封面引用文件；公开附件路径为 `/api/v1/blog/articles/{articleId}/files/{fileId}/content`，必须对应当前文章并实际引用。后台详情的安全 HTML 使用后台附件路径，客户端携带 Bearer 获取后生成临时 object URL。

公开文章确实引用的文件可从该文章上下文读取；草稿、私密、下架和回收站上下文返回 404。后台读取同时满足文章和文件各自权限范围。头像、配置、主页/简历图标和音乐等展示字段仅用外部资源，不接受本站附件 API。

仍被任何文章（含回收站）引用的文件不可删除。删除文件不要求版本；存储失败保留元数据并返回 503。需要私密隔离的内容应另传文件，不能复用已经公开的 fileId。详细引用、存储补偿与事务规则见 [文件开发约定](API_CONTRACT.md#file-consistency)。

<a id="transactions"></a>

### 13.5 授权与写入

角色授权整体替换 permissionIds/menuIds，菜单补齐祖先但不自动授予接口权限；最后有效管理员和最后登录方式受保护。成功业务与数据库审计共同提交，失败回滚；Redis/文件存储与 MySQL 没有共同事务。锁等待后的原会话、权限、范围、版本及状态复核等实现规则见 [事务约定](API_CONTRACT.md#transactions)。

---

<a id="errors"></a>

## 14. 错误码

### 14.1 HTTP 与业务错误

<a id="user-unique-conflict"></a>

用户名（账号）和邮箱分别保持全库唯一，按数据库 `utf8mb4_0900_ai_ci` 排序规则判断，不区分大小写。注册、后台新建用户、Admin/Blog 本人资料修改及后台修改用户资料共用以下冲突响应；修改邮箱时排除目标用户本人，邮箱传 null 仍可清空。

| 重复情况 | HTTP / code | `msg` | `errors` 中的字段 |
| --- | --- | --- | --- |
| 仅用户名重复 | 409 / UNIQUE_CONFLICT | 用户名已被使用 | username |
| 仅邮箱重复 | 409 / UNIQUE_CONFLICT | 邮箱已被使用 | email |
| 用户名和邮箱同时重复 | 409 / UNIQUE_CONFLICT | 用户名和邮箱均已被使用 | username、email |

两个字段分别属于不同账号时，也一次返回两个冲突字段；不返回占用者 ID、原始输入、SQL 或数据库约束名。写入仍由数据库唯一约束保证，失败整笔回滚，不创建用户或角色关联，不保存部分资料。注册先验证并消费 REGISTER 邮箱验证码，冲突后重试需要重新获取验证码。前端直接展示 `msg`，避免再把相同字段说明追加一遍；需要定位输入项时可使用 `errors`。

```json
{
  "code": "UNIQUE_CONFLICT",
  "msg": "用户名和邮箱均已被使用",
  "data": null,
  "errors": [
    {"field": "username", "message": "用户名已被使用"},
    {"field": "email", "message": "邮箱已被使用"}
  ]
}
```

| HTTP | code | 适用场景 |
| --- | --- | --- |
| 200 | OK | 查询、更新、删除、幂等重放成功；除XML与文件成功响应外使用Result |
| 201 | OK | 创建新业务记录（含注册、文章、评论、上传）；返回模型；后台创建设置对应详情Location，注册/评论通过响应ID及本人接口定位，不设置Location |
| 400 | EMAIL_CODE_INVALID | 邮件验证码错误、过期、跨用途或已消费，重新发送 |
| 403 | OAUTH_NOT_BOUND | 第三方身份未绑定；先完成邮箱注册，再登录绑定 |
| 400 | CAPTCHA_INVALID | 验证码缺失、格式错误、过期、错误或已消费，需重新获取 |
| 400 | VALIDATION_ERROR | 字段、枚举、分页、查询组合或 JSON 非法 |
| 400 | OAUTH_STATE_INVALID | state/浏览器/操作不匹配、过期或已消费；不泄露哪一项失败 |
| 400 | OAUTH_DENIED | 用户在平台取消授权 |
| 400 | SUBSCRIPTION_TICKET_INVALID | 订阅确认或邮件退订票据无效、过期、复用或与当前用户/邮箱/轮次不符；统一错误，不泄露匹配项 |
| WebSocket | ONLINE_TICKET_INVALID | 在线票据格式错、过期、复用或来源/用途不匹配；1008 关闭，重新申请票据 |
| 401 | UNAUTHENTICATED | Token 缺失、失效、过期或被注销 |
| 401 | AUTH_FAILED | 密码登录名不存在、密码错误或无本站密码，统一文案“账号或密码错误” |
| 403 | ACCOUNT_DISABLED | 已确认凭据身份后发现本站账号禁用 |
| 403 | VISITOR_BLOCKED | 仅访问采集接口限制此统计标识；不代表本站账号被禁用，不阻止公开阅读 |
| 403 | ACCESS_BLOCKED | 反复404导致当前可信IP临时封禁，附Retry-After；不修改账号或访客统计状态 |
| 403 | FORBIDDEN | 权限不足；全局操作拒绝 |
| 403 | REAUTH_REQUIRED | 安全操作需最近 5 分钟内重新登录 |
| 404 | NOT_FOUND | 不存在、非公开文章、SELF 不可访问的他人资源；不泄露目标存在性 |
| 409 | UNIQUE_CONFLICT | username/email/slug/编码/路由唯一键冲突；已回滚；账号字段按[重复情况](#user-unique-conflict)返回明确提示及字段明细 |
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
{"code":"FORBIDDEN","msg":"无权执行该操作","data":null,"errors":[]}
```

<a id="delivery"></a>

接口实施状态以本文标记为准；部署配置和历史验收记录见 [后端运行说明](后端运行说明.md)。

---

<a id="legacy-sources"></a>

## 21. 旧项目来源与迁移对照

V2 沿用旧站的主页、简历、文章、留言、音乐、订阅与访问统计业务，并采用当前 Sa-Token、RBAC 和 28 表设计。旧站 JWT、游客 Token、私密评论与前端完成页均不自动成为 V2 能力。单条与批量操作已按 V2 规则合并为[统一操作入口](#admin-batch)，没有复制旧接口、鉴权或默认封禁时间。

具体来源和迁移取舍见 [迁移对照](API_CONTRACT.md#legacy-sources)；运行配置与历史验证见 [后端运行说明](后端运行说明.md)。

---
