# DriftingLeavesV2 接口文档

> **文档说明**：按业务模块介绍接口调用方式，更新日期：2026-10-31。当前 153 个路由已实现；第 22 章的 6 个新路由与 1 项参数扩展待实现。
>
> **维护提示**：接口统一列出基本信息、请求参数、请求示例、响应参数与响应示例，特殊规则放在接口末尾。示例使用虚构数据；普通成功状态、鉴权、分页和版本要求统一见第 0 章。

---

## 目录

- [按业务名称或完整路径查找全部接口](API_INDEX.md)
- [0. 通用说明](#common)
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
- [12. 响应模型](#models)
- [13. 业务补充说明](#business)
- [14. 错误码](#errors)
- [15. 主页展示与在线简历](#group-15)
- [16. 留言板、本人留言与审核](#group-16)
- [17. 背景音乐与播放列表](#group-17)
- [18. 邮件订阅、RSS Feed 与站点地图](#group-18)
- [19. 访客、浏览记录与统计报表](#group-19)
- [20. 操作日志](#group-20)
- [21. 旧项目来源与迁移对照](#legacy-sources)
- [22. 文章发现与内容管理补充设计（待实现）](#group-22)

---

<a id="common"></a>

## 0. 通用说明

### 0.1 路径与鉴权

- 下文使用完整路径，统一前缀 `/api/v1`。JSON 请求使用 `application/json`，上传使用 `multipart/form-data`。
- 登录后通过 `Authorization: Bearer <accessToken>` 传递本站 Sa-Token。Token 有效期 7200 秒，无 refreshToken；浏览器仅在内存保存。
- **公开**：无需登录；**登录本人**：账号启用，身份只取当前会话；**后台：P**：账号启用，同时具备 `admin:access` 与权限 P。未认证 401，无权限 403。
- 文章、文件、评论按本次权限的 ALL/SELF 范围检查；范围外资源返回 404。多个权限分别检查范围。分类、标签、用户、角色等全局资源不产生“本人版本”；标注 ALL 的操作必须具备 ALL 范围。
- 密码变更、清除密码、发起绑定与解绑要求最近 300 秒成功认证。密码变更/清除/解绑、账号禁用及授权变更会撤销相关会话；需要时重新登录。
- `/auth`、`/me`、`/admin` 和带身份的响应使用 `Cache-Control: no-store`。OAuth 还需同浏览器 Cookie 与明确 Origin，见 [13.1](#oauth-flow)。

<a id="pagination"></a>

### 0.2 响应与分页

普通接口统一返回 Result；文件流、XML 与 OAuth 回调按各接口说明处理。默认成功 HTTP 200，创建通常为 201；特殊状态在基本信息中注明。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | string | 成功为 `OK`，失败见 [错误码](#errors) |
| `msg` | string | 成功为 `成功`，失败为可读提示 |
| `data` | object/array/null | 业务数据；无返回值或失败时为 null |
| `errors` | array | 始终存在；字段校验失败时为 `{field,message}[]`，其余为 [] |

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

没有通用幂等键。创建超时先查询确认；点赞/取消点赞、注销、占位删除可幂等，OAuth state/ticket 一次性。429 读取 Retry-After；服务不可用返回 503，不当作成功重试。跨域需允许 Authorization/If-Revision 并暴露 X-Revision/Location/Retry-After。

### 0.5 查找接口

- [完整接口索引](API_INDEX.md)：按模块、业务名称或完整路径查找。
- [文章场景速查](#article-scenarios)：删除、分类/标签/作者筛选、搜索、归档、回收和恢复。
- 当前 **153 个路由已实现**；[第 22 章](#group-22)另有 **6 个待实现路由和 1 项参数扩展**，不计入已实现数量。
- 字段与状态、锁顺序、事务及平台接入的完整开发约定见 [接口开发约定](API_CONTRACT.md)，部署与历史验收见 [后端运行说明](后端运行说明.md)。

---

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

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 | 3..64 位 ASCII 字母、数字或下划线；全库不区分大小写唯一 |
| `password` | string | 是 | 8..64 个字符且 UTF-8 ≤72 字节；不 trim、不截断 |
| `nickname` | string | 是 | 1..64 字符 |

#### 请求示例

```http
POST /api/v1/auth/register
Content-Type: application/json

{"username":"leaf","password":"Example-password-2026","nickname":"落叶"}
```

#### 响应参数

`data`：[Me](#response-me)。

#### 响应示例

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

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 | 3..64 位 ASCII 字母、数字或下划线；全库不区分大小写唯一 |
| `password` | string | 是 | 8..64 个字符且 UTF-8 ≤72 字节；不 trim、不截断 |

#### 请求示例

```http
POST /api/v1/auth/login
Content-Type: application/json

{"username":"leaf","password":"Example-password-2026"}
```

#### 响应参数

`data`：[Session](#response-session)。

#### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "tokenType": "Bearer", "accessToken": "<site-token>", "expiresIn": 7200,
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

### 1.3 退出当前登录

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/auth/logout` |
| 接口描述 | 退出当前登录 |
| Content-Type | 无请求体 |
| 访问要求 | 幂等注销；无 Token 或已失效仍返回 200 |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
POST /api/v1/auth/logout
Authorization: Bearer <site-token>
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

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

#### 请求参数

无 Path、Query 或请求体参数；GET/无模型写操作不发送请求体。

#### 请求示例

```http
GET /api/v1/auth/oauth/providers
```

#### 响应参数

`data`：[Provider](#response-provider) 数组。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":[{"source":"GITHUB","displayName":"GitHub"}],"errors":[]}
```

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

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `source` | string | 是 | Path；服务端已启用平台的大写标识；未知或未启用 404 |
| `operation` | string | 是 | LOGIN 登录；BIND 绑定当前用户；不接收 redirectUri/userId/uuid |
| `__Host-dl-oauth-browser` | string | 是（发起时可由后端新设） | Cookie；Secure、HttpOnly、SameSite=Lax、Path=/，仅绑定浏览器，不是业务登录凭证 |
| `Origin` | string | 是 | Header；浏览器自动发送，必须匹配配置前端 origin；BIND/兑换附加身份要求见访问要求 |

#### 请求示例

```http
POST /api/v1/auth/oauth/GITHUB/authorize
Cookie: __Host-dl-oauth-browser=<browser-id>
Origin: https://blog.example.com
Content-Type: application/json

{"operation":"LOGIN"}
```

#### 响应参数

`data`：[OAuthAuthorizeResult](#response-oauthauthorizeresult)。

#### 响应示例

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

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `source` | string | 是 | Path；服务端已启用平台的大写标识；未知或未启用 404 |
| `__Host-dl-oauth-browser` | string | 是（发起时可由后端新设） | Cookie；Secure、HttpOnly、SameSite=Lax、Path=/，仅绑定浏览器，不是业务登录凭证 |
| `state` | string | 是 | 与当前浏览器、平台和流程匹配，一次性 |
| `code` | string | 成功时 | 所选平台 GET 回调授权码；适配器负责平台实际参数 |
| `error` | string | 拒绝时 | 平台取消/错误标记，不透传平台说明 |
| `error_description` | string | 否 | 平台错误说明，仅允许带回；不交给业务、不写日志、不回显 |

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

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ticket` | string | 是 | 回调得到的本站一次性票据；有效期 60 秒，不是平台 code 或本站 Token |
| `__Host-dl-oauth-browser` | string | 是（发起时可由后端新设） | Cookie；Secure、HttpOnly、SameSite=Lax、Path=/，仅绑定浏览器，不是业务登录凭证 |
| `Origin` | string | 是 | Header；浏览器自动发送，必须匹配配置前端 origin；BIND/兑换附加身份要求见访问要求 |

#### 请求示例

```http
POST /api/v1/auth/oauth/exchange
Cookie: __Host-dl-oauth-browser=<browser-id>
Origin: https://blog.example.com
Content-Type: application/json

{"ticket":"<one-time-ticket>"}
```

#### 响应参数

`data`：[OAuthResult](#response-oauthresult)。

#### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "operation": "LOGIN",
    "session": {
      "tokenType": "Bearer", "accessToken": "<site-token>", "expiresIn": 7200,
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

## 2. 本人资料与安全设置

<a id="api-8"></a>

### 2.1 查询本人资料

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/me` |
| 接口描述 | 查询本人资料 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
GET /api/v1/me
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[Me](#response-me)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

### 2.2 修改本人资料

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PATCH /api/v1/me/profile` |
| 接口描述 | 修改本人资料 |
| Content-Type | application/json |
| 访问要求 | 登录本人 |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `nickname` | string | 否 | 提供时不可为 null，1..64 字符 |
| `email` | string&#124;null | 否 | 合法邮箱，≤254 字符，唯一；null 清空 |
| `avatar` | string&#124;null | 否 | 外部 http(s) URL ≤2048 字符；null 清空 |
| `intro` | string&#124;null | 否 | 纯文本 ≤500 字符；null 清空；四项至少提供一项 |

#### 请求示例

```http
PATCH /api/v1/me/profile
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"nickname":"新落叶","email":null,"avatar":null,"intro":null}
```

#### 响应参数

`data`：[Me](#response-me)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

### 2.3 设置或修改本站密码

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/me/password` |
| 接口描述 | 设置或修改本站密码 |
| Content-Type | application/json |
| 访问要求 | 登录本人 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `newPassword` | string | 是 | 同注册密码规则 |
| `currentPassword` | string | 条件必填 | 已有密码时必填且必须验证；纯第三方无密码账号必须省略 |

#### 请求示例

```http
PUT /api/v1/me/password
Authorization: Bearer <site-token>
Content-Type: application/json

{"newPassword":"New-example-password-2026","currentPassword":"Example-password-2026"}
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

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
| 访问要求 | 登录本人 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `currentPassword` | string | 是 | 验证现有密码；无密码账号返回 STATE_CONFLICT |

#### 请求示例

```http
DELETE /api/v1/me/password
Authorization: Bearer <site-token>
Content-Type: application/json

{"currentPassword":"Example-password-2026"}
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

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
| 访问要求 | 登录本人 |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
GET /api/v1/me/capabilities
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[Capabilities](#response-capabilities)。

#### 响应示例

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

### 2.6 查询本人可访问菜单树

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/me/menus` |
| 接口描述 | 查询本人可访问菜单树 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
GET /api/v1/me/menus
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[MenuNode](#response-menunode) 数组。

#### 响应示例

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

### 2.7 查询本人第三方绑定

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/me/oauth-bindings` |
| 接口描述 | 查询本人第三方绑定 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
GET /api/v1/me/oauth-bindings
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[OAuthBinding](#response-oauthbinding) 数组。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":[{"source":"GITHUB","createTime":"2026-10-29T02:00:00.000Z"}],"errors":[]}
```

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
| 访问要求 | 登录本人 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `source` | string | 是 | Path；服务端已启用平台的大写标识；未知或未启用 404 |

#### 请求示例

```http
DELETE /api/v1/me/oauth-bindings/GITHUB
Authorization: Bearer <site-token>
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

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
| 访问要求 | 后台：`sys:user:query` |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `status` | integer | 否 | 可选，0/1；含义见该业务状态约定 |
| `roleId` | ID | 否 | 可选，按关联角色筛选 |

#### 请求示例

```http
GET /api/v1/admin/users?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [UserAdmin](#response-useradmin)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

### 3.2 查询用户详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/users/{userId}` |
| 接口描述 | 查询用户详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`sys:user:query` |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/users/12
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[UserAdmin](#response-useradmin)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

### 3.3 创建用户

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/users` |
| 接口描述 | 创建用户 |
| Content-Type | application/json |
| 访问要求 | 后台：`sys:user:create` |
| 成功状态 | 201 Created |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 | 3..64 位 ASCII 字母、数字或下划线；全库不区分大小写唯一 |
| `password` | string | 是 | 8..64 个字符且 UTF-8 ≤72 字节；不 trim、不截断 |
| `nickname` | string | 是 | 1..64 字符 |
| `email` | string&#124;null | 否 | 同本人资料，默认 null |
| `avatar` | string&#124;null | 否 | 仅外部 http(s)，默认 null |
| `intro` | string&#124;null | 否 | ≤500 字符，默认 null |
| `status` | integer | 否 | 0 禁用、1 启用，默认 1；不接受 roleIds |

#### 请求示例

```http
POST /api/v1/admin/users
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "username": "leaf", "password": "Example-password-2026", "nickname": "落叶", "email": null, "avatar": null,
  "intro": null, "status": 1
}
```

#### 响应参数

`data`：[UserAdmin](#response-useradmin-2)。

#### 响应示例

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

新建用户不分配角色；需要 assignRole 权限才能另行分配，不沿用公开注册的 member 默认。

---

<a id="api-19"></a>

### 3.4 修改用户资料及状态

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/users/{userId}` |
| 接口描述 | 修改用户资料及状态 |
| Content-Type | application/json |
| 访问要求 | 后台：`sys:user:update` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `nickname` | string | 是 | 1..64 字符 |
| `email` | string&#124;null | 是，可 null | 合法邮箱，≤254 字符，唯一 |
| `avatar` | string&#124;null | 是，可 null | 仅外部 http(s) URL |
| `intro` | string&#124;null | 是，可 null | ≤500 字符 |
| `status` | integer | 是 | 0/1；禁用撤销全部会话 |

#### 请求示例

```http
PUT /api/v1/admin/users/12
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"nickname":"落叶","email":null,"avatar":null,"intro":null,"status":1}
```

#### 响应参数

`data`：[UserAdmin](#response-useradmin)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

### 3.5 分配用户角色

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/users/{userId}/roles` |
| 接口描述 | 分配用户角色 |
| Content-Type | application/json |
| 访问要求 | 后台：`sys:user:assignRole` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `roleIds` | ID[] | 是 | 0..100 个，不重复、存在且启用；[] 清空；整体替换 |

#### 请求示例

```http
PUT /api/v1/admin/users/12/roles
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"roleIds":["3"]}
```

#### 响应参数

`data`：[UserAdmin](#response-useradmin)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

## 4. 角色与授权管理

<a id="api-21"></a>

### 4.1 分页查询角色

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/roles` |
| 接口描述 | 分页查询角色 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`sys:role:query` |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `status` | integer | 否 | 可选，0/1；含义见该业务状态约定 |

#### 请求示例

```http
GET /api/v1/admin/roles?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [Role](#response-role)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

### 4.2 查询角色详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/roles/{roleId}` |
| 接口描述 | 查询角色详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`sys:role:query` |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `roleId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/roles/4
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[Role](#response-role)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

### 4.3 创建角色

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/roles` |
| 接口描述 | 创建角色 |
| Content-Type | application/json |
| 访问要求 | 后台：`sys:role:create` |
| 成功状态 | 201 Created |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `roleCode` | string | 是 | [a-z][a-z0-9_]{0,63}，唯一且创建后不可变 |
| `roleName` | string | 是 | 1..64 字符 |
| `description` | string&#124;null | 否 | ≤255 字符，默认 null |
| `dataScope` | string | 否 | ALL/SELF，默认 SELF |
| `status` | integer | 否 | 0/1，默认 1 |

#### 请求示例

```http
POST /api/v1/admin/roles
Authorization: Bearer <site-token>
Content-Type: application/json

{"roleCode":"editor","roleName":"内容编辑","description":null,"dataScope":"SELF","status":1}
```

#### 响应参数

`data`：[Role](#response-role)。

#### 响应示例

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

### 4.4 修改角色

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/roles/{roleId}` |
| 接口描述 | 修改角色 |
| Content-Type | application/json |
| 访问要求 | 后台：`sys:role:update` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `roleId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `roleName` | string | 是 | 1..64 字符 |
| `description` | string&#124;null | 是，可 null | ≤255 字符，默认 null |
| `dataScope` | string | 是 | ALL/SELF，默认 SELF |
| `status` | integer | 是 | 0/1，默认 1 |

#### 请求示例

```http
PUT /api/v1/admin/roles/4
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"roleName":"内容编辑","description":null,"dataScope":"SELF","status":1}
```

#### 响应参数

`data`：[Role](#response-role)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

<a id="api-25"></a>

### 4.5 删除角色

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/roles/{roleId}` |
| 接口描述 | 删除角色 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`sys:role:delete` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `roleId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |

#### 请求示例

```http
DELETE /api/v1/admin/roles/4
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

内置 admin/author/member 禁止删除，返回 409/STATE_CONFLICT；自定义角色即使已分配给用户仍允许删除，级联清理 sys_user_role、sys_role_permission、sys_role_menu 三类关联，保留用户及权限/菜单字典，撤销全部关联用户的所有会话。关联删除使受影响用户的聚合版本变化。Redis 失败则整笔回滚并返回 503。

---

<a id="api-26"></a>

### 4.6 配置角色权限与菜单

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/roles/{roleId}/grants` |
| 接口描述 | 配置角色权限与菜单 |
| Content-Type | application/json |
| 访问要求 | 后台：`sys:role:grant` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `roleId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `permissionIds` | ID[] | 是 | ≤1000 个，不重复；整体替换，权限存在且启用 |
| `menuIds` | ID[] | 是 | ≤1000 个，不重复；存在且启用，自动补齐祖先；页面/按钮权限须显式列在 permissionIds |

#### 请求示例

```http
PUT /api/v1/admin/roles/4/grants
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"permissionIds":["1","10","11"],"menuIds":["110","1011"]}
```

#### 响应参数

`data`：[Role](#response-role)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

## 5. 权限字典管理

<a id="api-27"></a>

### 5.1 分页查询权限字典

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/permissions` |
| 接口描述 | 分页查询权限字典 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`sys:permission:query` |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `module` | string | 否 | 可选，[a-z][a-z0-9_]{0,31} |
| `status` | integer | 否 | 可选，0/1；含义见该业务状态约定 |

#### 请求示例

```http
GET /api/v1/admin/permissions?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [Permission](#response-permission)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

### 5.2 查询权限详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/permissions/{permissionId}` |
| 接口描述 | 查询权限详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`sys:permission:query` |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `permissionId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/permissions/114
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[Permission](#response-permission)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

### 5.3 创建权限字典项

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/permissions` |
| 接口描述 | 创建权限字典项 |
| Content-Type | application/json |
| 访问要求 | 后台：`sys:permission:create` |
| 成功状态 | 201 Created |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `permissionCode` | string | 是 | ≤100 字符；模块:资源:动作，允许 admin:access 例外，创建后不可变 |
| `permissionName` | string | 是 | 1..64 字符 |
| `module` | string | 是 | [a-z][a-z0-9_]{0,31} |
| `description` | string&#124;null | 否 | ≤255 字符，默认 null |
| `status` | integer | 否 | 0/1，默认 1 |

#### 请求示例

```http
POST /api/v1/admin/permissions
Authorization: Bearer <site-token>
Content-Type: application/json

{"permissionCode":"blog:article:export","permissionName":"导出文章","module":"blog","description":null,"status":1}
```

#### 响应参数

`data`：[Permission](#response-permission)。

#### 响应示例

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

### 5.4 修改权限字典项

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/permissions/{permissionId}` |
| 接口描述 | 修改权限字典项 |
| Content-Type | application/json |
| 访问要求 | 后台：`sys:permission:update` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `permissionId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `permissionName` | string | 是 | 1..64 字符 |
| `module` | string | 是 | [a-z][a-z0-9_]{0,31} |
| `description` | string&#124;null | 是，可 null | ≤255 字符，默认 null |
| `status` | integer | 是 | 0/1，默认 1 |

#### 请求示例

```http
PUT /api/v1/admin/permissions/114
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"permissionName":"导出文章","module":"blog","description":null,"status":1}
```

#### 响应参数

`data`：[Permission](#response-permission)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

<a id="api-31"></a>

### 5.5 删除权限字典项

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/permissions/{permissionId}` |
| 接口描述 | 删除权限字典项 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`sys:permission:delete` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `permissionId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |

#### 请求示例

```http
DELETE /api/v1/admin/permissions/114
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

固定后端权限（当前 AuthorizationProvider.ADMIN_PERMISSIONS 明确列出的后台入口、用户、角色、菜单及权限管理权限）禁止删除，返回 409/STATE_CONFLICT。此白名单不随 SQL 新增字典项自动扩大。任何菜单引用（含禁用或隐藏节点）均返回 409/RESOURCE_IN_USE，不能静默移除门禁。

其他字典项可物理删除，已授权给角色不是额外删除限制；角色权限关联由现有外键 CASCADE 清理，菜单外键保持 RESTRICT。采用与修改相同的保护锁、升序用户锁和目标权限锁，先锁内复核身份、双权限及版本，再检查删除限制。删除前枚举并锁定全部关联用户，撤销所有会话后提交；Redis 失败回滚字典及角色关联，返回 503。数据库最终提交失败时会话可能已撤销，MySQL 与 Redis 无共同事务。

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
| 访问要求 | 后台：`sys:menu:query` |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
GET /api/v1/admin/menus
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[MenuNode](#response-menunode) 数组。

#### 响应示例

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

### 6.2 查询菜单详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/menus/{menuId}` |
| 接口描述 | 查询菜单详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`sys:menu:query` |
| 并发校验 | 返回 X-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `menuId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/menus/110
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[Menu](#response-menu)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

### 6.3 创建菜单节点

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/menus` |
| 接口描述 | 创建菜单节点 |
| Content-Type | application/json |
| 访问要求 | 后台：`sys:menu:create` |
| 成功状态 | 201 Created |

#### 请求参数

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

#### 请求示例

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

#### 响应参数

`data`：[Menu](#response-menu)。

#### 响应示例

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

### 6.4 修改菜单节点

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/menus/{menuId}` |
| 接口描述 | 修改菜单节点 |
| Content-Type | application/json |
| 访问要求 | 后台：`sys:menu:update` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

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

#### 请求示例

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

#### 响应参数

`data`：[Menu](#response-menu)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

<a id="api-36"></a>

### 6.5 删除菜单节点

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/menus/{menuId}` |
| 接口描述 | 删除菜单节点 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`sys:menu:delete` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `menuId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |

#### 请求示例

```http
DELETE /api/v1/admin/menus/110
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

沿用修改的结构锁、升序用户锁和目标节点锁，锁内复核原 Token、身份、双权限及版本。有任意状态子节点返回 409/RESOURCE_IN_USE；叶子删除级联 role_menu，使关联角色聚合版本变化，独立 role_permission、用户及会话均保留。菜单和权限实时读库；Redis 身份校验不可用返回 503，不能跳过身份校验提交写入。

---

<a id="group-7"></a>

## 7. 文章与归档

前台仅展示公开文章；后台提供草稿、编辑、发布/下架、回收与恢复。常用筛选和删除流程见 [7.12](#article-scenarios)。

<a id="api-37"></a>

### 7.1 分页查询公开文章

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/articles` |
| 接口描述 | 分页查询公开文章 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；文章须公开 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `categoryId` | ID | 否 | 可选，分类筛选 |
| `tagId` | ID | 否 | 可选，标签筛选 |
| `month` | string | 否 | 可选，合法 YYYY-MM，年份 1000..9999；按 UTC 月份过滤 |

#### 请求示例

```http
GET /api/v1/blog/articles?page=1&pageSize=10
```

#### 响应参数

`data`：分页对象，`records[]` 为 [ArticlePublic](#response-articlepublic)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

按分类、标签、关键词和月份查询的独立调用示例见 [7.12](#article-scenarios)。当前公开查询不接受 `authorId`，传入会返回 400；前台作者筛选的新增契约见 [22.1](#article-author-query)，尚待实现。

---

<a id="api-38"></a>

### 7.2 按 slug 查询公开文章详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/articles/{slug}` |
| 接口描述 | 按 slug 查询公开文章详情 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；文章须公开 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `slug` | string | 是 | Path；文章 slug ≤200，区分大小写 |

#### 请求示例

```http
GET /api/v1/blog/articles/first-leaf
```

#### 响应参数

`data`：[ArticlePublicDetail](#response-articlepublicdetail)。

#### 响应示例

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

---

<a id="api-39"></a>

### 7.3 分页查询文章月归档

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/archives` |
| 接口描述 | 分页查询文章月归档 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；文章须公开 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |

#### 请求示例

```http
GET /api/v1/blog/archives?page=1&pageSize=10
```

#### 响应参数

`data`：分页对象，`records[]` 为 [Archive](#response-archive)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

<a id="api-40"></a>

### 7.4 分页查询后台文章

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/articles` |
| 接口描述 | 分页查询后台文章 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:article:query` |

#### 请求参数

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

#### 请求示例

```http
GET /api/v1/admin/articles?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [ArticleAdminSummary](#response-articleadminsummary)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

### 7.5 查询后台文章详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/articles/{articleId}` |
| 接口描述 | 查询后台文章详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:article:query` |
| 并发校验 | 返回 X-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/articles/101
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[ArticleAdminDetail](#response-articleadmindetail)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

### 7.6 创建草稿文章

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/articles` |
| 接口描述 | 创建草稿文章 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:article:create` |
| 成功状态 | 201 Created |

#### 请求参数

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

#### 请求示例

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

#### 响应参数

`data`：[ArticleAdminDetail](#response-articleadmindetail)。

#### 响应示例

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

### 7.7 编辑文章

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/articles/{articleId}` |
| 接口描述 | 编辑文章 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:article:update` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

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

#### 请求示例

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

#### 响应参数

`data`：[ArticleAdminDetail](#response-articleadmindetail)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

<a id="api-44"></a>

### 7.8 发布或下架文章

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/articles/{articleId}/status` |
| 接口描述 | 发布或下架文章 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:article:publish` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `status` | integer | 是 | 1 发布、2 下架；禁止传 publishTime，回收站不可变更 |

#### 请求示例

```http
PUT /api/v1/admin/articles/101/status
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"status":1}
```

#### 响应参数

`data`：[ArticleAdminDetail](#response-articleadmindetail-2)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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
    "category": null, "title": "第一片落叶", "slug": "first-leaf", "status": 1
  },
  "errors": []
}
```

草稿→发布、发布→下架、下架→发布；首次发布写服务端当前 UTC，之后保留，不支持定时发布。

---

<a id="api-45"></a>

### 7.9 将文章移入回收站

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/articles/{articleId}` |
| 接口描述 | 将文章移入回收站 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:article:delete` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |

#### 请求示例

```http
DELETE /api/v1/admin/articles/101
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

仅写 deletedAt，不物理清理；保留正文、slug、标签与互动。同版本且已回收可幂等成功。

这是当前“删除文章”的业务入口，删除后不再满足 PUBLIC。需要找回时按 [7.10](#api-46) 恢复；需要不可恢复清理时，新增设计见 [22.7](#api-159)，当前尚不支持永久删除。

本接口及恢复接口均无请求体；实际发送 JSON 或其他载荷返回 400，不静默忽略身份、状态等未声明字段。

---

<a id="api-46"></a>

### 7.10 恢复回收站文章

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/articles/{articleId}/restore` |
| 接口描述 | 恢复回收站文章 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:article:restore` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |

#### 请求示例

```http
POST /api/v1/admin/articles/101/restore
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

`data`：[ArticleAdminDetail](#response-articleadmindetail)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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
    "category": null, "title": "第一片落叶", "slug": "first-leaf", "status": 2
  },
  "errors": []
}
```

恢复固定下架 status=2，保留首次发布时间，须显式发布才公开。非回收站 409。

---

<a id="api-87"></a>

### 7.11 查询文章热门榜

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/article-rankings` |
| 接口描述 | 查询文章热门榜 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `period` | string | 否 | site全站/month本月，默认site |
| `metric` | string | 否 | view浏览/like点赞，默认view |
| `limit` | integer | 否 | 默认5，1..20 |

#### 请求示例

```http
GET /api/v1/blog/article-rankings?period=month&metric=view&limit=5
```

#### 响应参数

`data`：[HotArticle](#response-hotarticle) 数组。

#### 响应示例

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

### 7.12 文章业务场景速查

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
| 删除文章 | `DELETE /api/v1/admin/articles/101` | 已实现；[7.9](#api-45)，带 Token 与详情取得的 If-Revision，移入回收站 |
| 查询回收站 | `GET /api/v1/admin/articles?trash=1&page=1&pageSize=10` | 已实现；仍按 query 范围过滤，不与正常文章混查 |
| 恢复文章 | `POST /api/v1/admin/articles/101/restore` | 已实现；[7.10](#api-46)，先读取回收站详情取得版本，恢复为下架，不自动公开 |
| 后台预览草稿或下架内容 | `GET /api/v1/admin/articles/101` | 已实现；[7.5](#api-41) 返回 Markdown 与安全 HTML，公开详情不会因 Token 放宽可见性 |
| 阅读上下篇和同分类推荐 | `GET /api/v1/blog/articles/first-leaf` | 已实现；[7.2](#api-38) 返回 prevArticle、nextArticle、relatedArticles，同分类推荐最多 6 条；详情成功会增加阅读量 |
| 前台按作者查询公开文章 | `GET /api/v1/blog/articles?authorId=12&page=1&pageSize=10` | **待实现**；[22.1](#article-author-query)，当前请求会被查询白名单拒绝 |

删除流程：在对应权限范围内获取后台详情及 X-Revision → 携带 If-Revision 删除 → 从正常列表移除；回收站若要恢复，重新取详情取得最新版本。缺少版本返回 428，版本过期返回 412。回收站文章仍占用 slug，恢复时沿用原 slug；永久删除的单独设计见 [22.7](#api-159)。

分类/标签页目前可从分页字典列表取得 ID，再调用上述文章列表。直接打开 `/categories/{slug}` 或 `/tags/{slug}` 时定位单个字典的补充设计见 [22.4](#api-156)、[22.5](#api-157)，无需让客户端遍历所有字典分页。

---

<a id="group-8"></a>

## 8. 分类与标签

分类与标签是全局字典。删除分类会清空文章分类，删除标签会清理关联，文章与正文保留。分类隐藏时不在前台字典与分类筛选中暴露，文章仍可公开。

<a id="api-47"></a>

### 8.1 分页查询公开分类

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/categories` |
| 接口描述 | 分页查询公开分类 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；文章须公开 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |

#### 请求示例

```http
GET /api/v1/blog/categories?page=1&pageSize=10
```

#### 响应参数

`data`：分页对象，`records[]` 为 [CategoryPublic](#response-categorypublic)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

<a id="api-48"></a>

### 8.2 分页查询后台分类

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/categories` |
| 接口描述 | 分页查询后台分类 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:category:query` |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `status` | integer | 否 | 可选，0/1；含义见该业务状态约定 |

#### 请求示例

```http
GET /api/v1/admin/categories?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [Category](#response-category)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

### 8.3 查询分类详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/categories/{categoryId}` |
| 接口描述 | 查询分类详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:category:query` |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `categoryId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/categories/1
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[Category](#response-category)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

### 8.4 创建分类

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/categories` |
| 接口描述 | 创建分类 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:category:create` |
| 成功状态 | 201 Created |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `categoryName` | string | 是 | 1..64，按库排序规则唯一 |
| `slug` | string | 是 | ASCII slug ≤100，区分大小写唯一 |
| `description` | string&#124;null | 否 | ≤255，默认 null |
| `sortOrder` | integer | 否 | int32，默认 0 |
| `status` | integer | 否 | 0/1，默认 1 |

#### 请求示例

```http
POST /api/v1/admin/categories
Authorization: Bearer <site-token>
Content-Type: application/json

{"categoryName":"技术","slug":"tech","description":null,"sortOrder":0,"status":1}
```

#### 响应参数

`data`：[Category](#response-category)。

#### 响应示例

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

### 8.5 修改分类

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/categories/{categoryId}` |
| 接口描述 | 修改分类 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:category:update` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `categoryId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `categoryName` | string | 是 | 1..64，按库排序规则唯一 |
| `slug` | string | 是 | ASCII slug ≤100，区分大小写唯一 |
| `description` | string&#124;null | 是，可 null | ≤255，默认 null |
| `sortOrder` | integer | 是 | int32，默认 0 |
| `status` | integer | 是 | 0/1，默认 1 |

#### 请求示例

```http
PUT /api/v1/admin/categories/1
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"categoryName":"技术","slug":"tech","description":null,"sortOrder":0,"status":1}
```

#### 响应参数

`data`：[Category](#response-category)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

<a id="api-52"></a>

### 8.6 删除分类

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/categories/{categoryId}` |
| 接口描述 | 删除分类 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:category:delete` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `categoryId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |

#### 请求示例

```http
DELETE /api/v1/admin/categories/1
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

文章 categoryId 置 null；关联变化须令文章聚合 X-Revision 失效。

---

<a id="api-53"></a>

### 8.7 分页查询公开标签

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/tags` |
| 接口描述 | 分页查询公开标签 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；文章须公开 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |

#### 请求示例

```http
GET /api/v1/blog/tags?page=1&pageSize=10
```

#### 响应参数

`data`：分页对象，`records[]` 为 [TagPublic](#response-tagpublic)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

<a id="api-54"></a>

### 8.8 分页查询后台标签

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/tags` |
| 接口描述 | 分页查询后台标签 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:tag:query` |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |

#### 请求示例

```http
GET /api/v1/admin/tags?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [Tag](#response-tag)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

### 8.9 查询标签详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/tags/{tagId}` |
| 接口描述 | 查询标签详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:tag:query` |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `tagId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/tags/1
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[Tag](#response-tag)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

### 8.10 创建标签

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/tags` |
| 接口描述 | 创建标签 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:tag:create` |
| 成功状态 | 201 Created |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `tagName` | string | 是 | 1..64，按库排序规则唯一 |
| `slug` | string | 是 | ASCII slug ≤100，区分大小写唯一 |

#### 请求示例

```http
POST /api/v1/admin/tags
Authorization: Bearer <site-token>
Content-Type: application/json

{"tagName":"Java","slug":"java"}
```

#### 响应参数

`data`：[Tag](#response-tag)。

#### 响应示例

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

### 8.11 修改标签

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/tags/{tagId}` |
| 接口描述 | 修改标签 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:tag:update` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `tagId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `tagName` | string | 是 | 1..64，按库排序规则唯一 |
| `slug` | string | 是 | ASCII slug ≤100，区分大小写唯一 |

#### 请求示例

```http
PUT /api/v1/admin/tags/1
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"tagName":"Java","slug":"java"}
```

#### 响应参数

`data`：[Tag](#response-tag)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

<a id="api-58"></a>

### 8.12 删除标签

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/tags/{tagId}` |
| 接口描述 | 删除标签 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:tag:delete` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `tagId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |

#### 请求示例

```http
DELETE /api/v1/admin/tags/1
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

级联清理文章标签关联，保留文章；关联变化须令文章聚合 X-Revision 失效。

---

<a id="group-9"></a>

## 9. 评论审核与点赞

评论为纯文本，审核后展示；删除保留占位。评论创建按用户+IP共用 60 秒 10 次窗口，点赞/取消按用户+IP共用 60 秒 60 次窗口。点赞计数取当前明细，公开评论数只含可见且通过的评论。

<a id="api-59"></a>

### 9.1 分页查询文章公开评论

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/articles/{articleId}/comments` |
| 接口描述 | 分页查询文章公开评论 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；文章须公开 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..50 |
| `parentId` | ID | 否 | 可选，同篇文章的可见父评论；缺失仅根节点 |

#### 请求示例

```http
GET /api/v1/blog/articles/101/comments?page=1&pageSize=10
```

#### 响应参数

`data`：分页对象，`records[]` 为 [CommentPublic](#response-commentpublic)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

### 9.2 发表评论或回复

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/articles/{articleId}/comments` |
| 接口描述 | 发表评论或回复 |
| Content-Type | application/json |
| 访问要求 | 登录 + `blog:comment:create`；目标文章须 PUBLIC |
| 成功状态 | 201 Created |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `content` | string | 是 | trim 后 1..2000 字符纯文本 |
| `parentId` | ID&#124;null | 否 | 默认 null；非 null 须同篇文章、已通过且祖先可见 |

#### 请求示例

```http
POST /api/v1/blog/articles/101/comments
Authorization: Bearer <site-token>
Content-Type: application/json

{"content":"感谢分享。","parentId":null}
```

#### 响应参数

`data`：[CommentOwn](#response-commentown)。

#### 响应示例

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

<a id="api-61"></a>

### 9.3 分页查询本人评论

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/me/comments` |
| 接口描述 | 分页查询本人评论 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..50 |
| `articleId` | ID | 否 | 可选，文章筛选 |
| `status` | integer | 否 | 可选，0/1/2/3；含义见该业务状态约定 |

#### 请求示例

```http
GET /api/v1/me/comments?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [CommentOwn](#response-commentown)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

### 9.4 删除本人评论

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/me/comments/{commentId}` |
| 接口描述 | 删除本人评论 |
| Content-Type | 无请求体 |
| 访问要求 | 登录 + `blog:comment:deleteOwn`；仅本人 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `commentId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
DELETE /api/v1/me/comments/201
Authorization: Bearer <site-token>
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

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
| 访问要求 | 后台：`blog:comment:query` |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..50 |
| `articleId` | ID | 否 | 可选，文章筛选 |
| `userId` | ID | 否 | 可选，评论者筛选，不扩大评论管理范围 |
| `status` | integer | 否 | 可选，0/1/2/3；含义见该业务状态约定 |

#### 请求示例

```http
GET /api/v1/admin/comments?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [CommentAdmin](#response-commentadmin)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

### 9.6 查询后台评论详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/comments/{commentId}` |
| 接口描述 | 查询后台评论详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:comment:query` |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `commentId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/comments/201
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[CommentAdmin](#response-commentadmin)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

<a id="api-65"></a>

### 9.7 审核评论

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/comments/{commentId}/review` |
| 接口描述 | 审核评论 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:comment:review` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `commentId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `status` | integer | 是 | 1 通过、2 驳回；3 占位为终态，不能复活 |

#### 请求示例

```http
PUT /api/v1/admin/comments/201/review
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"status":1}
```

#### 响应参数

`data`：[CommentAdmin](#response-commentadmin)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "commentId": "201", "articleId": "101", "userId": "12", "parentId": null, "content": "感谢分享。", "status": 1
  },
  "errors": []
}
```

支持 0/1/2→1或2；3 为终态。父节点驳回会隐藏整支，恢复后重新计算。

---

<a id="api-66"></a>

### 9.8 后台管理删除评论

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/comments/{commentId}` |
| 接口描述 | 后台管理删除评论 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:comment:moderate` |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `commentId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
DELETE /api/v1/admin/comments/201
Authorization: Bearer <site-token>
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

占位删除幂等，不需 If-Revision；SELF 按文章作者校验；可处理非公开文章评论。

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

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/blog/articles/101/like
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[LikeState](#response-likestate)。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":{"liked":false,"likeCount":"0"},"errors":[]}
```

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
| 访问要求 | 登录 + `blog:article:like`；目标文章须 PUBLIC |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
PUT /api/v1/blog/articles/101/like
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[LikeState](#response-likestate)。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":{"liked":true,"likeCount":"1"},"errors":[]}
```

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
| 访问要求 | 登录 + `blog:article:like`；目标文章须 PUBLIC |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
DELETE /api/v1/blog/articles/101/like
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[LikeState](#response-likestate)。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":{"liked":false,"likeCount":"0"},"errors":[]}
```

不存在也成功且 liked=false；下架后不开放此路径，既有明细保留。

---

<a id="group-10"></a>

## 10. 友链与站点配置

友链仅 status=1 时公开，按 sortOrder/linkId 排序。配置仅支持 site/comment/message：site 按公开标记展示，comment/message 始终私密；不提供任意新增/删除配置键。展示 URL 只用外部 HTTP(S)，不接受本站附件路径。

<a id="api-70"></a>

### 10.1 分页查询公开友链

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/links` |
| 接口描述 | 分页查询公开友链 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；文章须公开 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |

#### 请求示例

```http
GET /api/v1/blog/links?page=1&pageSize=10
```

#### 响应参数

`data`：分页对象，`records[]` 为 [LinkPublic](#response-linkpublic)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

<a id="api-71"></a>

### 10.2 分页查询后台友链

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/links` |
| 接口描述 | 分页查询后台友链 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:link:query` |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `status` | integer | 否 | 可选，0/1/2；含义见该业务状态约定 |

#### 请求示例

```http
GET /api/v1/admin/links?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [Link](#response-link)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

### 10.3 查询友链详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/links/{linkId}` |
| 接口描述 | 查询友链详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:link:query` |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `linkId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/links/1
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[Link](#response-link)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

### 10.4 创建友链

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/links` |
| 接口描述 | 创建友链 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:link:create` |
| 成功状态 | 201 Created |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `linkName` | string | 是 | 1..100，不要求唯一 |
| `url` | string | 是 | http(s) URL ≤2048，不要求唯一 |
| `avatar` | string&#124;null | 否 | 仅外部 http(s)，默认 null |
| `description` | string&#124;null | 否 | ≤255，默认 null |
| `sortOrder` | integer | 否 | int32，默认 0 |
| `status` | integer | 否 | 0 待审、1 展示、2 驳回/下架，默认 0 |

#### 请求示例

```http
POST /api/v1/admin/links
Authorization: Bearer <site-token>
Content-Type: application/json

{"linkName":"示例友站","url":"https://example.com","avatar":null,"description":null,"sortOrder":0,"status":0}
```

#### 响应参数

`data`：[Link](#response-link)。

#### 响应示例

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

### 10.5 修改或审核友链

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/links/{linkId}` |
| 接口描述 | 修改或审核友链 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:link:update` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

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

#### 请求示例

```http
PUT /api/v1/admin/links/1
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"linkName":"示例友站","url":"https://example.com","avatar":null,"description":null,"sortOrder":0,"status":0}
```

#### 响应参数

`data`：[Link](#response-link)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

<a id="api-75"></a>

### 10.6 删除友链

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/links/{linkId}` |
| 接口描述 | 删除友链 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:link:delete` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `linkId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |

#### 请求示例

```http
DELETE /api/v1/admin/links/1
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

物理删除。

---

<a id="api-76"></a>

### 10.7 查询公开站点配置

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/configs` |
| 接口描述 | 查询公开站点配置 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；文章须公开 |

#### 请求参数

无 Path、Query 或请求体参数；GET/无模型写操作不发送请求体。

#### 请求示例

```http
GET /api/v1/blog/configs
```

#### 响应参数

`data`：[Config](#response-config) 数组。

#### 响应示例

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

<a id="api-77"></a>

### 10.8 查询后台配置列表

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/configs` |
| 接口描述 | 查询后台配置列表 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:config:query` |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
GET /api/v1/admin/configs
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[Config](#response-config) 数组。

#### 响应示例

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

### 10.9 查询配置详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/configs/{configKey}` |
| 接口描述 | 查询配置详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:config:query` |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `configKey` | string | 是 | Path；仅 site/comment/message，未知键 404 |

#### 请求示例

```http
GET /api/v1/admin/configs/site
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[Config](#response-config)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

### 10.10 修改配置

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/configs/{configKey}` |
| 接口描述 | 修改配置 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:config:update` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `configKey` | string | 是 | Path；仅 site/comment/message，未知键 404 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `configValue` | object | 是 | site: title/description/icp/publicSecurityRecord/startDate；comment或message: enabled/reviewRequired；完整白名单对象，UTF-8 ≤16KiB |
| `description` | string&#124;null | 是，可 null | ≤255 字符 |
| `isPublic` | integer | 是 | site 为 0/1；comment/message 必须 0 |

#### 请求示例

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

#### 响应参数

`data`：[Config](#response-config)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

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

<a id="group-11"></a>

## 11. 文件与附件

支持图片上传、元数据查询、预览与删除；元数据不可编辑。文件访问由文章公开状态、实际引用及相应权限决定，见 [13.4](#file-consistency)。

<a id="api-80"></a>

### 11.1 分页查询文件元数据

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/files` |
| 接口描述 | 分页查询文件元数据 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:file:query` |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |
| `keyword` | string | 否 | 可选 S(100)，按本接口指定字段字面包含，不按 SQL 通配符解释 |
| `uploaderId` | ID | 否 | 可选，上传者筛选，不扩大 SELF |
| `mimeType` | string | 否 | 可选 ≤100，按已确认 MIME 类型筛选 |

#### 请求示例

```http
GET /api/v1/admin/files?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [File](#response-file)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

### 11.2 查询文件元数据

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/files/{fileId}` |
| 接口描述 | 查询文件元数据 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:file:query` |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fileId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/files/42
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[File](#response-file)。

#### 响应示例

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

### 11.3 上传图片

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/files` |
| 接口描述 | 上传图片 |
| Content-Type | multipart/form-data |
| 访问要求 | 后台：`blog:file:upload` |
| 成功状态 | 201 Created |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | binary | 是 | 唯一文件字段，单个 PNG/JPEG，非空且 ≤10MiB，不收路径/上传者 |

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

`data`：[File](#response-file)。

#### 响应示例

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

### 11.4 预览后台原文件

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/files/{fileId}/content` |
| 接口描述 | 预览后台原文件 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:file:query` |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fileId` | ID | 是 | Path；十进制正整数 ID 字符串 |

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

需要文件查询权限及其范围；前端 Bearer fetch 后使用临时 object URL，不把 Token 拼到 img URL。

---

<a id="api-84"></a>

### 11.5 删除文件

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/files/{fileId}` |
| 接口描述 | 删除文件 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:file:delete` |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fileId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
DELETE /api/v1/admin/files/42
Authorization: Bearer <site-token>
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

持文件锁检查所有文章引用，含草稿/私密/回收站；仍引用 409。存储删除失败保留元数据返回 503。

---

<a id="api-85"></a>

### 11.6 读取公开文章附件

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/articles/{articleId}/files/{fileId}/content` |
| 接口描述 | 读取公开文章附件 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；文章须公开 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `fileId` | ID | 是 | Path；十进制正整数 ID 字符串 |

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
| 访问要求 | 后台：`blog:article:query` + `blog:file:query` |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `fileId` | ID | 是 | Path；十进制正整数 ID 字符串 |

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

同时检查文章 query 与文件 query 及各自范围；文章须实际引用。文章作者身份不能代替跨用户文件授权。

---

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
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |

<a id="response-session"></a>

### 12.2 Session

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `tokenType` | string | 固定 Bearer |
| `accessToken` | string | 本站不透明 Token，仅成功登录返回 |
| `expiresIn` | integer | 剩余有效秒数 |
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
| `session.expiresIn` | integer | 剩余有效秒数 |
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
| `operationData` | object&#124;null | 白名单脱敏摘要；UTF-8≤16KiB，不是完整请求体 |

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
4. LOGIN 完成页读取并移除 ticket 后调用 exchange，得到本站 Session；BIND 弹窗以精确 origin 的 postMessage 把 ticket 交给原页面，原页面携带原 Token 兑换绑定结果。

来源值使用后端启用的平台码，例如 GITHUB；只列配置完整的平台。不得自定回调地址、以昵称/邮箱合并账号或转移他人绑定。保留至少一种可用登录方式；BIND 原页面/会话丢失后重新登录确认绑定结果。完整流程见 [OAuth 开发约定](API_CONTRACT.md#oauth-flow)。

### 13.2 文章可见性与删除

公开文章（PUBLIC）须同时满足：已发布 `status=1`、公开 `visibility=1`、未回收、首次发布时间非空且不晚于当前 UTC。所有前台列表、详情、搜索、互动、Feed、地图及附件文章上下文共用此条件，管理员 Token 不扩大前台可见范围。

隐藏分类不隐藏文章，文章的 category 返回 null；按隐藏/不存在分类或不存在标签筛选返回空页。公开列表所有条件 AND 组合；keyword 为标题、摘要与 Markdown 原文字面包含。前台 authorId 仍待实现，后台 authorId 已可用。

普通删除只移入回收站，保留正文、标签、互动和 slug；恢复为下架，不自动公开。永久删除的待实现设计见 [22.7](#api-159)。详情包含上下篇及最多 6 条同分类公开推荐，无须额外查询接口。

### 13.3 互动、订阅与统计

评论为纯文本，删除保留占位与回复链；有隐藏祖先的评论分支不公开。留言的悄悄话沿完整父链限制可见性，管理员通过后台读取；编辑重新待审，删除保留占位。相关规则见 [评论](API_CONTRACT.md#comment-rules) 与 [留言](API_CONTRACT.md#message-rules)。

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
{"code":"FORBIDDEN","msg":"无权执行该操作","data":null,"errors":[]}
```

<a id="delivery"></a>

接口实施状态以本文标记为准；部署配置和历史验收记录见 [后端运行说明](后端运行说明.md)。

---

<a id="group-15"></a>

## 15. 主页展示与在线简历

主页、博客侧栏与简历共用站点主人资料；公开接口仅返回展示字段。后台操作要求对应权限的 ALL 范围，不因站点主人身份自动赋权。

<a id="api-88"></a>

### 15.1 查询站点主人公开资料

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/home/profile` |
| 接口描述 | 查询站点主人公开资料 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

#### 请求参数

无业务参数。

#### 请求示例

```http
GET /api/v1/home/profile
```

#### 响应参数

`data`：[HomeProfilePublic](#response-homeprofilepublic)，未配置/无记录时可为 null。

#### 响应示例

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

<a id="api-89"></a>

### 15.2 查询后台主页资料

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/home/profile` |
| 接口描述 | 查询后台主页资料 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`home:profile:query`（ALL） |
| 并发校验 | 返回 X-Revision |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
GET /api/v1/admin/home/profile
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[HomeProfile](#response-homeprofile)，未配置/无记录时可为 null。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

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

### 15.3 设置主页资料

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/home/profile` |
| 接口描述 | 设置主页资料 |
| Content-Type | application/json |
| 访问要求 | 后台：`home:profile:update`（ALL） |
| 并发校验 | 必填 If-Revision |

#### 请求参数

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

#### 请求示例

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

#### 响应参数

`data`：[HomeProfile](#response-homeprofile)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

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

<a id="api-91"></a>

### 15.4 查询公开社交链接

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/home/social-links` |
| 接口描述 | 查询公开社交链接 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

#### 请求参数

无业务参数。

#### 请求示例

```http
GET /api/v1/home/social-links
```

#### 响应参数

`data`：[SocialPublic](#response-socialpublic) 数组。

#### 响应示例

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

<a id="api-92"></a>

### 15.5 查询后台社交链接

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/home/social-links` |
| 接口描述 | 查询后台社交链接 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`home:social:query`（ALL） |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |
| `keyword` | S(100) | 否 | 名称/标题字面包含，转义LIKE通配符 |
| `status` | integer | 否 | 可选0/1；缺失返回全部状态 |

#### 请求示例

```http
GET /api/v1/admin/home/social-links?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [Social](#response-social)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

### 15.6 查询社交链接详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/home/social-links/{socialId}` |
| 接口描述 | 查询社交链接详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`home:social:query`（ALL） |
| 并发校验 | 返回 X-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `socialId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/home/social-links/21
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[Social](#response-social)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

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

### 15.7 创建社交链接

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/home/social-links` |
| 接口描述 | 创建社交链接 |
| Content-Type | application/json |
| 访问要求 | 后台：`home:social:create`（ALL） |
| 成功状态 | 201 Created |
| 并发校验 | 返回 X-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | S(64) | 是 | 社交平台或链接名称 |
| `icon` | string&#124;null | 是，可null | ≤2048；外部HTTP(S)图标URL或前端注册图标键，禁止任意HTML |
| `url` | URL | 是 | 外部HTTP(S)目标链接，邮箱链接可使用经过校验的mailto:邮箱 |
| `sortOrder` | int32 | 是 | 越小越靠前 |
| `status` | integer | 是 | 0隐藏 1展示 |

#### 请求示例

```http
POST /api/v1/admin/home/social-links
Authorization: Bearer <site-token>
Content-Type: application/json

{"name":"GitHub","icon":"github","url":"https://github.com/example","sortOrder":0,"status":1}
```

#### 响应参数

`data`：[Social](#response-social)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

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

### 15.8 修改社交链接

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/home/social-links/{socialId}` |
| 接口描述 | 修改社交链接 |
| Content-Type | application/json |
| 访问要求 | 后台：`home:social:update`（ALL） |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `socialId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `name` | S(64) | 是 | 社交平台或链接名称 |
| `icon` | string&#124;null | 是，可null | ≤2048；外部HTTP(S)图标URL或前端注册图标键，禁止任意HTML |
| `url` | URL | 是 | 外部HTTP(S)目标链接，邮箱链接可使用经过校验的mailto:邮箱 |
| `sortOrder` | int32 | 是 | 越小越靠前 |
| `status` | integer | 是 | 0隐藏 1展示 |

#### 请求示例

```http
PUT /api/v1/admin/home/social-links/21
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"name":"GitHub","icon":"github","url":"https://github.com/example","sortOrder":0,"status":1}
```

#### 响应参数

`data`：[Social](#response-social)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

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

<a id="api-96"></a>

### 15.9 删除社交链接

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/home/social-links/{socialId}` |
| 接口描述 | 删除社交链接 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`home:social:delete`（ALL） |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `socialId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |

#### 请求示例

```http
DELETE /api/v1/admin/home/social-links/21
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

物理删除。

---

<a id="api-97"></a>

### 15.10 查询公开简历经历

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/cv/experiences` |
| 接口描述 | 查询公开简历经历 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |
| `experienceType` | integer | 否 | 0教育 1工作/实习 2项目 |

#### 请求示例

```http
GET /api/v1/cv/experiences?page=1&pageSize=10
```

#### 响应参数

`data`：分页对象，`records[]` 为 [ExperiencePublic](#response-experiencepublic)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

<a id="api-98"></a>

### 15.11 查询后台简历经历

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/cv/experiences` |
| 接口描述 | 查询后台简历经历 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`cv:experience:query`（ALL） |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |
| `experienceType` | integer | 否 | 0/1/2 |
| `keyword` | S(100) | 否 | 名称/标题字面包含，转义LIKE通配符 |
| `status` | integer | 否 | 可选0/1；缺失返回全部状态 |

#### 请求示例

```http
GET /api/v1/admin/cv/experiences?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [Experience](#response-experience)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

### 15.12 查询简历经历详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/cv/experiences/{experienceId}` |
| 接口描述 | 查询简历经历详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`cv:experience:query`（ALL） |
| 并发校验 | 返回 X-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `experienceId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/cv/experiences/31
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[Experience](#response-experience)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

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

### 15.13 创建简历经历

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/cv/experiences` |
| 接口描述 | 创建简历经历 |
| Content-Type | application/json |
| 访问要求 | 后台：`cv:experience:create`（ALL） |
| 成功状态 | 201 Created |
| 并发校验 | 返回 X-Revision |

#### 请求参数

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

#### 请求示例

```http
POST /api/v1/admin/cv/experiences
Authorization: Bearer <site-token>
Content-Type: application/json

{
  "experienceType": 0, "title": "示例大学", "subtitle": "计算机科学", "logoUrl": null, "content": "学习与项目实践",
  "startDate": "2022-09-01", "endDate": null, "sortOrder": 0, "status": 1
}
```

#### 响应参数

`data`：[Experience](#response-experience)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

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

### 15.14 修改简历经历

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/cv/experiences/{experienceId}` |
| 接口描述 | 修改简历经历 |
| Content-Type | application/json |
| 访问要求 | 后台：`cv:experience:update`（ALL） |
| 并发校验 | 必填 If-Revision |

#### 请求参数

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

#### 请求示例

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

#### 响应参数

`data`：[Experience](#response-experience)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

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

<a id="api-102"></a>

### 15.15 删除简历经历

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/cv/experiences/{experienceId}` |
| 接口描述 | 删除简历经历 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`cv:experience:delete`（ALL） |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `experienceId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |

#### 请求示例

```http
DELETE /api/v1/admin/cv/experiences/31
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

物理删除，不新增简历回收站。

---

<a id="api-103"></a>

### 15.16 查询公开简历技能

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/cv/skills` |
| 接口描述 | 查询公开简历技能 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |

#### 请求示例

```http
GET /api/v1/cv/skills?page=1&pageSize=10
```

#### 响应参数

`data`：分页对象，`records[]` 为 [SkillPublic](#response-skillpublic)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

<a id="api-104"></a>

### 15.17 查询后台简历技能

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/cv/skills` |
| 接口描述 | 查询后台简历技能 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`cv:skill:query`（ALL） |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |
| `keyword` | S(100) | 否 | 名称/标题字面包含，转义LIKE通配符 |
| `status` | integer | 否 | 可选0/1；缺失返回全部状态 |

#### 请求示例

```http
GET /api/v1/admin/cv/skills?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [Skill](#response-skill)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

### 15.18 查询技能详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/cv/skills/{skillId}` |
| 接口描述 | 查询技能详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`cv:skill:query`（ALL） |
| 并发校验 | 返回 X-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `skillId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/cv/skills/41
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[Skill](#response-skill)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

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

### 15.19 创建简历技能

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/cv/skills` |
| 接口描述 | 创建简历技能 |
| Content-Type | application/json |
| 访问要求 | 后台：`cv:skill:create`（ALL） |
| 成功状态 | 201 Created |
| 并发校验 | 返回 X-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | S(64) | 是 | 技能名称 |
| `description` | S(500)? | 是，可null | 技能描述 |
| `icon` | string&#124;null | 是，可null | ≤2048；外部HTTP(S)URL或注册图标键 |
| `sortOrder` | int32 | 是 | 越小越靠前 |
| `status` | integer | 是 | 0隐藏 1展示 |

#### 请求示例

```http
POST /api/v1/admin/cv/skills
Authorization: Bearer <site-token>
Content-Type: application/json

{"name":"Java","description":"后端开发","icon":null,"sortOrder":0,"status":1}
```

#### 响应参数

`data`：[Skill](#response-skill)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

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

### 15.20 修改简历技能

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/cv/skills/{skillId}` |
| 接口描述 | 修改简历技能 |
| Content-Type | application/json |
| 访问要求 | 后台：`cv:skill:update`（ALL） |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `skillId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `name` | S(64) | 是 | 技能名称 |
| `description` | S(500)? | 是，可null | 技能描述 |
| `icon` | string&#124;null | 是，可null | ≤2048；外部HTTP(S)URL或注册图标键 |
| `sortOrder` | int32 | 是 | 越小越靠前 |
| `status` | integer | 是 | 0隐藏 1展示 |

#### 请求示例

```http
PUT /api/v1/admin/cv/skills/41
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"name":"Java","description":"后端开发","icon":null,"sortOrder":0,"status":1}
```

#### 响应参数

`data`：[Skill](#response-skill)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

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

<a id="api-108"></a>

### 15.21 删除简历技能

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/cv/skills/{skillId}` |
| 接口描述 | 删除简历技能 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`cv:skill:delete`（ALL） |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `skillId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |

#### 请求示例

```http
DELETE /api/v1/admin/cv/skills/41
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

物理删除。

---

<a id="group-16"></a>

## 16. 留言板、本人留言与审核

留言与文章评论独立；按 parentId 逐层分页，不一次展开整棵线程。本人接口只取会话身份；私密父链、审核与占位规则见 [13.3](#business)。

<a id="api-109"></a>

### 16.1 分页读取可见留言或回复

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/messages` |
| 接口描述 | 分页读取可见留言或回复 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；可选Bearer，携带时必须校验有效会话和账号状态 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..50 |
| `parentId` | ID | 否 | 缺失仅根节点；提供时仅该节点的直接回复 |

#### 请求示例

```http
GET /api/v1/blog/messages?page=1&pageSize=10
```

#### 响应参数

`data`：分页对象，`records[]` 为 [MessagePublic](#response-messagepublic)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

### 16.2 发表或回复留言

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/messages` |
| 接口描述 | 发表或回复留言 |
| Content-Type | application/json |
| 访问要求 | 登录 + `blog:message:create`；始终限制本人归属 |
| 成功状态 | 201 Created |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `parentId` | ID&#124;null | 是，可null | 直接回复目标；根留言为null，回复创建后不可改 |
| `content` | S(2000) | 是 | 去首尾空白后非空；Markdown保留原文内部空白 |
| `contentFormat` | integer | 是 | 0纯文本 1Markdown |
| `isSecret` | integer | 是 | 0公开 1悄悄话；私密回复必须继承完整父链限制 |
| `notifyReply` | integer | 是 | 0/1；见留言通知边界 |

#### 请求示例

```http
POST /api/v1/blog/messages
Authorization: Bearer <site-token>
Content-Type: application/json

{"parentId":null,"content":"你好，欢迎交流。","contentFormat":0,"isSecret":0,"notifyReply":0}
```

#### 响应参数

`data`：[MessagePrivate](#response-messageprivate)。

#### 响应示例

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

<a id="api-111"></a>

### 16.3 分页查询本人留言

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/me/messages` |
| 接口描述 | 分页查询本人留言 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |
| `status` | integer | 否 | 0/1/2/3 |

#### 请求示例

```http
GET /api/v1/me/messages?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [MessagePrivate](#response-messageprivate)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

### 16.4 查询本人留言详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/me/messages/{messageId}` |
| 接口描述 | 查询本人留言详情 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |
| 并发校验 | 返回 X-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `messageId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/me/messages/301
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[MessagePrivate](#response-messageprivate)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

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

### 16.5 编辑本人留言

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/me/messages/{messageId}` |
| 接口描述 | 编辑本人留言 |
| Content-Type | application/json |
| 访问要求 | 登录 + `blog:message:updateOwn`；始终限制本人归属 |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `messageId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `content` | S(2000) | 是 | 去首尾空白后非空；Markdown保留原文内部空白 |
| `contentFormat` | integer | 是 | 0纯文本 1Markdown |
| `notifyReply` | integer | 是 | 0/1；见留言通知边界 |

#### 请求示例

```http
PUT /api/v1/me/messages/301
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"content":"你好，欢迎交流。","contentFormat":0,"notifyReply":0}
```

#### 响应参数

`data`：[MessagePrivate](#response-messageprivate)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

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

### 16.6 删除本人留言

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/me/messages/{messageId}` |
| 接口描述 | 删除本人留言 |
| Content-Type | 无请求体 |
| 访问要求 | 登录 + `blog:message:deleteOwn`；始终限制本人归属 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `messageId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
DELETE /api/v1/me/messages/301
Authorization: Bearer <site-token>
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

无If-Revision，与本人评论删除一致；核对归属后清空原文为该留言已删除、contentFormat=0、notifyReply=0、status=3，保留回复链和私密范围。重复删除幂等，他人404。

---

<a id="api-115"></a>

### 16.7 分页查询后台留言

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/messages` |
| 接口描述 | 分页查询后台留言 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:message:query`（ALL） |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |
| `status` | integer | 否 | 0/1/2/3 |
| `userId` | ID | 否 | 作者筛选 |
| `isSecret` | integer | 否 | 0/1 |
| `begin` | Time | 否 | createTime含下界 |
| `end` | Time | 否 | createTime不含上界 |

#### 请求示例

```http
GET /api/v1/admin/messages?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [MessagePrivate](#response-messageprivate)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

### 16.8 查询后台留言详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/messages/{messageId}` |
| 接口描述 | 查询后台留言详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:message:query`（ALL） |
| 并发校验 | 返回 X-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `messageId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/messages/301
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[MessagePrivate](#response-messageprivate)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

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

<a id="api-117"></a>

### 16.9 审核留言

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/messages/{messageId}/review` |
| 接口描述 | 审核留言 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:message:review`（ALL） |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `messageId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `status` | integer | 是 | 1通过 2驳回 |

#### 请求示例

```http
PUT /api/v1/admin/messages/301/review
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"status":1}
```

#### 响应参数

`data`：[MessagePrivate](#response-messageprivate)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

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

先检查ALL与版本，删除占位不可审核。父链待审或驳回时不允许通过子节点；重复目标幂等。通过审核不会公开悄悄话。通知仅在实际转为通过且事务提交后触发。

---

<a id="api-118"></a>

### 16.10 后台删除留言

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/messages/{messageId}` |
| 接口描述 | 后台删除留言 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:message:moderate`（ALL） |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `messageId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
DELETE /api/v1/admin/messages/301
Authorization: Bearer <site-token>
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

与本人删除同样占位并清除原文；不物理删除整棵线程、不改挂回复。重复删除幂等；需要ALL。

---

<a id="api-119"></a>

### 16.11 后台回复留言

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/messages/{messageId}/replies` |
| 接口描述 | 后台回复留言 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:message:review`（ALL） |
| 成功状态 | 201 Created |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `messageId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `content` | S(2000) | 是 | 去首尾空白后非空；Markdown保留原文内部空白 |
| `contentFormat` | integer | 是 | 0纯文本 1Markdown |
| `notifyReply` | integer | 是 | 0/1；见留言通知边界 |

#### 请求示例

```http
POST /api/v1/admin/messages/301/replies
Authorization: Bearer <site-token>
Content-Type: application/json

{"content":"你好，欢迎交流。","contentFormat":0,"notifyReply":0}
```

#### 响应参数

`data`：[MessagePrivate](#response-messageprivate)。

#### 响应示例

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

<a id="group-17"></a>

## 17. 背景音乐与播放列表

维护站点曲目元数据；音频、封面、歌词使用外部 HTTP(S) URL。现有图片上传接口不支持音频或歌词上传，也不提供播放资源代理。

<a id="api-120"></a>

### 17.1 分页查询公开播放列表

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/music` |
| 接口描述 | 分页查询公开播放列表 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |

#### 请求示例

```http
GET /api/v1/blog/music?page=1&pageSize=10
```

#### 响应参数

`data`：分页对象，`records[]` 为 [MusicPublic](#response-musicpublic)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

<a id="api-121"></a>

### 17.2 分页查询后台音乐

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/music` |
| 接口描述 | 分页查询后台音乐 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:music:query`（ALL） |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |
| `keyword` | S(100) | 否 | 标题或作者字面包含 |
| `status` | integer | 否 | 0/1，可选 |

#### 请求示例

```http
GET /api/v1/admin/music?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [Music](#response-music)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

### 17.3 查询音乐详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/music/{musicId}` |
| 接口描述 | 查询音乐详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:music:query`（ALL） |
| 并发校验 | 返回 X-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `musicId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/music/51
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[Music](#response-music)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

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

### 17.4 创建音乐

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/music` |
| 接口描述 | 创建音乐 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:music:create`（ALL） |
| 成功状态 | 201 Created |
| 并发校验 | 返回 X-Revision |

#### 请求参数

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

#### 请求示例

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

#### 响应参数

`data`：[Music](#response-music)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

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

### 17.5 修改音乐

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/music/{musicId}` |
| 接口描述 | 修改音乐 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:music:update`（ALL） |
| 并发校验 | 必填 If-Revision |

#### 请求参数

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

#### 请求示例

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

#### 响应参数

`data`：[Music](#response-music)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

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

<a id="api-125"></a>

### 17.6 删除音乐

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/music/{musicId}` |
| 接口描述 | 删除音乐 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:music:delete`（ALL） |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `musicId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |

#### 请求示例

```http
DELETE /api/v1/admin/music/51
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

仅删除数据库曲目元数据，不删除外部音频、封面或歌词。

---

<a id="group-18"></a>

## 18. 邮件订阅、RSS Feed 与站点地图

邮件订阅需登录并确认订阅邮箱；RSS 与站点地图公开读取，不要求订阅邮箱。订阅邮箱不自动成为账号登录邮箱。

<a id="api-126"></a>

### 18.1 查询本人邮件订阅

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/me/rss-subscription` |
| 接口描述 | 查询本人邮件订阅 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
GET /api/v1/me/rss-subscription
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[Subscription](#response-subscription)，未配置/无记录时可为 null。

#### 响应示例

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

### 18.2 申请或修改本人邮件订阅

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/me/rss-subscription` |
| 接口描述 | 申请或修改本人邮件订阅 |
| Content-Type | application/json |
| 访问要求 | 登录 + `blog:rss:subscribe`；始终限制本人归属 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `email` | string | 是 | 合法邮箱≤254；trim并转小写 |

#### 请求示例

```http
PUT /api/v1/me/rss-subscription
Authorization: Bearer <site-token>
Content-Type: application/json

{"email":"leaf@example.test"}
```

#### 响应参数

`data`：[Subscription](#response-subscription)。

#### 响应示例

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

首次或换邮箱写status=0、verifiedAt/subscribeTime/unsubscribeTime=null，旧邮箱推送立即停止；同一已激活邮箱幂等，不重复发信。待验证邮箱重试会重发确认邮件，限流返回429。确认票据24小时有效，绑定用户、subscriptionId、当前email及本次申请版本。SMTP/Redis失败503，已提交待验证记录可保留，通过重发恢复；不返回已激活成功。账号与邮箱唯一冲突409，不挪用其他用户订阅。

---

<a id="api-128"></a>

### 18.3 确认本人订阅邮箱

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/me/rss-subscription/confirm` |
| 接口描述 | 确认本人订阅邮箱 |
| Content-Type | application/json |
| 访问要求 | 登录 + `blog:rss:subscribe`；始终限制本人归属 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ticket` | S(512) | 是 | 从确认邮件完成页读取的不透明票据；不得放日志 |

#### 请求示例

```http
POST /api/v1/me/rss-subscription/confirm
Authorization: Bearer <site-token>
Content-Type: application/json

{"ticket":"confirmation-ticket-example"}
```

#### 响应参数

`data`：[Subscription](#response-subscription)。

#### 响应示例

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

需已登录的同一本站用户；可在另一个浏览器登录同一账号确认，不依赖旧访客存储。锁用户及订阅，验证当前pending状态、邮箱和申请版本，Redis原子消费后设置verifiedAt、subscribeTime、status=1。过期/复用/错账号或已被新申请替换统一400/SUBSCRIPTION_TICKET_INVALID。数据库失败回滚，已消费票据需重新申请；不把Redis与MySQL描述为共同事务。

---

<a id="api-129"></a>

### 18.4 取消本人邮件订阅

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/me/rss-subscription` |
| 接口描述 | 取消本人邮件订阅 |
| Content-Type | 无请求体 |
| 访问要求 | 登录 + `blog:rss:unsubscribe`；始终限制本人归属 |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
DELETE /api/v1/me/rss-subscription
Authorization: Bearer <site-token>
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

身份只取会话，无If-Revision。将本人记录置status=2并写unsubscribeTime；不存在或已退订幂等200。保留已验证邮箱信息，重订需走申请及确认。后台ALL不扩大到他人。

---

<a id="api-130"></a>

### 18.5 通过邮件链接退订

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/rss-subscriptions/unsubscribe` |
| 接口描述 | 通过邮件链接退订 |
| Content-Type | application/json |
| 访问要求 | 公开 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ticket` | S(512) | 是 | 仅限本订阅退订的不可猜票据 |

#### 请求示例

```http
POST /api/v1/blog/rss-subscriptions/unsubscribe
Content-Type: application/json

{"ticket":"unsubscribe-ticket-example"}
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

这是显式公开的票据能力接口；不需要Bearer，也不颁发会话。票据存Redis，有效30天，绑定subscriptionId、email、verifiedAt和本轮subscribeTime；只能将该订阅改为退订。相同有效票据重复退订200，邮箱或订阅轮次变化后400/SUBSCRIPTION_TICKET_INVALID。邮件链接指向固定前端完成页#unsubscribeTicket=...，页面清除fragment并由用户确认后POST；邮件扫描器GET不改变状态。不接受email/userId作为替代凭据。

---

<a id="api-131"></a>

### 18.6 分页查询后台邮件订阅

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/rss-subscriptions` |
| 接口描述 | 分页查询后台邮件订阅 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:rss:query`（ALL） |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |
| `email` | S(254) | 否 | 邮箱字面包含 |
| `userId` | ID | 否 | 所属用户 |
| `status` | integer | 否 | 0待验证 1已订阅 2已退订 |

#### 请求示例

```http
GET /api/v1/admin/rss-subscriptions?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [SubscriptionAdmin](#response-subscriptionadmin)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

### 18.7 查询邮件订阅详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/rss-subscriptions/{subscriptionId}` |
| 接口描述 | 查询邮件订阅详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:rss:query`（ALL） |
| 并发校验 | 返回 X-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `subscriptionId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/rss-subscriptions/401
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[SubscriptionAdmin](#response-subscriptionadmin)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

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

<a id="api-133"></a>

### 18.8 管理邮件订阅状态

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/rss-subscriptions/{subscriptionId}/status` |
| 接口描述 | 管理邮件订阅状态 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:rss:update`（ALL） |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `subscriptionId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `status` | integer | 是 | 1激活 2退订 |

#### 请求示例

```http
PUT /api/v1/admin/rss-subscriptions/401/status
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"status":2}
```

#### 响应参数

`data`：[SubscriptionAdmin](#response-subscriptionadmin)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

```http
X-Revision: "<current-revision>"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "userId": "12", "subscriptionId": "401", "email": "leaf@example.test", "status": 2, "verifiedAt": null,
    "subscribeTime": null
  },
  "errors": []
}
```

只更新状态，不允许写userId/email/verifiedAt。激活仅允许当前邮箱已经验证且所属用户启用；待验证状态不能被管理员绕过，409/STATE_CONFLICT。激活写subscribeTime并清空unsubscribeTime；退订写unsubscribeTime。重新激活会更新订阅轮次并使旧退订票据失效；退订保留当前轮次，支持相同票据幂等退订。

---

<a id="api-134"></a>

### 18.9 删除邮件订阅

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/rss-subscriptions/{subscriptionId}` |
| 接口描述 | 删除邮件订阅 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:rss:delete`（ALL） |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `subscriptionId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |

#### 请求示例

```http
DELETE /api/v1/admin/rss-subscriptions/401
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

需ALL；物理删除，旧确认/退订票据因目标不存在失效，不自动转移邮箱归属。

---

<a id="api-135"></a>

### 18.10 读取公开RSS Feed

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/rss.xml` |
| 接口描述 | 读取公开RSS Feed |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

#### 请求参数

无业务参数。

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

成功Content-Type=application/rss+xml; charset=UTF-8，返回RSS2.0而非Result。取最近20篇PUBLIC文章，publishTime DESC,articleId DESC；guid使用稳定本站文章URL，link使用规范slug，pubDate为RFC822日期。description输出转义后的摘要，不输出Markdown、私密正文或邮箱。配置的公开博客基址由app.sites.blog-base-url提供，文章前端规范路径为/articles/{slug}，不能信任任意Host头。

---

<a id="api-136"></a>

### 18.11 读取公开站点地图

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/sitemap.xml` |
| 接口描述 | 读取公开站点地图 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 可选，正整数；仅供sitemapindex的子图链接使用 |

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

成功application/xml; charset=UTF-8，不包Result。只输出站点公开入口、PUBLIC文章及可公开分类/标签URL；使用安全XML编码，lastmod取公开资源更新时间，UTC格式。超过单文件50000条或未压缩50MiB时返回sitemapindex；同一路径支持page正整数查询读取有界子图，未知页404。不得悄悄截断已发布URL。子图链接使用app.sites.api-base-url（含/api/v1）加/blog/sitemap.xml?page=...；公开入口使用已配置home/blog/cv基址，分类/标签前端路径为/categories/{slug}与/tags/{slug}。任何缓存均需随发布、下架、回收、可见性或slug变化失效，不能继续暴露非PUBLIC条目。

---

<a id="group-19"></a>

## 19. 访客、浏览记录与统计报表

统计标识与本站账号分开。封禁统计标识只限制采集，不等于禁用账号。页面 PV/UV 与文章累计阅读数口径不同，日期按 UTC。

<a id="api-137"></a>

### 19.1 记录站点页面浏览

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/analytics/page-views` |
| 接口描述 | 记录站点页面浏览 |
| Content-Type | application/json |
| 访问要求 | 公开；可选Bearer，携带时必须校验有效会话和账号状态 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fingerprint` | string | 是 | ASCII字母数字或连字符16..150；浏览器随机统计标识，可伪造，不能作为身份 |
| `siteCode` | string | 是 | home/blog/cv；服务端与明确配置的来源站点核验 |
| `pagePath` | S(2048) | 是 | 规范站内路径，必须以/起始，剔除fragment及全部query |
| `pageTitle` | S(200)? | 是，可null | 展示标题，不作为鉴权或资源定位依据 |
| `referer` | URL? | 是，可null | 来源URL；服务端再次脱敏并去掉query/fragment |
| `articleId` | ID&#124;null | 是，可null | 仅blog文章详情可传；需PUBLIC且与pagePath的规范slug匹配 |

#### 请求示例

```http
POST /api/v1/analytics/page-views
Content-Type: application/json

{
  "fingerprint": "d681988a-cbd0-4ef6-bdec-f84bfa234e16", "siteCode": "home", "pagePath": "/",
  "pageTitle": "DriftingLeaves", "referer": null, "articleId": null
}
```

#### 响应参数

`data`：[TrackingResult](#response-trackingresult)。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":{"recorded":true},"errors":[]}
```

IP/UA、viewTime和userId由服务端读取；不接受visitorId、用户ID、会话ID或Token字段。携带Bearer时必须有效，匿名不创建sys_user。事务写访客首次/最近时间及一条page_view后返回recorded=true；失败503不伪造已记录。该POST不增加blog_article.view_count，文章成功详情读取仍按7.2计一次，避免双计。受限统计标识返回403/VISITOR_BLOCKED，只影响此采集入口；可伪造标识不能用于阻止公开阅读。重复POST可能产生多条浏览，无通用幂等键。

---

<a id="api-138"></a>

### 19.2 分页查询访客

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/visitors` |
| 接口描述 | 分页查询访客 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:visitor:query`（ALL） |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |
| `country` | S(64) | 否 | 精确匹配 |
| `province` | S(64) | 否 | 精确匹配 |
| `city` | S(64) | 否 | 精确匹配 |
| `isBlocked` | integer | 否 | 可选0/1，对持久化标记筛选 |

#### 请求示例

```http
GET /api/v1/admin/visitors?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [Visitor](#response-visitor)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

### 19.3 查询访客详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/visitors/{visitorId}` |
| 接口描述 | 查询访客详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:visitor:query`（ALL） |
| 并发校验 | 返回 X-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `visitorId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/visitors/501
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[Visitor](#response-visitor)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

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

<a id="api-140"></a>

### 19.4 封禁访客统计标识

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/visitors/{visitorId}/block` |
| 接口描述 | 封禁访客统计标识 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:visitor:block`（ALL） |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `visitorId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `blockedUntil` | Time&#124;null | 是，可null | 必须显式出现；null永久，否则晚于当前UTC时间 |

#### 请求示例

```http
PUT /api/v1/admin/visitors/501/block
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"blockedUntil":"2026-10-31T02:00:00.000Z"}
```

#### 响应参数

`data`：[Visitor](#response-visitor)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

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

需ALL，置isBlocked=1；到期后采集入口按时间自动视为未封禁，不要求定时任务先更新数据库。本人账号状态不受影响。

---

<a id="api-141"></a>

### 19.5 解除访客统计限制

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/visitors/{visitorId}/unblock` |
| 接口描述 | 解除访客统计限制 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:visitor:unblock`（ALL） |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `visitorId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |

#### 请求示例

```http
PUT /api/v1/admin/visitors/501/unblock
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

`data`：[Visitor](#response-visitor)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| X-Revision（Header） | string | 当前业务版本；写接口原样携带 |

#### 响应示例

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

置isBlocked=0、blockedUntil=null；重复目标幂等。仅影响此标识的采集限制。

---

<a id="api-142"></a>

### 19.6 删除访客统计记录

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/visitors/{visitorId}` |
| 接口描述 | 删除访客统计记录 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:visitor:delete`（ALL） |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `visitorId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |

#### 请求示例

```http
DELETE /api/v1/admin/visitors/501
Authorization: Bearer <site-token>
If-Revision: "<revision-from-detail>"
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

物理删除；对应page_view.visitor_id由外键置NULL，保留PV明细，UV可能下降。不能使用此接口删除用户、留言、评论或订阅。

---

<a id="api-143"></a>

### 19.7 分页查询浏览记录

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/page-views` |
| 接口描述 | 分页查询浏览记录 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:view:query`（ALL） |

#### 请求参数

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

#### 请求示例

```http
GET /api/v1/admin/page-views?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [PageView](#response-pageview)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

<a id="api-144"></a>

### 19.8 删除浏览记录

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/page-views/{viewId}` |
| 接口描述 | 删除浏览记录 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:view:delete`（ALL） |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `viewId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
DELETE /api/v1/admin/page-views/601
Authorization: Bearer <site-token>
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

明细不可编辑，不要求If-Revision；需ALL，物理删除，不存在404。会改变基于保留明细的报表，不扣减文章累计view_count。

---

<a id="api-145"></a>

### 19.9 查询公开博客统计

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/statistics` |
| 接口描述 | 查询公开博客统计 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

#### 请求参数

无业务参数。

#### 请求示例

```http
GET /api/v1/blog/statistics
```

#### 响应参数

`data`：[PublicStatistics](#response-publicstatistics)。

#### 响应示例

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

仅公开汇总，不接受任意userId或日期探索后台数据；当前未公开文章/分类及待审、私密互动不进入内容汇总。数据随源表变化更新，不输出IP、邮箱或UA。

---

<a id="api-146"></a>

### 19.10 查询后台统计总览

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/statistics/overview` |
| 接口描述 | 查询后台统计总览 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:statistics:query`（ALL） |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `begin` | date | 否 | 可选；与end同时出现；UTC日期含下界 |
| `end` | date | 否 | 可选；UTC日期含当日，最多367天，end不得早于begin |
| `siteCode` | string | 否 | 可选home/blog/cv，缺失统计全部站点 |

#### 请求示例

```http
GET /api/v1/admin/statistics/overview
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[AdminStatistics](#response-adminstatistics)。

#### 响应示例

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

### 19.11 查询浏览趋势

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/statistics/page-views` |
| 接口描述 | 查询浏览趋势 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:statistics:query`（ALL） |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `begin` | date | 否 | 可选；与end同时出现；UTC日期含下界 |
| `end` | date | 否 | 可选；UTC日期含当日，最多367天，end不得早于begin |
| `siteCode` | string | 否 | 可选home/blog/cv，缺失统计全部站点 |

#### 请求示例

```http
GET /api/v1/admin/statistics/page-views
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[ViewTrend](#response-viewtrend) 数组。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":[{"date":"2026-10-30","pageViewCount":"8","visitorCount":"5"}],"errors":[]}
```

需ALL；按UTC日期ASC补全每一天，无数据为字符串0。begin/end默认UTC今天；站点筛选不改变日边界。

---

<a id="api-148"></a>

### 19.12 查询新访客趋势

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/statistics/visitors` |
| 接口描述 | 查询新访客趋势 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:statistics:query`（ALL） |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `begin` | date | 否 | 可选；与end同时出现；UTC日期含下界 |
| `end` | date | 否 | 可选；UTC日期含当日，最多367天，end不得早于begin |
| `siteCode` | string | 否 | 可选home/blog/cv，缺失统计全部站点 |

#### 请求示例

```http
GET /api/v1/admin/statistics/visitors
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[VisitorTrend](#response-visitortrend) 数组。

#### 响应示例

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

### 19.13 查询访客省份分布

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/statistics/provinces` |
| 接口描述 | 查询访客省份分布 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:statistics:query`（ALL） |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `begin` | date | 否 | 可选；与end同时出现；UTC日期含下界 |
| `end` | date | 否 | 可选；UTC日期含当日，最多367天，end不得早于begin |
| `siteCode` | string | 否 | 可选home/blog/cv，缺失统计全部站点 |

#### 请求示例

```http
GET /api/v1/admin/statistics/provinces
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[ProvinceMetric](#response-provincemetric) 数组。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":[{"province":null,"visitorCount":"3"}],"errors":[]}
```

需ALL；以区间内出现过的visitorId去重，按访客当前province分组，未知为null，不按每条PV重复计访客。visitorCount DESC,province ASC；不是地理位置历史快照。

---

<a id="api-150"></a>

### 19.14 查询文章访问排行

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/statistics/articles` |
| 接口描述 | 查询文章访问排行 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:statistics:query`（ALL） |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `begin` | date | 否 | 可选；与end同时出现；UTC日期含下界 |
| `end` | date | 否 | 可选；UTC日期含当日，最多367天，end不得早于begin |
| `limit` | integer | 否 | 默认10，1..100 |

#### 请求示例

```http
GET /api/v1/admin/statistics/articles
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[ArticleViewMetric](#response-articleviewmetric) 数组。

#### 响应示例

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

<a id="group-20"></a>

## 20. 操作日志

查询与清理日志均需对应权限的 ALL 范围。清理操作自身会写入审计，不以 admin 角色名绕过授权。

<a id="api-151"></a>

### 20.1 分页查询操作日志

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/operation-logs` |
| 接口描述 | 分页查询操作日志 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`sys:log:query`（ALL） |

#### 请求参数

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

#### 请求示例

```http
GET /api/v1/admin/operation-logs?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [OperationLog](#response-operationlog)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

筛选AND组合；begin<end；operationTime DESC,logId DESC。列表不展开operationData，详情才展示脱敏摘要。

---

<a id="api-152"></a>

### 20.2 查询操作日志详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/operation-logs/{logId}` |
| 接口描述 | 查询操作日志详情 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`sys:log:query`（ALL） |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `logId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/operation-logs/701
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：[OperationLogDetail](#response-operationlogdetail)。

#### 响应示例

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

<a id="api-153"></a>

### 20.3 清理单条操作日志

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/operation-logs/{logId}` |
| 接口描述 | 清理单条操作日志 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`sys:log:delete`（ALL） |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `logId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
DELETE /api/v1/admin/operation-logs/701
Authorization: Bearer <site-token>
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

不可编辑记录，无If-Revision；物理删除，目标不存在404。清理行为本身写一条新的审计记录，不形成递归记录。不存在公开创建/修改日志接口。

---

<a id="legacy-sources"></a>

## 21. 旧项目来源与迁移对照

V2 沿用旧站的主页、简历、文章、留言、音乐、订阅与访问统计业务，并采用当前 Sa-Token、RBAC 和 28 表设计。旧站 JWT、游客 Token、批量操作、私密评论与前端完成页均不自动成为 V2 能力。

具体来源和迁移取舍见 [迁移对照](API_CONTRACT.md#legacy-sources)；运行配置与历史验证见 [后端运行说明](后端运行说明.md)。

---

<a id="group-22"></a>

## 22. 文章发现与内容管理补充设计（待实现）

**本章待实现**：6 个新路由与 1 项前台作者筛选扩展。以下示例是设计，不代表当前可调用；已实现的文章用法见 [7.12](#article-scenarios)。本章 GET 不增加阅读量，公开条件与既有接口相同。

<a id="article-author-query"></a>

### 22.1 前台按作者查询公开文章（扩展既有接口）

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/articles`，新增可选 `authorId` |
| 接口描述 | 作者主页分页读取该作者的公开文章，并可与分类、标签、关键词和月份组合 |
| Content-Type | 无请求体 |
| 访问要求 | 公开，只查询 PUBLIC 文章 |
| 实施状态 | **待实现参数扩展**；当前传 authorId 返回 400，不新增路由计数 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `authorId` | ID | 否 | 作者 userId，省略则不按作者过滤；仅筛选，不作为操作者身份 |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |
| `categoryId` | ID | 否 | 与作者条件同时满足；隐藏/不存在分类返回空页 |
| `tagId` | ID | 否 | 与作者条件同时满足 |
| `keyword` | S(100) | 否 | 标题、摘要、Markdown 原文字面包含 |
| `month` | string | 否 | 合法 YYYY-MM，年份 1000..9999；UTC 月份 |

#### 请求示例

```http
GET /api/v1/blog/articles?authorId=12&categoryId=1&tagId=1&page=1&pageSize=10
```

#### 响应参数

`data` 仍为 [7.1 的 Page<ArticlePublic>](#api-37)，完整字段、可空性均不变；total 与 records 必须使用同一作者及组合条件。排序仍为 `isTop DESC,publishTime DESC,articleId DESC`。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":{"page":1,"pageSize":10,"total":"0","totalPages":"0","records":[]},"errors":[]}
```

作者 ID 可取自现有文章的 `author.userId`，也可取自下文作者列表/详情。合法但不存在或没有 PUBLIC 文章的作者返回空页；不是用户详情接口，不泄露草稿数、私密文章数或账号存在性。格式无效的 ID 返回 400。作者文章页可先查询作者详情展示简介，再分页查询文章；不能调用后台用户详情作为公开资料来源。

---

<a id="api-154"></a>

### 22.2 分页查询公开作者

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/authors` |
| 接口描述 | 作者发现页及作者筛选器，只列出至少有一篇 PUBLIC 文章的作者 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |
| 实施状态 | 待实现 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |

#### 请求示例

```http
GET /api/v1/blog/authors?page=1&pageSize=10
```

#### 响应参数

`data`：分页对象，`records[]` 为 [AuthorPublic](#response-authorpublic)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

### 22.3 查询作者公开主页资料

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/authors/{userId}` |
| 接口描述 | 公开作者主页的资料区与公开文章数 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；目标须至少有一篇 PUBLIC 文章 |
| 实施状态 | 待实现 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userId` | ID | 是 | Path；作者用户 ID，不是当前登录用户身份 |

#### 请求示例

```http
GET /api/v1/blog/authors/12
```

#### 响应参数

`data` 为 AuthorPublic，完整字段与约束见 [22.2](#api-154)：userId、nickname、avatar、intro、articleCount、lastPublishTime。主页文章不内嵌、不截断，使用 [22.1](#article-author-query) 独立分页。

#### 响应示例

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

<a id="api-156"></a>

### 22.4 按 slug 查询公开分类详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/categories/{slug}` |
| 接口描述 | 直接打开分类页时解析 slug，取得分类资料、ID 和公开文章数 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；分类 status=1 |
| 实施状态 | 待实现 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `slug` | string | 是 | Path；ASCII `[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*`，最长 100，区分大小写 |

#### 请求示例

```http
GET /api/v1/blog/categories/tech
```

#### 响应参数

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data.categoryId` | ID | 非 null，供文章列表 categoryId 筛选 |
| `data.categoryName` | S(64) | 分类名称 |
| `data.slug` | string | 当前 slug |
| `data.description` | S(255)? | 分类说明，可 null |
| `data.sortOrder` | integer | int32，字典排序值 |
| `data.articleCount` | string | 该分类当前 PUBLIC 文章数，允许 "0" |

#### 响应示例

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

### 22.5 按 slug 查询公开标签详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/tags/{slug}` |
| 接口描述 | 直接打开标签页时解析 slug，取得标签 ID 和公开文章数 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；标签无 status 字段 |
| 实施状态 | 待实现 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `slug` | string | 是 | Path；ASCII `[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*`，最长 100，区分大小写 |

#### 请求示例

```http
GET /api/v1/blog/tags/java
```

#### 响应参数

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `data.tagId` | ID | 标签 ID，供文章列表 tagId 筛选 |
| `data.tagName` | S(64) | 标签名称 |
| `data.slug` | string | 当前 slug |
| `data.articleCount` | string | 当前关联的不同 PUBLIC 文章数，允许 "0" |

#### 响应示例

```json
{"code":"OK","msg":"成功","data":{"tagId":"1","tagName":"Java","slug":"java","articleCount":"2"},"errors":[]}
```

不存在返回 `404/NOT_FOUND`；已存在但没有公开文章返回详情及 articleCount="0"。取得 ID 后调用 `GET /api/v1/blog/articles?tagId=1`；数量与该列表使用同一 PUBLIC 条件。只新增此详情模型的 articleCount，不改变既有标签列表字段；slug 改名后旧值 404。分类/标签详情都不返回后台 X-Revision 或非公开文章计数。

---

<a id="api-158"></a>

### 22.6 分页查询本人点赞的文章

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/me/liked-articles` |
| 接口描述 | 个人中心回看自己点赞过、当前仍公开的文章 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人且账号启用，无额外后台权限 |
| 实施状态 | 待实现 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |

#### 请求示例

```http
GET /api/v1/me/liked-articles?page=1&pageSize=10
Authorization: Bearer <site-token>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [LikedArticle](#response-likedarticle)；分页字段见 [0.2](#pagination)。

#### 响应示例

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

<a id="api-159"></a>

### 22.7 永久删除回收站文章

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/articles/{articleId}/permanent` |
| 接口描述 | 从回收站不可恢复地删除文章聚合及其互动；正常删除仍走 7.9 |
| Content-Type | 无请求体 |
| 访问要求 | 后台 `blog:article:delete` + 新增 `blog:article:purge`；分别满足各自数据范围 |
| 并发校验 | 必填 If-Revision |
| 实施状态 | 待实现；purge 权限尚未加入初始化授权或后端权限检查 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleId` | ID | 是 | Path；要彻底清理的文章 ID |
| `If-Revision` | string | 是 | Header；原样携带最新后台详情 X-Revision，含引号；不得从列表推测版本 |

#### 请求示例

```http
DELETE /api/v1/admin/articles/101/permanent
Authorization: Bearer <site-token>
If-Revision: "<revision-from-trash-detail>"
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

仅允许永久删除回收站文章；正常文章返回 409，已不存在的目标返回 404。delete 与 purge 分别检查权限范围，详情查询仍需 query 权限；purge 尚待显式初始化与授权。

| 数据 | 删除影响 |
| --- | --- |
| 文章、正文、标签关联、全部评论/回复和点赞 | 永久删除，无法恢复；清理失败整笔回滚 |
| 分类、标签字典、文件元数据与存储对象 | 保留；文件由原文件接口检查引用后单独删除 |
| 浏览明细 | 保留历史路径和标题，articleId 置 null；原站点 PV 保留，不再关联文章 ID 排行 |
| 审计 | 保留历史日志，并记录本次永久删除 |
| slug 与公开入口 | 释放 slug；详情、该文章附件上下文和恢复入口返回 404，不再出现在 Feed、地图及推荐 |

回收站操作需明确提示“永久删除文章及全部评论、点赞，无法恢复”。评论外键清理、锁与事务等实现要求见 [永久删除开发约定](API_CONTRACT.md#permanent-delete-rules)。

---

### 22.8 实施说明

上述新增设计不计入 153 个已实现接口；实现后再更新对应状态与接口数量。开发范围与验收条件见 [补充设计验收](API_CONTRACT.md#planned-acceptance)。
