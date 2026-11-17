# DriftingLeavesV2 接口文档与通用协议

各端文档提供请求参数、响应和示例。本文汇总通用协议、共享规则和错误码。

统一根路径为 `/api/v1`，紧接根路径的第一个业务前缀必须是所属端名。当前共 **211 个 HTTP 路由、3 个 WebSocket 入口**，包含各端复用同一业务能力的独立路径。

2026-11-01 将 26 个单条管理写入口合并为[26 个统一操作入口](API_ADMIN.md#admin-batch)，每次支持 1..100 个显式目标；单条操作也提交一项数组，不再提供重复的单条写路由。另增用户批量启用、禁用、删除 3 项及前端未知路由上报 2 项，2026-11-02 增加音乐文件读取及分类批量状态切换，2026-11-03 补充其余 8 类批量状态。2026-11-04 移除评论和留言本人编辑接口，保留本人评论详情。2026-11-07 增加公开展示图片的[上传](API_ADMIN.md#display-image-upload)和[读取](API_BLOG.md#display-image-content)接口。

| 端 | 完整前缀 | 用途 | HTTP 路由数 |
| --- | --- | --- | --- |
| [Admin 管理端](API_ADMIN.md#client-admin) | `/api/v1/admin` | 登录、本人设置与后台管理 | 137 |
| [Blog 博客端](API_BLOG.md#client-blog) | `/api/v1/blog` | 登录、本人互动、公开博客与统计 | 62 |
| [Home 主页端](API_HOME.md#client-home) | `/api/v1/home` | 主人资料、社交、配置与统计 | 5 |
| [Cv 简历端](API_CV.md#client-cv) | `/api/v1/cv` | 主人资料、社交、简历、配置与统计 | 7 |

[完整接口索引](API_INDEX.md) · [通用协议](#common) · [共享响应模型](#models) · [错误码](#errors) · [开发约定](API_CONTRACT.md)

### 路径迁移与共享能力

- 原 `/auth/**` 和 `/me/**` 分别迁到 `/admin/auth/**`、`/admin/me/**` 及 `/blog/auth/**`、`/blog/me/**`。本人评论、留言、点赞记录和邮件订阅属于 Blog。
- 原 `/analytics/**`、`/ws/online` 迁到 Home、Blog、Cv 各自前缀下；`siteCode` 必须与当前端一致，WebSocket 票据也必须在同端使用。Admin 不参与在线人数统计。
- Blog、Home、Cv 分别通过自己端的 `/profile`、`/social-links`、`/configs` 读取共享公开数据。更改资料和配置仍使用 Admin 入口。
- 旧的无端名前缀入口已移除。前后端须一起部署。OAuth 平台继续登记任一端 HTTPS Origin 上的 `/api/v1/blog/auth/oauth/{source}/callback` 或 `/api/v1/admin/auth/oauth/{source}/callback`，两个入口共用该平台应用。
- 四端路径不建立新的账号、角色或会话体系。Admin 的认证与本人设置维持公开/登录本人规则，后台业务继续要求 `admin:access`、业务权限和数据范围。

<a id="common"></a>

## 通用协议

### 路径与鉴权

- 各端文档使用完整路径，统一前缀 `/api/v1`。JSON 请求使用 `application/json`，上传使用 `multipart/form-data`。
- Admin 和 Blog 的登录、注册、OAuth 和本人设置共用后端实现，调用时使用各自的 `/admin/auth`、`/admin/me` 或 `/blog/auth`、`/blog/me` 路径。邮箱验证码发送按端区分，Admin 只提交 `email`，Blog 还提交 `purpose`（`REGISTER` 或 `LOGIN`）。
- 本站通过 Sa-Token 和 Redis 校验会话。Admin 与 Blog 通过同源 `/api/v1` 反代访问 API。生产使用不同 HTTPS 主机名，`dev` 可使用回环 HTTP。后台使用 `__Host-dl-admin-session`，固定 604800 秒（7 天）。博客使用 `__Host-dl-blog-session`，固定 259200 秒（3 天）。两个登录 Cookie 均为 host-only，设置 `HttpOnly; Secure; SameSite=Strict; Path=/`，省略 Domain，访问与刷新均不续期。
- 账密、邮箱验证码登录及 OAuth LOGIN 成功时，事务提交后通过 `Set-Cookie` 签发对应端会话。会话 JSON 只含 `expiresIn` 和 `user`，OAuth 登录结果另带 `operation`。后台各登录方式均要求 `admin:access`；账密和邮箱登录还要求图形验证码。两端只读取各自的登录 Cookie，旧 Bearer 和旧会话值均无法恢复登录。新版前端启动时删除本端旧的 `dl-admin-session` 或 `dl-blog-session` localStorage 值，切换后需重新登录一次。
- 刷新后以本端 `/me`、`/me/capabilities` 核验身份和权限，Admin 还读取 `/me/menus` 并确认 `admin:access`。预期的 401 按未登录处理。退出先撤销当前 Cookie 对应的会话，再用同名同属性、`Max-Age=0` 的 Cookie 清除浏览器值。退出幂等，撤销失败返回错误。普通 401 响应不清除 Cookie。
- 页面已知登录账号时，用 `X-Expected-User-Id` 发送该页的十进制用户 ID。受保护写请求、有效会话的退出以及 OAuth BIND 发起和兑换要求该头，受保护读取提供该头时也会核对。后端用 Cookie 确定真实身份并执行权限检查。预期缺失或与实际账号不符时，受约束的请求返回 `409/SESSION_ACCOUNT_CHANGED`，不执行操作，也不修改 Cookie。页面重新核验身份，原请求不重试。
- 博客讨论读取、访问采集和在线票据请求可匿名。省略该头时按匿名处理，即使浏览器已有另一标签页的登录 Cookie。提供该头时，必须带对应的有效会话。账密或邮箱登录、OAuth LOGIN 和独立的邮件退订票据不要求预期账号。
- Admin、Blog 的写请求（包括注册、登录、退出、绑定、兑换和上传）在业务执行前核对本端配置的 scheme、host 和 port。优先读取唯一 `Origin`，缺失时才从唯一可解析的 `Referer` 提取来源。`Origin: null`、来源缺失、跨端或不匹配均为 403。独立校验的 OAuth 顶层 GET 回调、邮件退订票据及匿名 404 上报按各自规则处理。公开文章详情的阅读计数与会话无关。
- Admin 和 Blog 按各自配置处理同源请求，反代改写后端看到的 Host 时也适用。HTTP 跨域只向 Home 和 Cv 的配置来源开放各自的公开接口，允许 GET、POST 和 Content-Type，可读取 Retry-After。两端统计为匿名，WebSocket 各自核对所属站点的 Origin。
- **公开**：无需登录；**登录本人**：账号启用，身份只取当前会话；**后台：P**：账号启用，同时具备 `admin:access` 与权限 P。未认证 401，无权限 403。
- 文章、文件、评论按本次权限的 ALL/SELF 范围检查；范围外资源返回 404。多个权限分别检查范围。分类、标签、用户、角色等全局资源不产生“本人版本”；标注 ALL 的操作必须具备 ALL 范围。
- 密码变更、清除密码、发起绑定与解绑要求最近 300 秒成功认证。密码变更/清除/解绑、账号禁用及授权变更会撤销相关会话；需要时重新登录。
- HTTP 接口响应使用 `Cache-Control: no-store`。OAuth 还需同浏览器的流程 Cookie，见 [13.1](#oauth-flow)。

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
- slug 区分大小写，仅允许 `[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*`；文章最长 200，分类/标签最长 100。分类名、标签名、用户名、邮箱按数据库排序规则判断唯一，文章标题允许重复。
- URL 最长 2048，外部资源只接受合法 HTTP(S)，社交 mailto 的例外见对应模型；本站附件仅接受指定的文章上下文路径，见 [13.4](#file-consistency)。
- JSON 请求体上限 8MiB；Markdown 解码后最多 1MiB；文件限制见上传接口。审计时间、发布时间及操作者身份由服务端生成。

### 0.4 编辑版本与重试

支持版本校验的详情接口返回 `X-Revision: "<opaque>"`，各接口明确标注；要求版本的写接口须在 `If-Revision` 原样携带该值（含引号），不接受 `*`。缺失返回 428，过期返回 412，先重新获取详情再合并。列表没有逐条版本，编辑前先取详情。

需要版本的写入成功返回新 X-Revision，删除无需返回；无实际变化可以保持原版本。先检查权限和范围，再校验版本与状态；状态不变也不能跳过版本。版本涵盖业务字段及关联集合，不以阅读数或审计时间变化判定编辑冲突。

统一的[批量接口](API_ADMIN.md#admin-batch)是多资源例外：逐条使用 Body 中的 `items[].revision`，不使用整个请求的 If-Revision；状态写入在响应 `data.items[].revision` 返回各资源的新版本，不返回单一 X-Revision。版本来源和校验语义仍与单条接口一致。后台编辑表单若同时修改资料与状态，在 `PUT` 资料保存成功并取得最新 `X-Revision`（或重新读取详情版本）后，再按状态变化调用对应的统一状态接口，不合并进资料 `PUT` 请求体。

没有通用幂等键。创建超时先查询确认；点赞/取消点赞、注销可幂等，OAuth state/ticket 一次性。429 读取 Retry-After；服务不可用返回 503，不当作成功重试。Home 和 Cv 的公开接口在合法跨域响应中暴露 Retry-After。

### 0.5 查找接口

- [完整接口索引](API_INDEX.md)：先按端，再按业务名称或完整路径查找。
- [文章场景速查](API_BLOG.md#article-scenarios)：删除、分类/标签/作者筛选、搜索、归档、回收和恢复。
- [后台统一操作（38 个入口）](API_ADMIN.md#admin-batch)：参数、响应、权限和整批失败规则集中在 1.22，单个目标也走同一协议。
- 字段与状态、锁顺序、事务及平台接入的完整开发约定见 [接口开发约定](API_CONTRACT.md)，配置与当前检查入口见 [后端运行说明](后端运行说明.md)。

### 邮箱验证与登录方式

- 公开注册（`/blog/auth/register` 及保留的 `/admin/auth/register`）必须提供 `email` 和 `emailCode`，仅消费 REGISTER 用途验证码；注册只授予 member，不创建后台身份。
- 前台可以选择用户名密码、邮箱验证码或已绑定第三方登录，其中账密和邮箱登录需填写图形验证码。第三方登录免图形验证码，只接受已绑定账号。
- 后台支持用户名密码、邮箱验证码和已绑定的第三方账号登录，均需 `admin:access`。账密和邮箱登录需图形验证码，第三方登录免图形验证码。邮箱登录和第三方登录不要求设置本站密码。所有受保护 `/admin/**` 都检查后台认证标记。切换到 Cookie 鉴权后，已有用户需重新登录。

| 接口 | JSON 请求体 | 成功响应 |
| --- | --- | --- |
| `POST /api/v1/blog/auth/email-code` | `{"email":"leaf@example.test","purpose":"REGISTER"}`；purpose 仅 REGISTER / LOGIN | `Result<null>` |
| `POST /api/v1/blog/auth/email-login` | `{"email":"leaf@example.test","emailCode":"012345"}` | `Result<Session>`，三天 |
| `POST /api/v1/admin/auth/email-code` | `{"email":"leaf@example.test"}` | `Result<null>`；核对当前邮箱、启用状态和 admin:access |
| `POST /api/v1/admin/auth/email-login` | `{"email":"leaf@example.test","emailCode":"012345"}` | `Result<Session>`，七天；消费后台验证码并检查 admin:access |

以上接口均公开并要求 `X-Captcha-Id`、`X-Captcha-Code`，只接收声明字段、不接受查询参数。发送成功与失败都会消耗图形验证码；成功后前端刷新图片，注册或登录提交时填写新图片。邮件验证码六位纯数字、五分钟有效，按注册/前台登录/后台登录分开，任何一次邮件校验都会原子消费对应验证码；验证码不出现在 HTTP 响应或日志中。发送接口仅在 SMTP 接受后返回成功，未启用或发送失败返回 503，失败清理验证码。

两个端的邮箱登录与发送登录验证码接口在限流和图形验证码校验通过后，邮箱未绑定本站账号时返回 `401/AUTH_FAILED`，提示“该邮箱未注册”。博客注册用途仍向新邮箱发送验证码。

所有发送用途和端共享每邮箱每 60 秒一次、每可信 IP 每 60 秒五次的限额，超限 429 并带 Retry-After。发送接口在图形验证码通过后计数；后台发送还检查账号登录失败次数。两端账密和邮箱登录，以及后台发送邮箱验证码，共用每 IP 每分钟 30 次的入口预算。邮箱登录的邮件验证码错误计入登录失败预算，图形验证码错误不计入。

账号邮箱沿用 `sys_user.email` 唯一约束，不新增表或自动改写历史用户。邮箱验证码登录每次证明当前邮箱所有权，不把已有联系邮箱或订阅邮箱当作注册验证结果。账密方式要求已设置本站密码；邮箱方式要求已有可收信的账号邮箱。至少有一种可用即可登录后台，不要求同时配置两者，禁止重建业务库补齐资料。

<a id="captcha"></a>

### 图形验证码

获取接口为 `GET /api/v1/admin/auth/captcha` 和 `GET /api/v1/blog/auth/captcha`，均公开、无查询参数、无请求体，返回 `Cache-Control: no-store`。两端共享每可信 IP 每 60 秒 30 次的生成窗口，超限返回 429 和 Retry-After。

首次加载、点击图片刷新和提交后刷新均调用上述接口、共用同一预算；刷新不会绕过限流，也不会重置窗口。超限在生成图片和写入验证码缓存之前拒绝。

使用 Hutool `CaptchaUtil.createCircleCaptcha` 生成 200×80 PNG、6 位纯数字和 20 个干扰圆圈，数字保留前导零。验证码不是本站会话或 OAuth state，不设置 Cookie，无需登录。

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
| `POST /api/v1/{admin,blog}/auth/login` | 账密登录 |
| `POST /api/v1/{admin,blog}/auth/email-code` | 发送邮箱验证码 |
| `POST /api/v1/{admin,blog}/auth/email-login` | 邮箱验证码登录 |

### 评论和留言的数字题目验证

`GET /api/v1/blog/auth/math-captcha` 公开、无查询参数和请求体，返回 `Cache-Control: no-store`。与图形验证码共用每可信 IP 每分钟 30 次的生成预算，题目为两个 1–9 整数的加、减或乘法，减法不产生负数。

```json
{"code":"OK","msg":"成功","data":{"captchaId":"<43位URL安全随机字符串>","question":"9 × 9 = ?","expiresIn":120},"errors":[]}
```

客户端显示 question 并填写计算结果，答案只保存在 Redis，不在响应中返回。以下接口改用数字题目，Body 保持原有字段，Header 仍为 `X-Captcha-Id` 和 `X-Captcha-Code`，后者填写 0–81 的十进制整数（不补前导零）。

| 接口 | 验证范围 |
| --- | --- |
| `POST /api/v1/blog/articles/{articleId}/comments` | 发表评论或回复 |
| `POST /api/v1/blog/messages` | 发表留言或回复 |

题目和图形验证码使用不同 Redis 键，不能跨用途使用；有效期均为 120 秒。后台留言回复仍不要求验证码，保留后台登录、权限与限流；后台评论管理当前只有查询、审核和删除，没有独立回复接口。

验证码在业务操作前验证；进入校验后的第一次尝试即原子删除，正确、错误或后续业务失败都不能复用。缺失、格式错误、过期、答案错误及重放统一返回 HTTP 400、`CAPTCHA_INVALID`，不累计密码错误次数。Redis 不可用返回 503，不能跳过校验。其他入口校验、鉴权或限流提前拒绝时可能尚未消费，客户端每次提交需验证码的请求后清空输入并重新获取图片或题目，不自动重放写入，正文保留供用户重试。

验证码及 OAuth 发起和兑换使用同源请求。OAuth 发起、回调和兑换均不要求图形验证码。日志不得记录验证码答案或上述验证码请求头。

<a id="request-protection"></a>

### 0.6 接口限流覆盖与阈值

以下路径均省略统一前缀 `/api/v1`。Admin 与 Blog 的同类认证操作共享既有计数预算；三个公开端读取相同业务数据时共用 Service 缓存。所有限流继续使用本站 Redis 原子固定窗口，不采用参考项目的进程内令牌桶。IP 仅取服务端识别的可信客户端地址；IP、用户、浏览器等是分别累计的维度，任一超限即拒绝，不能仅限制它们的组合。用户只能来自当前有效会话，fingerprint 和 OAuth 浏览器标识均不能代替本站身份。

| 业务及路径 | 操作窗口与默认阈值 | 实施状态 |
| --- | --- | --- |
| `GET /{admin,blog}/auth/captcha`、`GET /blog/auth/math-captcha` | 三个入口共用60秒30次/可信IP | 已实现，生成图片或题目前限流 |
| 两端账密登录、邮箱登录及后台发送邮箱验证码 | 共用默认 60 秒 10 次的失败窗口，按可信 IP 和 IP + 归一化用户名或邮箱分别累计 | 凭据错误计入，邮箱登录的邮件验证码错误也计入。阈值可部署配置 |
| 两端账密登录、邮箱登录及后台发送邮箱验证码 | 共用 60 秒 30 次/可信 IP 的入口窗口 | 在图形验证码与凭据检查前计数，独立于失败窗口 |
| `POST /{admin,blog}/auth/register` | 60秒5次；IP、存在的浏览器标识；无标识按IP | 已实现，可部署配置 |
| `POST /{admin,blog}/auth/oauth/{source}/authorize`（LOGIN） | 60秒3次，IP、浏览器标识分别计数；两端和各平台共享额度 | 仅发起登录计次；绑定、回调和兑换不占用此额度 |
| `POST /{admin,blog}/auth/oauth/{source}/authorize`（BIND）、`POST /{admin,blog}/auth/oauth/exchange` | 共用60秒20次；IP、浏览器标识；无标识按IP | 已实现，可部署配置 |
| `GET /{admin,blog}/auth/oauth/{source}/callback` | 所有平台共用60秒30次/IP，独立于发起/兑换窗口；跨端转发的两次回调均计数，超限不得消费state或调用平台 | 已实现 |
| `PUT/DELETE /{admin,blog}/me/password` | 两端及两种方法共用 60 秒 10 次，按可信 IP 和用户分别计数 | 已实现 |
| `POST /blog/articles/{articleId}/comments` | 60秒10次；IP、用户 | 已实现 |
| `PUT/DELETE /blog/articles/{articleId}/like` | 两种方法及所有文章共用60秒60次；IP、用户 | 已实现 |
| `POST /blog/messages` | 60秒10次；IP、用户 | 已实现 |
| `POST /admin/messages/{messageId}/replies` | 60秒10次；IP、用户，跨父留言ID累计 | 已实现，跨父留言累计 |
| `POST /admin/files`、`POST /admin/files/display-images` | 共用60秒10次；IP、用户 | 已实现 |
| `PUT /blog/me/rss-subscription` 申请/重发 | 共用60秒5次；IP、用户 | 已实现 |
| `POST /blog/me/rss-subscription/confirm` | 60秒10次；IP、用户，限流在消费票据之前 | 已实现 |
| `POST /blog/rss-subscriptions/unsubscribe` | 60秒30次/IP，跨票据累计；保持有效票据退订幂等 | 已实现 |
| `POST /{blog,home,cv}/analytics/online/tickets` | 60秒120次/可信IP、30次/已验证浏览器统计键 | 已实现，两个维度各累计一次 |
| WebSocket `GET /{blog,home,cv}/ws/online` Upgrade | 60秒120次/可信IP；每连接业务消息60秒30条 | 已实现；超限握手429与Retry-After，消息超限1008关闭 |
| `POST /{blog,home,cv}/analytics/page-views` | 60秒60次/IP、30次/fingerprint；博客提供预期账号且会话有效时另限60次/用户；home/blog/cv共用窗口 | 已实现，在行锁、地域解析和写入前按独立 IP、fingerprint、可选用户原子累计 |
| 公开内容GET公共窗口 | 60秒120次/IP；覆盖 `/blog/articles`、文章详情、归档、热门榜、分类/标签列表与详情、作者列表与详情、评论列表、留言列表、友链、配置、音乐、统计、RSS、站点地图，以及三端各自的 `/profile`、`/social-links`、`/configs` 和简历的 `/cv/experiences`、`/cv/skills` | 已实现，所有这些入口共享，冷/热缓存同样计数 |
| `GET /blog/articles` 且带 `keyword` | 另限60秒10次/IP，跨关键词、页码及作者/分类/标签筛选累计 | 已实现，跨关键词和筛选累计 |
| `GET /blog/articles/{slug}` | 另限60秒60次/IP，跨slug累计 | 已实现，跨 slug 累计 |
| `GET /blog/rss.xml`、`GET /blog/sitemap.xml` | 两种XML及所有子图页共用60秒30次/IP | 已实现，RSS/全部地图页共享 |
| `GET /blog/articles/{articleId}/files/{fileId}/content` | 60秒120次/IP，跨文章及文件累计，独立于公开内容GET公共窗口 | 已实现，独立预算，保持 PUBLIC 和引用校验 |
| `GET /blog/display-images/{fileId}` | 60秒120次/IP，展示图片共用，包含 HEAD | 已实现 |
| `GET /admin/statistics/{overview,page-views,visitors,provinces,articles}` | 五类报表共用60秒30次；IP、用户，跨日期与站点条件累计；花括号内为五个既有路径的简写 | 已实现，实时 ALL 范围与日期检查后累计 |

新增阈值为实现默认值，后续按真实页面请求数、代理/NAT及压测结果调整并同步文档；已在当前实现中启用；部署仍需正确配置可信代理。未列出的普通本人/后台CRUD保持当前鉴权、版本、事务及请求大小约束，不机械增加统一写限流。

同一请求在同一业务窗口只计一次；公共窗口与专用窗口属于独立预算，各计一次，不叠加两套相同业务计数器。路径参数、关键词、查询日期和客户端自报身份不得拆分IP窗口。鉴权、严格输入检查先于业务限流；登录总请求限流先于密码计算；所有限流均先于业务副作用。超限统一 `429/RATE_LIMITED`，携带正整数秒 `Retry-After`，客户端等待后再试；被拒请求不消费state/ticket、不发送邮件、不增加阅读量或写入浏览明细。限流Redis不可用返回503，不跳过保护，也不降级匿名。OPTIONS不计数。

<a id="not-found-protection"></a>

#### 0.6.1 错误页展示与反复 404 临时封禁

不存在的页面显示 `404 / 页面不存在`。网络、服务和限流错误使用全屏请求错误页，显示实际 HTTP 状态（如有）及用户可读的错误提示。Admin 和 Blog 的认证接口遇到 429 时在当前界面提示 `Retry-After` 等待时间。

参考 `FeiTwnd-Website` 的限频后封禁一天机制，使用本站 Redis 原子固定窗口：同一可信客户端 IP 在 60 秒内最多允许 20 次不存在访问，第 21 次触发 86400 秒临时封禁。四端共享 IP 预算，不按路径、Token、UA 或 fingerprint 拆分；只存 IP 摘要，不修改本站账号或 `blog_visitor`。封禁期间所有新的 API 请求及 WebSocket 握手返回 `403/ACCESS_BLOCKED`，附正整数秒 `Retry-After`；不会续期，到期自动恢复。已建立的 WebSocket 不主动断开。共享出口 IP 的访问者共用预算，部署须配置准确的可信代理。

服务端实际返回 404 的统一 JSON 响应（含接口路由缺失和业务资源不存在）计一次；达到阈值的响应改为 `403/ACCESS_BLOCKED`。OPTIONS、500/503、网络异常及普通429不计入不存在次数，不能因为错误页采用404样式就封禁服务故障的访问者。Redis 保护不可用时返回 `503/SERVICE_UNAVAILABLE`，不继续执行业务。

SPA 未知路由未必请求后端，Blog 与 Admin 在每次成功进入兜底路由时上报一次；Admin 未登录仍按原规则跳转登录，不将此导航当作404；接口404引起的错误页不再次上报。不上报完整URL、查询参数或错误信息，不自动重试；此上报仅能累计请求者自己的 IP，不能当作账号或统计指纹的封禁依据。关闭 JavaScript 的未知前端路径需由静态站点网关另行限制。

| 项目 | 约定 |
| --- | --- |
| 接口 | `POST /api/v1/blog/security/not-found`；`POST /api/v1/admin/security/not-found` |
| 鉴权 | 公开。必须携带所属端 `app.sites` 配置的 `Origin` |
| 参数 | 无 Path/Query/Body 参数；不接受客户端 IP、用户ID、fingerprint 或目标URL |
| 成功 | HTTP 200；`{"code":"OK","msg":"成功","data":null,"errors":[]}` |
| 错误 | 参数/请求体400，非法来源403/FORBIDDEN，封禁403/ACCESS_BLOCKED，保护不可用503 |
| 副作用 | 只累计当前可信 IP 的不存在窗口；达到阈值写入带一天TTL的封禁键，不创建浏览或访客记录 |

管理员也可从[访客管理](API_ADMIN.md#batch-visitor-block)手动封禁可信客户端 IP。手动封禁在公开站点的 API 读取、写入和 WebSocket 握手前检查，拒绝时返回 `403/ACCESS_BLOCKED`。Blog、Home 和 Cv 收到该错误时显示封禁提示，统计请求同样处理。后台 API 可用于解封。Nginx 直接提供的静态文件和已经下发的内容仍可访问。

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
| `/blog/articles` 不带keyword，包括作者/分类/标签/月组合筛选 | 10分钟 | 缓存文章集合、排序与公开静态资料；按页批量复核公开状态并从数据库补充阅读/点赞/评论计数，发现文章不再公开时重新分页；仅page≤10且pageSize≤20写缓存，其他合法查询正常回源 |
| `/blog/articles` 带keyword | 60秒 | 缓存同一完整查询的搜索集合，公开状态与计数按同一方式批量补充；仅page≤3且pageSize≤20写缓存；防止大量一次性关键词长驻Redis；仍执行专用搜索限流 |
| `/blog/articles/{slug}` | 15分钟 | 只缓存公开正文、安全HTML、派生阅读信息与公开关联；缓存命中仍校验PUBLIC，并按7.2锁内成功加一次阅读；不缓存整条含副作用的Service调用 |
| `/blog/archives` | 30分钟 | 按页码/页大小缓存月份与PUBLIC数量；发布、下架、回收、恢复、永久删除或可见性变化失效 |
| `/blog/article-rankings` | 60秒 | period、metric、limit及UTC月份分别入键；排行次序与分数使用同一缓存快照，阅读/点赞变化最多延迟60秒；文章下架、删除、标题/slug等资料变化主动失效 |
| `/blog/articles/{articleId}/comments` | 30秒 | 仅匿名查询使用公开缓存；有效会话查询保留本人待审/驳回内容并直接回源；新增、审核、本人/后台分支删除、祖先状态及文章可见性变化失效 |
| `/blog/messages` | 30秒 | 只缓存匿名根留言首页（page=1、pageSize=10、未传parentId）；留言新增、回复、审核、删除以及作者资料或身份徽标相关更新提交后失效 |
| `/blog/statistics` | 5分钟 | 公开汇总；访问PV/UV允许最多5分钟延迟，UTC日期入键；文章/分类/标签变化及后台统计清理主动失效 |
| `/blog/rss.xml`、`/blog/sitemap.xml` | 30分钟 | 缓存XML；RSS和地图分区，地图索引与各page分别入键；依赖内容、字典、公开配置、站点基址变化主动失效；子图仍遵守50000条/50MiB及不存在页404 |

所有分页、筛选、parentId、类型和大小写敏感slug必须完整入键；不能只按路径缓存。当前文章viewCount、likeCount、commentCount保持数据库口径，列表/详情缓存不引入异步计数或Redis点赞副本；排行与公开统计的延迟仅限上表明确的字段。新产生的访问明细及每次阅读/点赞不清空所有文章正文缓存。

**不缓存的业务响应**：`/blog/auth`、`/blog/me`、`/admin` 全部响应；登录用户的留言查询、匿名留言的其他分页及回复页；采集POST及全部业务写响应；文件字节、预览和临时下载地址。会话、OAuth及订阅票据继续使用独立Redis安全存储，不能当作普通查询缓存丢失后回源重建。权限、菜单、账号状态、最近认证、SQL归属及comment/message开关仍实时校验。

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
| `PUT /admin/articles/batch-status` | 同上；下架不能等15分钟详情TTL或30分钟XML TTL；首次发布清理之前的空集合，重复发布不改变原有邮件发送规则 |
| `POST /admin/articles/batch-recycle`；`POST /admin/articles/batch-restore` | 同文章元数据写入，并清理该文章的全部公开评论页；恢复保持下架，清缓存不代表恢复成PUBLIC |
| `POST /admin/articles/batch-permanent-delete` | 同回收规则；成功后文章详情、评论、列表、导航、推荐和XML不得残留可访问条目；文件仍按原业务保留，不因清缓存删除存储对象 |
| `POST /admin/categories`；`PUT /admin/categories/{categoryId}`；`PUT /admin/categories/batch-status`；`POST /admin/categories/batch-delete` | 分类列表/详情域；文章列表/搜索/详情域、归档、热门榜、作者列表/详情、公开统计、RSS及地图；覆盖改名、隐藏、排序、旧/新slug及删除后category_id置null |
| `POST /admin/tags`；`PUT /admin/tags/{tagId}`；`POST /admin/tags/batch-delete` | 标签列表/详情域；文章列表/搜索/详情域、归档、热门榜、作者列表/详情、公开统计、RSS及地图；删除还覆盖文章标签关联级联变化，不能只删标签详情 |
| `PUT /admin/users/batch-enable`；`PUT /admin/users/batch-disable` | 作者、文章、搜索、文章详情和评论缓存整批成功后统一清理；只修改账号状态 |
| `POST /admin/users/batch-delete` | 清理统计缓存；点赞按明细实时聚合，历史浏览及日志保留 |
| `PATCH /blog/me/profile`；`PUT /admin/users/{userId}` 中公开字段变化 | 作者列表/详情、含作者资料的文章列表/搜索/详情、公开评论域；昵称/头像/简介变化不能继续使用缓存中的旧资料；邮箱等未公开字段不进入公共缓存 |
| `POST /blog/articles/{articleId}/comments` | 该articleId下所有parentId、分页的公开评论缓存；待审新增也统一清理；文章列表/详情的评论数仍实时查库，不清空所有正文 |
| `DELETE /blog/me/comments/{commentId}`；`PUT /admin/comments/batch-review`；`POST /admin/comments/batch-delete` | 根据数据库中的articleId清理该文章全部公开评论页和回复页；祖先审核或删除影响后代，不能只删commentId对应的一条结果 |
| `PUT/DELETE /blog/articles/{articleId}/like`；成功文章详情GET | 不删正文缓存，不建立Redis计数副本；本次互动数按数据库读取，排行榜最多延迟60秒；这是0.7明确允许的计数例外 |
| `POST /admin/links`；`PUT /admin/links/batch-status`；`PUT /admin/links/{linkId}`；`POST /admin/links/batch-delete`；`DELETE /blog/me/link-application/{linkId}` | 整个公开友链域，含所有分页与排序；审核拒绝/隐藏/删除后下一次公开查询不能继续显示旧友链 |
| `PUT /admin/configs/site` | 公开site配置、RSS及地图；从公开改为不公开也必须删旧缓存，RSS继续按原规则检查必需公开配置 |
| `PUT /admin/configs/comment`、`PUT /admin/configs/message` | 不建立或清理不存在的策略缓存；继续原配置行锁与锁内实时读取，新开关/审核策略立即用于后续写请求 |
| `PUT /admin/home/profile` | 整个主页公开资料域；不与sys_user作者资料混为同一业务 |
| `POST /admin/home/social-links`；`PUT /admin/home/social-links/batch-status`；`PUT/DELETE /admin/home/social-links/{socialId}` | 整个公开社交链接域，含显示、隐藏、排序及空列表 |
| `POST /admin/cv/experiences`；`PUT /admin/cv/experiences/batch-status`；`PUT/DELETE /admin/cv/experiences/{experienceId}` | 整个公开经历域，含全部类型和分页；改变type时旧类型与新类型都必须失效 |
| `POST /admin/cv/skills`；`PUT /admin/cv/skills/batch-status`；`PUT/DELETE /admin/cv/skills/{skillId}` | 整个公开技能域，含实际筛选及分页；更新/隐藏/排序/删除均清理 |
| `POST /admin/music`；`PUT /admin/music/batch-status`；`PUT/DELETE /admin/music/{musicId}` | 整个公开播放列表域，含所有分页；显示、排序和地址变化一并生效 |
| `POST /blog/analytics/page-views` | 不缓存写入响应，不逐次清空公开统计；PV/UV按0.7允许的5分钟TTL更新；不改变文章累计阅读量 |
| `POST /admin/visitors/batch-delete`；`POST /admin/page-views/batch-delete` | 公开统计域主动失效；IP 封禁实时读库，不创建访客身份缓存 |
| 留言创建、回复、审核及本人/后台删除 | 提交后让匿名留言首页失效；作者公开资料、用户状态、用户角色及角色状态更新也清理该缓存 |
| 文件上传/删除、邮件订阅各类写接口、日志清理及其余身份/授权写接口 | 对应业务响应没有公共查询缓存。文件删除先按原规则拒绝仍被引用的文件；订阅轮次及会话撤销照旧执行 |

无实际业务变化的幂等写入可以不重复清缓存，但仍须完成鉴权、版本及状态检查；前次提交后缓存清理失败且保护尚未解除时，不能因“数据库已是目标值”跳过失效恢复。所有写入口（HTTP或现有内部调用）都在真正落库的Service负责关联清理，不能只在Controller清理。参考项目的异步摘要回填也会主动清理文章缓存；本站今后若增加内部写入，须沿用同一规则，本次不新增摘要任务。

参考方法、实际清理缓存名及本站补齐项见[参考项目写入清理对照](API_CONTRACT.md#reference-cache-eviction)；并发、回滚及提交后Redis故障仍按[事务失效规则](API_CONTRACT.md#cache-contract)处理。当前已在真正落库的 Service 接入；实际缓存域与恢复步骤见运行说明。

## 共享业务规则

以下规则适用于各端的对应业务。具体路径、权限和示例见各端文档。

<a id="group-7"></a>

### 7. 文章与归档

前台仅展示公开文章；后台提供草稿、编辑、发布/下架、回收与恢复。常用筛选和删除流程见 [7.12](API_BLOG.md#article-scenarios)。

<a id="group-8"></a>

### 8. 分类与标签

分类与标签是全局字典。删除分类会清空文章分类，删除标签会清理关联，文章与正文保留。分类隐藏时不在前台字典与分类筛选中暴露，文章仍可公开。

<a id="group-9"></a>

### 9. 评论审核与点赞

评论为纯文本，审核后展示；删除同时物理移除目标和全部子回复，不生成占位。评论创建按用户+IP共用 60 秒 10 次窗口，点赞/取消按用户+IP共用 60 秒 60 次窗口。点赞计数取当前明细，公开评论数只含可见且通过的评论。

[本人评论列表](API_BLOG.md#271-分页查询本人评论)返回公开文章的标题、slug 及评论跳转位置。页码按登录本人看到的文章评论根列表计算，每页 10 条，按创建时间和 ID 倒序。

<a id="group-10"></a>

### 10. 友链与站点配置

友链仅 status=1 时公开，按 sortOrder/linkId 排序。配置仅支持 site/comment/message：site 按公开标记展示，comment/message 始终私密；不提供任意新增/删除配置键。展示图片使用公开 HTTP(S) 地址，附件路径受各自的读取权限约束。

<a id="group-11"></a>

### 11. 文件与附件

支持上传、详情查询、预览与删除，元数据不可编辑。后台可按原始文件名或存储文件名筛选文件。普通附件按文章、音乐和文件的访问规则读取，展示图片通过专用入口公开，见 [13.4](#file-consistency)。

<a id="group-15"></a>

### 15. 主页展示与在线简历

主页、博客侧栏与简历共用站点主人资料；公开接口仅返回展示字段。后台操作要求对应权限的 ALL 范围，不因站点主人身份自动赋权。

<a id="group-16"></a>

### 16. 留言板、本人留言与审核

留言与文章评论独立；前台按 parentId 将整组可见回复合并分页，每次只读取一页正文，管理端默认和筛选时均省略 `parentId`，将根留言和各层回复平铺分页。对话接口按整组回复分页，另返回根留言。本人接口只取会话身份；私密父链、审核与分支删除规则见 [13.3](#business)。

[本人留言列表](API_BLOG.md#281-分页查询本人留言)返回留言跳转位置。页码按登录本人看到的留言板根列表计算，每页 10 条，按创建时间和 ID 正序。

<a id="group-17"></a>

### 17. 背景音乐与播放列表

音乐表单通过既有 POST /admin/files 上传音频、封面或歌词，再保存曲目；继续支持外部 HTTP(S) 地址。受管文件地址为 /api/v1/blog/music/files/{fileId}/content，只有当前展示曲目实际引用时可匿名读取，隐藏音乐使用后台授权原文件预览。读取支持 Range/206/416，不代理外部媒体。完整权限、格式、歌词结构及删除引用规则见[音乐模型](API_CONTRACT.md#model-12-11)。

<a id="group-18"></a>

### 18. 邮件订阅、RSS Feed 与站点地图

邮件订阅仅能使用当前登录账号邮箱，页面只读且服务端强制核对，仍须收信确认；RSS 与站点地图公开读取，不要求订阅邮箱。订阅操作不修改账号登录邮箱。

<a id="group-19"></a>

### 19. 访客、浏览记录与统计报表

统计标识与本站账号分开。旧统计标识封禁只限制采集。手动 IP 封禁影响公开站点 API。页面 PV/UV 与文章累计阅读数口径不同，日期按 UTC。

<a id="group-20"></a>

### 20. 操作日志

查询与清理日志均需对应权限的 ALL 范围。清理操作自身会写入审计，不以 admin 角色名绕过授权。

<a id="group-22"></a>

### 22. 文章发现与内容管理补充

博客端提供作者主页、分类与标签详情及本人点赞列表，管理端回收站支持永久删除。文章筛选和删除流程见[文章场景速查](API_BLOG.md#article-scenarios)。作者、分类、标签详情和本人点赞列表均不增加文章阅读量。

<a id="group-23"></a>

### 23. 实时在线人数

Home、Blog、Cv 建立在线连接参与全站合计，只有 Blog 的“站点统计”卡片展示三项人数。Admin 不参与。首期仅单后端实例，在线成员不写 MySQL，不改变 PV/UV 或文章阅读数。完整去重、消息和生命周期约定见[在线说明](实时在线人数接口说明.md)。

<a id="models"></a>

## 12. 共享响应模型

以下表格列出完整业务字段，字段名相对于单个业务对象；分页位于 `data.records[]`，数组位于 `data[]`，单对象位于 `data`。分页公共字段见 [0.2](#pagination)，可写字段仍以各接口请求表为准。输入模型、详细状态与开发约束见 [接口开发约定](API_CONTRACT.md#models)。

<a id="response-me"></a>

### 12.1 Me

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `username` | string | 登录名 |
| `nickname` | string | 展示昵称 |
| `email` | string&#124;null | 未验证联系邮箱，可 null |
| `avatar` | string&#124;null | 公开头像 URL，可 null |
| `intro` | string&#124;null | 个人简介，可 null |
| `status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `hasPassword` | boolean | 是否设置有效本站密码，不返回哈希 |
| `lastLoginTime` | Time | 最近登录 UTC 时间，未登录可 null |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |

<a id="response-session"></a>

### 12.2 Session

登录响应的 JSON 数据，浏览器会话只由 `Set-Cookie` 下发。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `expiresIn` | integer | 当前端的会话有效秒数：Blog 为 259200（3 天），Admin 为 604800（7 天） |
| `user` | object | 本人资料 Me |
| `user.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `user.username` | string | 登录名 |
| `user.nickname` | string | 展示昵称 |
| `user.email` | string&#124;null | 未验证联系邮箱，可 null |
| `user.avatar` | string&#124;null | 公开头像 URL，可 null |
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
| `session.expiresIn` | integer | LOGIN 分支的会话有效秒数。Admin 为 604800（7 天），Blog 为 259200（3 天） |
| `session.user` | object | 本人资料 Me |
| `session.user.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `session.user.username` | string | 登录名 |
| `session.user.nickname` | string | 展示昵称 |
| `session.user.email` | string&#124;null | 未验证联系邮箱，可 null |
| `session.user.avatar` | string&#124;null | 公开头像 URL，可 null |
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
| `configValue.reviewRequired` | boolean | 仅comment/message对象；true默认待审，false直接通过；有效admin发表两类内容均免审，有效author仅评论本人文章免审，详见[审核规则](API_BLOG.md#discussion-review)；不向公开接口输出 |
| `description` | string&#124;null | 配置说明。`configValue.description` 是可为空串的站点简介 |
| `isPublic` | integer | 0 私有、1 可公开，公开接口还校验键白名单 |
| `updateTime` | Time | 修改 UTC 时间 |

<a id="response-homeprofilepublic"></a>

### 12.33 HomeProfilePublic

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `nickname` | S(64) | 公开昵称 |
| `tagline` | S0(100) | 个人标签或一句话介绍 |
| `description` | S(500)? | 公开简介，null清空 |
| `avatar` | URL? | 公开头像，HTTP(S) 图片地址 |
| `website` | URL? | 个人网站 |
| `publicEmail` | string&#124;null | ≤254，合法联系邮箱；明确自愿公开，不自动读取登录邮箱 |
| `githubUrl` | URL? | GitHub主页 |
| `location` | S(100)? | 自愿公开的所在地 |

<a id="response-socialpublic"></a>

### 12.35 SocialPublic

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `socialId` | ID | 社交链接ID |
| `name` | S(64) | 社交平台或链接名称 |
| `icon` | string&#124;null | ≤2048；公开 HTTP(S) 图片地址或前端注册图标键，禁止任意HTML |
| `url` | URL | 外部HTTP(S)目标链接，邮箱链接可使用经过校验的mailto:邮箱 |
| `sortOrder` | int32 | 越小越靠前 |

<a id="response-messageprivate"></a>

### 12.42 MessagePrivate

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `messageId` | ID | 留言ID |
| `parentId` | ID&#124;null | 直接回复目标；根留言为null |
| `rootParentId` | ID&#124;null | 本人列表中回复所属的根留言 ID，按十进制字符串返回；根留言或其他接口响应为 null |
| `page` | integer&#124;null | 本人列表中目标根留言在当前读者留言板的页码，从 1 起，每页 10 条；目标未通过审核、父链对当前读者不可见或其他接口响应时为 null |
| `author` | object&#124;null | 当前用户公开资料 |
| `author.userId` | ID | 本站用户ID |
| `author.nickname` | string | 当前昵称 |
| `author.avatar` | URL&#124;null | 当前公开头像 |
| `content` | string | 原文 |
| `location` | string&#124;null | 发布时从可信 IP 解析的省市或国家；未知为 null，不公开完整 IP |
| `userAgentOs` | string&#124;null | 发布时识别的操作系统类型；未知、未知为 null |
| `userAgentBrowser` | string&#124;null | 发布时识别的浏览器类型；未知、未知为 null，不公开原始 User-Agent |
| `htmlContent` | string | 安全渲染HTML；纯文本转义并保留换行 |
| `contentFormat` | integer | 0纯文本 1Markdown |
| `isSecret` | integer | 0公开 1悄悄话 |
| `status` | integer | 0待审核 1通过 2驳回 |
| `editedAt` | Time&#124;null | 最近作者编辑时间，未编辑为null |
| `isOwn` | boolean | 按当前会话计算，匿名恒为false |
| `createTime` | Time | 创建时间，UTC |
| `updateTime` | Time | 最后更新时间，UTC |
| `notifyReply` | integer | 0不通知 1请求回复通知；不是邮箱验证凭据 |
| `hasChildren` | boolean&#124;null | 后台列表是否存在直接回复，包含私密及未通过审核的回复；本人接口、详情及写入响应为null，不据此透露私密回复关系 |
| `replyTarget` | object&#124;null | 后台列表、后台详情及后台对话返回直接目标昵称及最多120字符原文摘要；根记录及本人/写入响应为null |
| `siteAuthor` | boolean | 后台读取时展示启用用户的有效 author 或 admin 身份；本人/写入响应为false |
| `siteAdmin` | boolean | 后台读取时展示启用用户的有效 admin 身份；本人/写入响应为false |

<a id="response-trackingresult"></a>

### 12.47 TrackingResult

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `recorded` | boolean | 数据库成功记录本次浏览后为true，不返回访客会话 |

<a id="response-captcha"></a>

### 12.60 Captcha

Admin/Blog 的 `/auth/captcha` 共用此响应；不返回答案或会话。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `captchaId` | string | 43位URL安全随机标识，非十进制业务ID |
| `imageBase64` | string | 200×80 PNG Data URI，显示六位纯数字图形验证码 |
| `expiresIn` | integer | 固定120秒，读取不续期 |

<a id="response-discussionreplytarget"></a>

### 12.62 DiscussionReplyTarget

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `nickname` | string&#124;null | 直接回复对象的当前昵称；缺失时为null |
| `content` | string | 直接回复对象的原文摘要，最多120字符，按文本展示 |

根记录的 `replyTarget` 为null。公开接口只返回当前读者可见目标的摘要，不能通过此模型暴露私密或不可见父链。

---

<a id="business"></a>

## 13. 业务补充说明

<a id="oauth-flow"></a>

### 13.1 OAuth 登录与绑定

每个平台只注册一个 HTTPS 回调。两端共用平台应用和用户绑定，各自在本端 HTTPS Origin 完成授权和兑换。

1. 查询本端可用平台，同源调用 authorize。LOGIN 可整页跳转，BIND 使用原会话并打开弹窗保留发起页面。流程 Cookie 为 `__Host-dl-oauth-browser`，Secure、HttpOnly、SameSite=Lax、Path=/、无 Domain，最长 600 秒。
2. 登记回调与发起端不同时，后端按 state 记录把回调转到发起端的固定 API 路径。发起端校验 state、浏览器 Cookie 和流程后才换码。state 有效期 300 秒，一次性使用。BIND 还检查原会话与最近认证。
3. 后端 303 跳到发起端 `/oauth/complete`，成功 fragment 只放 ticket 和 operation。ticket 有效期 60 秒，只能在同一端、同一浏览器兑换一次。
4. 完成页读出并清除 fragment。LOGIN 同源兑换时签发本端登录 Cookie，Admin 另查 `admin:access`。BIND 弹窗只发送短期票据，原页面校验消息 Origin 和来源窗口，再用原会话兑换绑定结果。

来源值使用后端启用的平台码，例如 GITHUB；只列配置完整的平台。不得自定回调地址、以昵称/邮箱合并账号或转移他人绑定。保留至少一种可用登录方式；BIND 原页面/会话丢失后重新登录确认绑定结果。完整流程见 [OAuth 开发约定](API_CONTRACT.md#oauth-flow)。

### 13.2 文章可见性与删除

公开文章（PUBLIC）须同时满足：已发布 `status=1`、公开 `visibility=1`、未回收、首次发布时间非空且不晚于当前 UTC。所有前台列表、详情、搜索、互动、Feed、地图及附件文章上下文共用此条件，管理员 Token 不扩大前台可见范围。

隐藏分类不隐藏文章，文章的 category 返回 null；按隐藏/不存在分类或不存在标签筛选返回空页。公开列表所有条件 AND 组合；keyword 为标题、摘要与 Markdown 原文字面包含。前后台均支持 authorId 筛选，不能扩大 PUBLIC 条件或当前后台权限范围。

普通删除只移入回收站，保留正文、标签、互动和 slug；恢复为下架，不自动公开。永久删除见 [22.7](API_ADMIN.md#api-159)，须先回收并同时满足 delete 与 purge 权限范围。详情包含上下篇及最多 6 条同分类公开推荐，无须额外查询接口。

### 13.3 互动、订阅与统计

评论为纯文本，有隐藏祖先的评论分支不公开。留言的悄悄话沿完整父链限制可见性，管理员通过后台读取。评论和留言发表后均不支持再次修改。评论与留言删除均在事务中物理移除目标及全部子回复，不生成占位。相关规则见 [评论](API_CONTRACT.md#comment-rules) 与 [留言](API_CONTRACT.md#message-rules)。

订阅邮箱独立确认，不把账号联系邮箱当作已验证邮箱。首次公开发布及留言实际审核通过后再通知；发送失败不回滚已提交业务，进程内通知不保证跨宕机投递。RSS/站点地图公开读取，不依赖邮件订阅。

文章发布通知按收件人有限并发投递，与留言回复通知共用发送名额；获得名额后重新检查文章 PUBLIC、当前订阅邮箱及订阅轮次。不同收件人的到达顺序不作保证，单封发送失败不影响其他收件人。接口成功仅表示业务提交成功，不表示通知已全部送达。验证码和申请/重发订阅确认信仍同步等待 SMTP 接受，发送失败返回503。

累计文章阅读数与页面浏览采集分别计算；浏览记录清理影响保留明细的 PV/UV，不能当作文章累计阅读回退。统计日期按 UTC，时间区间与补零规则见 [统计约定](API_CONTRACT.md#analytics-rules)。

<a id="file-consistency"></a>

### 13.4 文件引用与访问

先创建草稿取得 articleId，再上传并在正文/封面引用文件；公开附件路径为 `/api/v1/blog/articles/{articleId}/files/{fileId}/content`，必须对应当前文章并实际引用。后台详情的安全 HTML 使用后台附件路径，客户端通过后台会话获取后生成临时 object URL。

编辑器预览当前文章时，通过 `GET /api/v1/admin/files/{fileId}/content` 读取尚未保存引用的图片，需要 `blog:file:query` 及该文件的查询范围。保存后，文章详情和公开阅读按各自的文章附件接口检查引用。

公开文章确实引用的文件可从该文章上下文读取；草稿、私密、下架和回收站上下文返回 404。后台读取同时满足文章和文件各自权限范围。音乐使用独立的[公开音乐文件路径](API_BLOG.md#music-file-content)，保存时检查文件权限与用途。头像、友链头像、经历 Logo 和图标可使用外部地址或[公开展示图片地址](API_BLOG.md#display-image-content)。

文章（含回收站）和音乐（含隐藏曲目）的受管附件引用会阻止删除。公开展示图片地址可以被任意页面引用，删除前需确认影响，删除后地址失效。删除文件不要求版本，存储失败时保留元数据并返回 503。详细规则见 [文件开发约定](API_CONTRACT.md#file-consistency)。

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
| 303（完成页 fragment） | OAUTH_NOT_BOUND | 第三方身份未绑定。先完成邮箱注册，再登录绑定 |
| 400 | CAPTCHA_INVALID | 验证码缺失、格式错误、过期、错误或已消费，需重新获取 |
| 400 | VALIDATION_ERROR | 字段、枚举、分页、查询组合或 JSON 非法 |
| 400 | OAUTH_STATE_INVALID | state/浏览器/操作不匹配、过期或已消费；不泄露哪一项失败 |
| 303（完成页 fragment） | OAUTH_DENIED | 用户在平台取消授权 |
| 400 | SUBSCRIPTION_TICKET_INVALID | 订阅确认或邮件退订票据无效、过期、复用或与当前用户/邮箱/轮次不符；统一错误，不泄露匹配项 |
| WebSocket | ONLINE_TICKET_INVALID | 在线票据格式错、过期、复用或来源/用途不匹配；1008 关闭，重新申请票据 |
| 401 | UNAUTHENTICATED | 登录会话缺失、失效、过期或被注销 |
| 401 | AUTH_FAILED | 账密登录名不存在、密码错误或无本站密码时提示“账号或密码错误”；邮箱登录和发送登录验证码找不到邮箱账号时提示“该邮箱未注册” |
| 403 | ACCOUNT_DISABLED | 已确认凭据身份后发现本站账号禁用 |
| 403 | VISITOR_BLOCKED | 仅访问采集接口限制此统计标识；不代表本站账号被禁用，不阻止公开阅读 |
| 403 | ACCESS_BLOCKED | 可信客户端 IP 已被封禁；反复 404 触发的自动封禁附 Retry-After |
| 403 | FORBIDDEN | 权限不足；全局操作拒绝 |
| 403 | REAUTH_REQUIRED | 安全操作需最近 5 分钟内重新登录 |
| 404 | NOT_FOUND | 不存在、非公开文章、SELF 不可访问的他人资源；不泄露目标存在性 |
| 409 | UNIQUE_CONFLICT | 唯一键冲突时 msg 指出具体字段及修改方式，errors 保留对应请求字段；涵盖文章 URL 标识、分类名称/URL 标识、标签名称/URL 标识、角色编码、权限编码、菜单编码/路由名称/路由路径、账号和订阅等全部已声明唯一键。账号多字段冲突仍按[重复情况](#user-unique-conflict)返回；文章标题允许重复；未识别的内部冲突提示刷新并编辑已有记录，不回显 SQL、约束名或冲突值 |
| 303（完成页 fragment） | OAUTH_BIND_CONFLICT | 身份已属他人，或本站用户同平台已绑定其他身份 |
| 409 | LAST_LOGIN_METHOD | 清除密码/解绑会失去最后一种可用登录方式 |
| 409 | LAST_ADMIN | 会使最后一个有效管理员失效 |
| 409 | SESSION_ACCOUNT_CHANGED | 页面预期账号与 Cookie 账号不一致，或认证写请求缺少预期账号。拒绝操作并保留 Cookie，由页面重新核验身份。 |
| 409 | STATE_CONFLICT | 当前状态不支持操作，如回收站编辑、父评论已不可回复 |
| 409 | RESOURCE_IN_USE | 菜单有子节点、权限被菜单引用、文件仍有引用等 |
| 412 | REVISION_CONFLICT | 提交的版本与当前资源不符。重新读取详情后合并 |
| 413 | PAYLOAD_TOO_LARGE | 请求体/正文/文件超限 |
| 415 | UNSUPPORTED_MEDIA_TYPE | 上传类型或接口 Content-Type 不支持 |
| 428 | PRECONDITION_REQUIRED | 单条写操作缺少 `If-Revision`，或批量操作中目标缺少版本 |
| 429 | RATE_LIMITED | 超出限流；携带 Retry-After 秒数 |
| 303（完成页 fragment） | OAUTH_UPSTREAM_ERROR | 平台换码或获取身份失败 |
| 503 | SERVICE_UNAVAILABLE | Redis、数据库或文件存储不可用，安全校验失败时禁止放行 |
| 500 | INTERNAL_ERROR | 未预期异常，不返回 SQL、堆栈、凭据或内部路径 |

可信 OAuth 回调在消费 state 后失败时，通过 303 跳转到完成页，并在 fragment 中返回 `error`。state 无效或浏览器不符时直接返回 HTTP JSON 错误。框架产生的 405 包装为 Result，使用 `METHOD_NOT_ALLOWED`。反向代理在进入应用前拒绝的请求可能使用其他响应格式。

```json
{"code":"VALIDATION_ERROR","msg":"请求参数不合法","data":null,"errors":[{"field":"title","message":"长度应为1至200字符"}]}
```

### 14.2 鉴权失败示例

缺失、过期或已撤销会话的受保护请求统一 401；有效登录但缺少业务权限返回 403；有权限而资源不属于本次 SELF 范围返回 404。OAuth 流程中的可信失败按固定回跳规则处理，不能把此规则推广到普通 API。

```http
HTTP/1.1 403 Forbidden
Content-Type: application/json; charset=UTF-8
Cache-Control: no-store
```

```json
{"code":"FORBIDDEN","msg":"无权执行该操作","data":null,"errors":[]}
```

<a id="delivery"></a>

接口实施状态以本文标记为准；配置与当前检查入口见 [后端运行说明](后端运行说明.md)。
