<a id="client-admin"></a>

# 管理端接口

本端接口使用 `/api/v1/admin` 前缀。

[本端接口索引](API_INDEX.md#index-admin) · [通用协议](API.md#common) · [共享响应模型](API.md#models) · [错误码](API.md#errors) · [本端响应模型](#models)

共用接口见[图形验证码](API.md#captcha)和[未知路由上报](API.md#not-found-protection)。

<a id="admin-group-1"></a>

## 1.1 认证与第三方授权

<a id="admin-api-1"></a>

### 1.1.1 注册账号

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/auth/register` |
| 接口描述 | 注册账号 |
| Content-Type | application/json |
| 访问要求 | 公开 |
| 成功状态 | 201 Created |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `X-Captcha-Id` | string | 是 | Header；见[验证码契约](API.md#captcha) |
| `X-Captcha-Code` | string | 是 | Header；6 位数字，保留前导零 |
| `username` | string | 是 | 3..64 位 ASCII 字母、数字或下划线；全库不区分大小写唯一 |
| `password` | string | 是 | 8..64 个字符且 UTF-8 ≤72 字节；不 trim、不截断 |
| `nickname` | string | 是 | 1..64 字符 |
| `email` | string | 是 | 合法邮箱，≤254 字符，去首尾空白并转小写；注册后保存为账号邮箱 |
| `emailCode` | string | 是 | 对应用途邮件中的六位数字；五分钟有效，校验一次即失效 |

#### 请求示例

```http
POST /api/v1/admin/auth/register
Origin: https://admin.example.com
X-Captcha-Id: <captchaId>
X-Captcha-Code: 012345
Content-Type: application/json

{"username":"leaf","password":"Example-password-2026","nickname":"落叶","email":"leaf@example.test","emailCode":"012345"}
```

#### 响应参数

`data`：[Me](API.md#response-me)。

#### 响应示例

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

先验证 REGISTER 用途的邮箱验证码；用户和 member 关联同事务创建；仅返回资料，不自动登录，不接受角色、状态或客户端 userId。用户名、邮箱或二者重复时，返回 [409/UNIQUE_CONFLICT 及对应字段提示](API.md#user-unique-conflict)。

---

<a id="admin-api-2"></a>

### 1.1.2 账密登录

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/auth/login` |
| 接口描述 | 使用用户名密码登录后台 |
| Content-Type | application/json |
| 访问要求 | 公开 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `X-Captcha-Id` | string | 是 | Header；见[验证码契约](API.md#captcha) |
| `X-Captcha-Code` | string | 是 | Header；6 位数字，保留前导零 |
| `username` | string | 是 | 3..64 位 ASCII 字母、数字或下划线；全库不区分大小写唯一 |
| `password` | string | 是 | 8..64 个字符且 UTF-8 ≤72 字节；不 trim、不截断 |

#### 请求示例

```http
POST /api/v1/admin/auth/login
Origin: https://admin.example.com
X-Captcha-Id: <captchaId>
X-Captcha-Code: 012345
Content-Type: application/json

{"username":"leaf","password":"Example-password-2026"}
```

#### 响应参数

`data`：[Session](API.md#response-session)。成功时通过 `Set-Cookie` 签发登录 Cookie。

#### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "expiresIn": 604800,
    "user": {
      "userId": "12", "username": "leaf", "nickname": "落叶", "email": null, "avatar": null, "intro": null,
      "status": 1
    }
  },
  "errors": []
}
```

邮箱登录另见[邮箱验证与登录方式](API.md#captcha)，不提交用户名或密码。两种后台登录均在用户锁内核对启用状态与 admin:access。

用户名不存在、密码错误、纯第三方账号无密码统一返回 401/AUTH_FAILED；验证凭据后再区分禁用账号。持有用户行锁确认身份和状态后创建会话、更新 lastLoginTime，提交后才返回；数据库提交失败撤销刚签发 Token，Redis 创建失败返回 503。

---

<a id="admin-api-3"></a>

### 1.1.3 退出当前登录

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/auth/logout` |
| 接口描述 | 退出当前登录 |
| Content-Type | 无请求体 |
| 访问要求 | 幂等注销；无会话或已失效仍返回 200 |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
POST /api/v1/admin/auth/logout
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
```

#### 响应参数

`data` 固定为 null。同时下发同名同属性、`Max-Age=0` 的登录 Cookie。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

存在有效会话时，先核对页面预期账号，再撤销对应会话。预期账号缺失或不匹配时返回 `409/SESSION_ACCOUNT_CHANGED`，不撤销会话、不清除 Cookie。无有效会话时仍可幂等退出。

---

<a id="admin-api-4"></a>

### 1.1.4 查询可用第三方平台

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/auth/oauth/providers` |
| 接口描述 | 查询可用第三方平台 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

#### 请求参数

无 Path、Query 或请求体参数；GET/无模型写操作不发送请求体。

#### 请求示例

```http
GET /api/v1/admin/auth/oauth/providers
```

#### 响应参数

`data`：[Provider](API.md#response-provider) 数组。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":[{"source":"GITHUB","displayName":"GitHub"}],"errors":[]}
```

只列本端配置完整且启用的平台，示例 GITHUB 不代表已经开通；不返回 App ID/Secret。

---

<a id="admin-api-5"></a>

### 1.1.5 发起第三方登录或绑定

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/auth/oauth/{source}/authorize` |
| 接口描述 | 发起第三方登录或绑定 |
| Content-Type | application/json |
| 访问要求 | LOGIN 公开，免图形验证码，超限返回 `429/RATE_LIMITED`；BIND 须后台有效会话及最近 300 秒认证；按通用规则检查来源 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `source` | string | 是 | Path；服务端已启用平台的大写标识；未知或未启用 404 |
| `operation` | string | 是 | LOGIN 登录或 BIND 绑定当前用户；不接收 redirectUri/userId/uuid |
| `__Host-dl-oauth-browser` | string | 否（缺失时由后端签发） | Cookie；Secure、HttpOnly、SameSite=Lax、Path=/，仅绑定浏览器，不是业务登录凭证 |
| `X-Expected-User-Id` | string | 仅 BIND | Header。页面预期账号，使用十进制用户 ID。 |
| `Origin` | string | 与 Referer 至少提供一项 | Header。匹配所属端 HTTPS Origin，缺失时按通用规则核对 Referer。 |

#### 请求示例

```http
POST /api/v1/admin/auth/oauth/GITHUB/authorize
Cookie: __Host-dl-oauth-browser=<browser-id>; __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
Origin: https://admin.example.com
Content-Type: application/json

{"operation":"BIND"}
```

#### 响应参数

`data`：[OAuthAuthorizeResult](API.md#response-oauthauthorizeresult)。

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

LOGIN 使用已有第三方绑定，兑换时检查账号状态和 `admin:access`。BIND 使用当前后台会话和最近 300 秒认证，绑定弹窗保留发起页面。

---

<a id="admin-api-6"></a>

### 1.1.6 第三方平台授权回调

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/auth/oauth/{source}/callback` |
| 接口描述 | 第三方平台授权回调 |
| Content-Type | 无请求体 |
| 访问要求 | 登记端按 state 记录转回发起端；发起端校验流程 Cookie，BIND 另查原会话；回调不需要登录 Cookie |
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
| `iss` | string | 否 | 仅 GitHub 回调可带，值必须为 `https://github.com/login/oauth`，校验后不转发 |

#### 请求示例

```http
GET /api/v1/admin/auth/oauth/GITHUB/callback?code=<platform-code>&state=<random-state>&iss=https%3A%2F%2Fgithub.com%2Flogin%2Foauth
Cookie: __Host-dl-oauth-browser=<browser-id>
```

#### 响应参数

| 字段/响应头 | 类型 | 说明 |
| --- | --- | --- |
| `Location` | string | 发起端回调或完成页；完成页成功 fragment 携带 ticket 和 operation，可信失败携带白名单错误码 |
| `Cache-Control` | string | no-store |
| `Referrer-Policy` | string | no-referrer |
| 响应体 | — | 成功重定向不返回 Result；不可信/无效流程直接返回对应 HTTP JSON 错误 |

#### 响应示例

```http
HTTP/1.1 303 See Other
Location: https://admin.example.com/oauth/complete#ticket=<one-time-ticket>&operation=BIND
Cache-Control: no-store
Referrer-Policy: no-referrer
```

平台顶层 GET 回调使用 OAuth 流程 Cookie。后端凭 state、浏览器 Cookie 和 Redis 流程定位操作；BIND 再校验服务端保存的原会话仍有效且属于目标用户。可信流程成功后 303 到固定完成页，不在回调直接向浏览器发本站 Token。已消费的可信流程遇到数据库访问或事务提交失败时，303 到固定完成页的 `#error=SERVICE_UNAVAILABLE`，不签发成功票据，且不能重放本次 state。

---

<a id="admin-api-7"></a>

### 1.1.7 兑换一次性登录或绑定结果

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/auth/oauth/exchange` |
| 接口描述 | 兑换一次性登录或绑定结果 |
| Content-Type | application/json |
| 访问要求 | 同一发起端和浏览器；BIND 另须后台原会话 Cookie |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ticket` | string | 是 | 回调得到的本站一次性票据；有效期 60 秒，不是平台 code 或本站 Token |
| `__Host-dl-oauth-browser` | string | 是 | Cookie；Secure、HttpOnly、SameSite=Lax、Path=/，仅绑定浏览器，不是业务登录凭证 |
| `X-Expected-User-Id` | string | 仅 BIND | Header。页面预期账号，使用十进制用户 ID。 |
| `Origin` | string | 与 Referer 至少提供一项 | Header。匹配所属端 HTTPS Origin，缺失时按通用规则核对 Referer。 |

#### 请求示例

```http
POST /api/v1/admin/auth/oauth/exchange
Cookie: __Host-dl-oauth-browser=<browser-id>; __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
Origin: https://admin.example.com
Content-Type: application/json

{"ticket":"<one-time-ticket>"}
```

#### 响应参数

`data`：[OAuthResult](API.md#response-oauthresult)。

#### 响应示例

BIND 成功（200，原会话必须有效，不返回新 Session）：

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

兑换时携带同一浏览器的 OAuth 流程 Cookie。BIND 还须带发起时的后台登录 Cookie 和 `X-Expected-User-Id`，只返回绑定结果。LOGIN 校验 `admin:access` 后通过 `Set-Cookie` 签发后台会话，返回 `operation=LOGIN` 和 `session`。票据有效期 60 秒且仅消费一次。

---

<a id="admin-auth-email-code"></a>

### 1.1.8 发送后台登录邮箱验证码

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/auth/email-code` |
| Content-Type | application/json |
| 访问要求 | 公开；无需登录，仍执行图形验证码及接口限流 |
| 成功状态 | 200 OK |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `X-Captcha-Id` | string | 是 | Header；当前端图形验证码的随机标识 |
| `X-Captcha-Code` | string | 是 | Header；图形验证码的6位数字，保留前导零 |
| `email` | string | 是 | JSON；非空合法邮箱，最多254字符；服务端去首尾空白并转小写 |

JSON只接收表中标为JSON的字段，不接受查询参数、额外字段或重复JSON属性。图形验证码与邮件验证码是两项独立校验，不可互换。

#### 请求示例

```http
POST /api/v1/admin/auth/email-code
Origin: https://admin.example.com
X-Captcha-Id: <captchaId>
X-Captcha-Code: 012345
Content-Type: application/json

{"email":"leaf@example.test"}
```

#### 响应参数

`data=null`。只有 SMTP 接受发送后才返回成功，不返回邮件验证码或会话。

```json
{
  "code": "OK",
  "msg": "成功",
  "data": null,
  "errors": []
}
```

#### 处理与失败

先检查登录失败限额，再消费图形验证码和检查发送频率；锁定邮箱所属账号，确认账号启用且拥有 `admin:access` 后，向当前账号邮箱发送 ADMIN_LOGIN 用途验证码。不要求用户名或本站密码，不创建会话。 邮件验证码五分钟有效，按邮箱和用途隔离；所有用途与端共享每邮箱每60秒1次、每可信IP每60秒5次的发送预算。

| HTTP / code | 说明 |
| --- | --- |
| 400 / VALIDATION_ERROR | 必填字段、邮箱、验证码格式、用途、JSON或查询参数不合法 |
| 400 / CAPTCHA_INVALID | 图形验证码缺失、无效、过期或已消费 |
| 401 / AUTH_FAILED | 该邮箱未注册 |
| 403 / ACCOUNT_DISABLED | 账号已禁用 |
| 403 / FORBIDDEN | 账号没有有效的 admin:access 权限 |
| 429 / RATE_LIMITED | 邮件发送限额超出；后台还受登录入口和失败限额限制，返回 Retry-After |
| 503 / SERVICE_UNAVAILABLE | 邮件未启用、SMTP发送失败或依赖不可用；发送失败清理已生成验证码 |

每次发送或登录结束后重新获取图形验证码；业务失败保留输入，不自动重放请求。通用响应与安全边界见[邮箱验证与登录方式](API.md#captcha)。

---

<a id="admin-auth-email-login"></a>

### 1.1.9 邮箱验证码登录后台

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/auth/email-login` |
| Content-Type | application/json |
| 访问要求 | 公开；无需登录，仍执行图形验证码及接口限流 |
| 成功状态 | 200 OK |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `X-Captcha-Id` | string | 是 | Header；当前端图形验证码的随机标识 |
| `X-Captcha-Code` | string | 是 | Header；图形验证码的6位数字，保留前导零 |
| `email` | string | 是 | JSON；非空合法邮箱，最多254字符；服务端去首尾空白并转小写 |
| `emailCode` | string | 是 | JSON；邮件中的6位纯数字字符串，保留前导零 |

JSON只接收表中标为JSON的字段，不接受查询参数、额外字段或重复JSON属性。图形验证码与邮件验证码是两项独立校验，不可互换。

#### 请求示例

```http
POST /api/v1/admin/auth/email-login
Origin: https://admin.example.com
X-Captcha-Id: <captchaId>
X-Captcha-Code: 012345
Content-Type: application/json

{"email":"leaf@example.test","emailCode":"012345"}
```

#### 响应参数

成功时通过 `Set-Cookie` 签发登录 Cookie，`data` 为 [Session](API.md#response-session)，`expiresIn=604800`（7天）；`user` 为 [Me](API.md#response-me)，不包含完整IP、密码哈希或权限快照。

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "expiresIn": 604800,
    "user": {
      "userId": "12",
      "username": "leaf",
      "nickname": "落叶",
      "email": "leaf@example.test",
      "avatar": null,
      "intro": null,
      "status": 1,
      "hasPassword": false,
      "lastLoginTime": "2026-11-05T13:00:00.000Z",
      "createTime": "2026-11-01T00:00:00.000Z",
      "updateTime": "2026-11-01T00:00:00.000Z"
    }
  },
  "errors": []
}
```

#### 处理与失败

先检查登录失败限额并消费图形验证码，再锁定邮箱所属账号；找不到账号时清除该用途的旧验证码并提示邮箱未注册。找到账号后消费 ADMIN_LOGIN 用途邮件验证码，不接受注册或另一端登录的验证码。邮件验证码正确后检查账号启用状态以及有效的 `admin:access` 权限，成功才签发本站会话。邮箱方式不要求已设置本站密码。邮件校验无论正确、错误或后续业务失败，当前验证码都不能再次使用。错误邮件验证码或 AUTH_FAILED 会计入登录失败预算，CAPTCHA_INVALID 不计入密码/邮箱登录失败次数；图形验证码失败时不会进入邮件校验。

| HTTP / code | 说明 |
| --- | --- |
| 400 / VALIDATION_ERROR | 必填字段、邮箱、验证码格式、用途、JSON或查询参数不合法 |
| 400 / CAPTCHA_INVALID | 图形验证码缺失、无效、过期或已消费 |
| 400 / EMAIL_CODE_INVALID | 邮件验证码错误、过期、用途不符或已消费 |
| 401 / AUTH_FAILED | 该邮箱未注册 |
| 403 / ACCOUNT_DISABLED | 账号已禁用 |
| 403 / FORBIDDEN | 账号没有有效的 admin:access 权限 |
| 429 / RATE_LIMITED | 登录入口预算或失败次数超限，返回 Retry-After |
| 503 / SERVICE_UNAVAILABLE | Redis、数据库等依赖不可用，不能跳过校验或返回成功 |

每次发送或登录结束后重新获取图形验证码；业务失败保留输入，不自动重放请求。通用响应与安全边界见[邮箱验证与登录方式](API.md#captcha)。

---

<a id="admin-group-2"></a>

## 1.2 本人资料与安全设置

<a id="admin-api-8"></a>

### 1.2.1 查询本人资料

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/me` |
| 接口描述 | 查询本人资料 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
GET /api/v1/admin/me
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：[Me](API.md#response-me)。

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

<a id="admin-api-9"></a>

### 1.2.2 修改本人资料

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PATCH /api/v1/admin/me/profile` |
| 接口描述 | 修改本人资料 |
| Content-Type | application/json |
| 访问要求 | 登录本人 |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `nickname` | string | 否 | 提供时不可为 null，1..64 字符 |
| `email` | string&#124;null | 否 | 合法邮箱，≤254 字符，唯一；已绑定时不能清空 |
| `emailCode` | string | 更换或首次绑定时必填 | 新邮箱收到的六位验证码 |
| `avatar` | string&#124;null | 否 | 外部 http(s) URL ≤2048 字符；null 清空 |
| `intro` | string&#124;null | 否 | 纯文本 ≤500 字符；null 清空；四项至少提供一项 |

#### 请求示例

```http
PATCH /api/v1/admin/me/profile
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"nickname":"新落叶","email":"new@example.test","emailCode":"012345","avatar":null,"intro":null}
```

#### 响应参数

`data`：[Me](API.md#response-me)。

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
    "userId": "12", "username": "leaf", "nickname": "新落叶", "email": "new@example.test", "avatar": null, "intro": null,
    "status": 1
  },
  "errors": []
}
```

昵称、邮箱、头像、简介至少提交一项，缺失的字段保持原值。头像和简介可传 null 清空。username、status、角色、权限不可改。头像接受公开 HTTP(S) 图片地址，包括[公开展示图片](API_BLOG.md#display-image-content)。附件 API 路径及其等价绝对 URL（含路径编码、归一化后的形式）会被拒绝。

<a id="admin-email-change-code"></a>

#### 发送本人换绑验证码

登录后调用 `POST /api/v1/admin/me/email-change-code`，提交 `{"email":"new@example.test"}`。返回 `Result<null>` 时邮件已提交发送。将新邮箱收到的六位数字作为 `emailCode` 提交资料。验证码五分钟有效，每次校验后即失效。发送限流返回 429，验证码缺失、错误或过期时提交资料返回 `400/EMAIL_CODE_INVALID`。

---

<a id="admin-api-10"></a>

### 1.2.3 设置或修改本站密码

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/me/password` |
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
PUT /api/v1/admin/me/password
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
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

<a id="admin-api-11"></a>

### 1.2.4 清除本站密码

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/me/password` |
| 接口描述 | 清除本站密码 |
| Content-Type | application/json |
| 访问要求 | 登录本人 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `currentPassword` | string | 是 | 验证现有密码；无密码账号返回 STATE_CONFLICT |

#### 请求示例

```http
DELETE /api/v1/admin/me/password
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
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

<a id="admin-api-12"></a>

### 1.2.5 查询本人有效角色与权限

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/me/capabilities` |
| 接口描述 | 查询本人有效角色与权限 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
GET /api/v1/admin/me/capabilities
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：[Capabilities](API.md#response-capabilities)。

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
      "blog:message:create": "SELF", "blog:message:deleteOwn": "SELF"
    }
  },
  "errors": []
}
```

普通 member 也可调用；仅返回当前有效授权，角色/权限字典序，数据范围按每个权限计算。

---

<a id="admin-api-13"></a>

### 1.2.6 查询本人可访问菜单树

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/me/menus` |
| 接口描述 | 查询本人可访问菜单树 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
GET /api/v1/admin/me/menus
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：[MenuNode](API.md#response-menunode) 数组。

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

<a id="admin-api-14"></a>

### 1.2.7 查询本人第三方绑定

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/me/oauth-bindings` |
| 接口描述 | 查询本人第三方绑定 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
GET /api/v1/admin/me/oauth-bindings
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：[OAuthBinding](API.md#response-oauthbinding) 数组。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":[{"source":"GITHUB","createTime":"2026-10-29T02:00:00.000Z"}],"errors":[]}
```

source 升序，只列当前配置完整且启用的平台绑定，仅返回平台和绑定时间，不暴露 uuid、绑定记录 ID 或第三方 Token。未启用或配置不完整的绑定记录仍保留在库中，不计为可用登录方式。

---

<a id="admin-api-15"></a>

### 1.2.8 解绑本人第三方账号

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/admin/me/oauth-bindings/{source}` |
| 接口描述 | 解绑本人第三方账号 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `source` | string | 是 | Path；服务端已启用平台的大写标识；未知或未启用 404 |

#### 请求示例

```http
DELETE /api/v1/admin/me/oauth-bindings/GITHUB
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

最近认证；未绑定 404。锁同一用户，检查剩余有效密码或可用平台绑定；禁止解绑最后登录方式，成功撤销全部会话。

---

<a id="group-6"></a>

<a id="admin-group-3"></a>

## 1.3 菜单管理

<a id="api-32"></a>

### 1.3.1 查询后台完整菜单树

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
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：[MenuNode](API.md#response-menunode) 数组。

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

### 1.3.2 查询菜单详情

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
Cookie: __Host-dl-admin-session=<opaque>
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

### 1.3.3 创建菜单节点

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
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
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

### 1.3.4 修改菜单节点

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

#### 请求示例

```http
PUT /api/v1/admin/menus/110
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "parentId": "100", "menuName": "文章扩展页", "menuType": "M", "routeName": "ArticleExtra",
  "path": "/content/articles-extra", "component": "content/article/index", "icon": "document",
  "permissionId": "10", "sortOrder": 20, "isVisible": 1, "keepAlive": 0
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


本接口只修改资料，保留当前 status。状态统一使用[批量切换菜单状态](#batch-menus-status)，单个目标也提交一项 items。创建时仍可指定初始状态。

---


<a id="group-3"></a>

<a id="admin-group-4"></a>

## 1.4 用户管理

用户批量操作见[批量启用](#batch-user-enable)、[批量禁用](#batch-user-disable)、[批量删除（含单个用户删除）](#batch-user-delete)，均支持 1..100 个显式目标。

<a id="api-16"></a>

### 1.4.1 分页查询用户

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
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [UserAdmin](#response-useradmin)；分页字段见 [0.2](API.md#pagination)。

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

### 1.4.2 查询用户详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/users/{userId}` |
| 接口描述 | 查询用户详情 |
| Content-Type | 无请求体 |
| 访问要求 | 已登录且账号启用，具有 `admin:access` 和 `sys:user:query`；全局查询，SELF 不缩小范围 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/users/12
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：[UserAdmin](#response-useradmin)。包含账号资料、密码设置状态、最近登录信息、创建及更新时间、完整角色 ID 与角色展示信息；禁用用户也可查看。

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
    "status": 1, "hasPassword": true,
    "lastLoginTime": "2026-11-01T14:03:00.000Z",
    "lastLoginIp": "198.51.100.8", "lastLoginLocation": "江苏省 南京市",
    "createTime": "2026-10-01T00:00:00.000Z", "updateTime": "2026-11-01T14:03:00.000Z",
    "roleIds": ["2", "3"],
    "roles": [
      { "roleId": "2", "roleCode": "member", "roleName": "普通用户", "status": 1 },
      { "roleId": "3", "roleCode": "author", "roleName": "作者", "status": 0 }
    ]
  },
  "errors": []
}
```

`roleIds` 与 `roles` 都按角色 ID 升序返回，包含已分配但禁用的角色；未分配角色时均为 `[]`。`roles[]` 仅含 `roleId`、`roleCode`、`roleName`、`status`（0 禁用、1 启用），供用户管理详情展示，不要求额外的 `sys:role:query`，也不能据此判断当前有效权限。列表、创建和更新用户响应同步增加 `roles`，原有字段保留。

详情返回与本人资料共用的 X-Revision；版本仍只由原有用户业务字段和完整 roleIds 生成。角色名称和启用状态、密码、登录信息及审计时间不参与该版本。不返回密码哈希、第三方身份凭据或会话 Token。

#### 错误响应

| HTTP 状态 | code | 说明 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | userId 格式无效或超出 Long 范围，不接受前导零、符号或空白 |
| 401 | `UNAUTHENTICATED` | 未登录或当前会话失效 |
| 403 | `FORBIDDEN` | 缺少后台入口、用户查询权限，或使用非后台登录会话 |
| 403 | `ACCOUNT_DISABLED` | 当前操作者账号已禁用；目标账号禁用不影响查询 |
| 404 | `NOT_FOUND` | 目标用户不存在 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库或会话服务不可用 |

后台用户列表提供“详情”操作，点击后请求本接口并在只读弹窗展示最新资料；加载期间显示进度，失败显示错误及重试入口，不以列表行或上一次详情冒充当前结果。

---

<a id="api-18"></a>

### 1.4.3 创建用户

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
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
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

新建用户不分配角色；需要 assignRole 权限才能另行分配，不沿用公开注册的 member 默认。用户名、邮箱或二者重复时，返回 [409/UNIQUE_CONFLICT 及对应字段提示](API.md#user-unique-conflict)。

---

<a id="api-19"></a>

### 1.4.4 修改用户资料

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/users/{userId}` |
| 接口描述 | 修改用户资料 |
| Content-Type | application/json |
| 访问要求 | 后台：`sys:user:update` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `nickname` | string | 是 | 1..64 字符 |
| `email` | string&#124;null | 是 | 合法邮箱，≤254 字符，唯一；仅当前未绑定时可传 null |
| `emailCode` | string | 更换或首次绑定时必填 | 新邮箱收到的六位验证码 |
| `avatar` | string&#124;null | 是，可 null | 仅外部 http(s) URL |
| `intro` | string&#124;null | 是，可 null | ≤500 字符 |

#### 请求示例

```http
PUT /api/v1/admin/users/12
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"nickname":"落叶","email":"new@example.test","emailCode":"012345","avatar":null,"intro":null}
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
    "userId": "12", "username": "leaf", "nickname": "落叶", "email": "new@example.test", "avatar": null, "intro": null,
    "status": 1
  },
  "errors": []
}
```

本接口修改昵称、邮箱、头像和简介。创建时可指定初始状态，已有用户可在用户列表的操作列点击「启用」或「禁用」。单条操作复用[启用/禁用用户接口](#batch-user-enable)，在 items 中提交一项。

<a id="admin-user-email-change-code"></a>

#### 发送目标用户换绑验证码

持有 `sys:user:update` 权限的操作者调用 `POST /api/v1/admin/users/{userId}/email-change-code`，提交 `{"email":"new@example.test"}`，成功返回 `Result<null>`。发送和校验使用与本人入口相同的有效期、限流与错误规则，验证码绑定操作者、目标用户和新邮箱。

---

<a id="api-20"></a>

### 1.4.5 分配用户角色

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
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
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

<a id="admin-group-5"></a>

## 1.5 角色与授权管理

<a id="api-21"></a>

### 1.5.1 分页查询角色

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
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [Role](#response-role)；分页字段见 [0.2](API.md#pagination)。

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

### 1.5.2 查询角色详情

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
Cookie: __Host-dl-admin-session=<opaque>
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

### 1.5.3 创建角色

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
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
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

### 1.5.4 修改角色

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

#### 请求示例

```http
PUT /api/v1/admin/roles/4
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"roleName":"内容编辑","description":null,"dataScope":"SELF"}
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


本接口只修改资料，保留当前 status。状态统一使用[批量切换角色状态](#batch-roles-status)，单个目标也提交一项 items。创建时仍可指定初始状态。

---


<a id="api-26"></a>

### 1.5.6 配置角色权限与菜单

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
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
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

<a id="admin-group-6"></a>

## 1.6 权限字典管理

<a id="api-27"></a>

### 1.6.1 分页查询权限字典

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
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [Permission](#response-permission)；分页字段见 [0.2](API.md#pagination)。

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

### 1.6.2 查询权限详情

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
Cookie: __Host-dl-admin-session=<opaque>
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

### 1.6.3 创建权限字典项

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
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
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

### 1.6.4 修改权限字典项

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

#### 请求示例

```http
PUT /api/v1/admin/permissions/114
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"permissionName":"导出文章","module":"blog","description":null}
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


本接口只修改资料，保留当前 status。状态统一使用[批量切换权限状态](#batch-permissions-status)，单个目标也提交一项 items。创建时仍可指定初始状态。

---


<a id="admin-group-7"></a>

## 1.7 文章与附件

<a id="api-40"></a>

### 1.7.1 分页查询后台文章

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
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [ArticleAdminSummary](#response-articleadminsummary)；分页字段见 [0.2](API.md#pagination)。

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
        "category": null, "title": "第一片落叶", "slug": "first-leaf", "status": 0,
        "isTop": 0, "viewCount": "0", "likeCount": "0", "commentCount": "0"
      }
    ]
  },
  "errors": []
}
```

先按置顶状态排序，置顶文章排在普通文章前面。同组按 `updateTime`、`articleId` 降序排列，再分页。`trash` 默认 0。SELF 强制本人，`authorId` 不能扩大范围。

---

<a id="api-41"></a>

### 1.7.2 查询后台文章详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/articles/{articleId}` |
| 接口描述 | 查询后台文章完整详情，供文章列表的详情弹窗与编辑回填共用 |
| Content-Type | 无请求体 |
| 访问要求 | 有效后台登录 + `blog:article:query`，按该权限的 ALL/SELF 范围查询 |
| 并发校验 | 返回 X-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/articles/101
Cookie: __Host-dl-admin-session=<opaque>
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
    "category": null, "categoryId": null, "tags": [], "tagIds": [],
    "title": "第一片落叶", "slug": "first-leaf", "summary": "记录与分享",
    "coverUrl": null, "articleType": 1, "originalUrl": null,
    "status": 0, "visibility": 1, "isTop": 0, "allowComment": 1,
    "viewCount": "0", "likeCount": "0", "commentCount": "0",
    "publishTime": null, "deletedAt": null,
    "createTime": "2026-10-30T02:00:00.000Z", "updateTime": "2026-10-30T02:00:00.000Z",
    "markdownContent": "# 第一片落叶"
  },
  "errors": []
}
```

详情与编辑共用此接口，不另建公开预览路由。返回当前资料、分类、升序标签和 Markdown 原文；资料、关联、正文和 X-Revision 在同一个只读事务中读取。草稿、下架、私密及回收站文章均可按查询权限查看；回收站仍禁止编辑和发布。

读取不增加文章阅读量，不修改状态、更新时间或版本。不存在及超出 SELF 范围统一返回 `404/NOT_FOUND`；无后台入口或查询权限返回 `403/FORBIDDEN`；未登录返回 `401/UNAUTHENTICATED`；ID 格式错误返回 `400/VALIDATION_ERROR`；正文缺失返回 `503/SERVICE_UNAVAILABLE`，不伪造空正文。

管理端在文章标题和操作列提供“详情”入口，打开后独立展示加载、失败重试或完整详情，不将列表数据当作详情。详情只读，不能因查看而要求编辑/发布权限；列表和回收站共用此入口。网页正文复用现有 Markdown 预览组件及清理规则，受管图片通过后台文章附件接口通过后台会话读取，临时地址随组件销毁释放；公开详情的 PUBLIC 条件保持不变。

---

<a id="api-42"></a>

### 1.7.3 创建草稿文章

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
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
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

创建成功返回详情 Location 与当前聚合 X-Revision；封面中的等价绝对本站附件 URL 保存为规范公开相对路径。后台详情的 coverUrl 也保留此规范写入值，HTML 内的本站附件属性则转换为后台文章上下文；后台预览封面需使用对应后台上下文并通过同源请求获取，不能向图片 URL 添加 Token。

---

<a id="api-43"></a>

### 1.7.4 编辑文章

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
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
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




<a id="api-86"></a>

### 1.7.8 读取后台文章附件

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
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

| 字段/响应头 | 类型 | 说明 |
| --- | --- | --- |
| 响应体 | binary | 已存储文件字节（图片可能已压缩），不是 Result；支持 Range/206/416 |
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


### 22.8 实施说明

前台和后台的文章列表均支持 `authorId` 筛选。开发约定和验收范围见[文章发现与内容管理](API_CONTRACT.md#planned-acceptance)。

---

<a id="admin-group-8"></a>

## 1.8 分类与标签

<a id="api-48"></a>

### 1.8.1 分页查询后台分类

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
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [Category](#response-category)；分页字段见 [0.2](API.md#pagination)。

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

### 1.8.2 查询分类详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/categories/{categoryId}` |
| 接口描述 | 查询分类详情 |
| Content-Type | 无请求体 |
| 访问要求 | 使用后台登录会话，账号启用且具有 `admin:access` 和 `blog:category:query`；全局查询，SELF 不缩小范围 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `categoryId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/categories/1
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data` 返回 categoryId、categoryName、slug、description、sortOrder、status、createTime、updateTime，字段定义见 [Category](#response-category)。

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
    "categoryId": "1", "categoryName": "技术", "slug": "tech", "description": null, "sortOrder": 0, "status": 1,
    "createTime": "2026-10-29T02:00:00.000Z", "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

隐藏分类也能查看，status 为 0 隐藏、1 展示。详情与 X-Revision 在同一只读事务中读取；ID 保留十进制字符串，时间使用 UTC 毫秒格式。详情查询不修改资源或文章关联，编辑、批量操作继续使用原有版本规则。

管理列表提供“详情”按钮，只需查询权限，不要求编辑或删除权限。点击后请求本接口，在只读弹窗展示最新资料；支持加载提示、错误重试、关闭时取消请求，不使用列表行或上一次结果替代当前详情。描述按文本展示，可空描述显示未填写。

#### 错误响应

| HTTP 状态 | code | 说明 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | ID 格式无效或超出 Long 范围，不接受前导零、符号或空白 |
| 401 | `UNAUTHENTICATED` | 未登录或会话失效 |
| 403 | `ACCOUNT_DISABLED` | 当前操作者账号已禁用 |
| 403 | `FORBIDDEN` | 缺少后台入口或本接口查询权限，或使用非后台登录会话 |
| 404 | `NOT_FOUND` | 目标分类不存在 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库或会话服务不可用 |

---

<a id="api-50"></a>

### 1.8.3 创建分类

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
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
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

### 1.8.4 修改分类

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

#### 请求示例

```http
PUT /api/v1/admin/categories/1
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"categoryName":"技术","slug":"tech","description":null,"sortOrder":0}
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

后台(blog:category:update)。本接口只完整替换名称、slug、说明和排序，保留当前展示状态。展示/隐藏统一使用[批量切换分类状态](#batch-category-status)，单个分类也提交一项 items。

---


<a id="api-54"></a>

### 1.8.6 分页查询后台标签

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
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [Tag](#response-tag)；分页字段见 [0.2](API.md#pagination)。

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

### 1.8.7 查询标签详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/tags/{tagId}` |
| 接口描述 | 查询标签详情 |
| Content-Type | 无请求体 |
| 访问要求 | 使用后台登录会话，账号启用且具有 `admin:access` 和 `blog:tag:query`；全局查询，SELF 不缩小范围 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `tagId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/tags/1
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data` 返回 tagId、tagName、slug、createTime、updateTime，字段定义见 [Tag](#response-tag)。

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

标签没有展示状态、描述或排序字段。详情与 X-Revision 在同一只读事务中读取；ID 保留十进制字符串，时间使用 UTC 毫秒格式。详情查询不修改资源或文章关联，编辑、批量操作继续使用原有版本规则。

管理列表提供“详情”按钮，只需查询权限，不要求编辑或删除权限。点击后请求本接口，在只读弹窗展示最新资料；支持加载提示、错误重试、关闭时取消请求，不使用列表行或上一次结果替代当前详情。

#### 错误响应

| HTTP 状态 | code | 说明 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | ID 格式无效或超出 Long 范围，不接受前导零、符号或空白 |
| 401 | `UNAUTHENTICATED` | 未登录或会话失效 |
| 403 | `ACCOUNT_DISABLED` | 当前操作者账号已禁用 |
| 403 | `FORBIDDEN` | 缺少后台入口或本接口查询权限，或使用非后台登录会话 |
| 404 | `NOT_FOUND` | 目标标签不存在 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库或会话服务不可用 |

---

<a id="api-56"></a>

### 1.8.8 创建标签

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
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
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

### 1.8.9 修改标签

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
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
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


<a id="admin-group-9"></a>

## 1.9 评论管理

<a id="api-63"></a>

### 1.9.1 分页查询后台评论

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
| `status` | integer | 否 | 可选，0/1/2；含义见该业务状态约定 |
| `parentId` | string | 否 | 未传：保留所有层级的平铺分页；`"0"`：仅根评论；正十进制 ID：仅该评论的直接回复，父评论不存在或超出查询范围返回404 |

#### 请求示例

```http
GET /api/v1/admin/comments?page=1&pageSize=10
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [CommentAdmin](#response-commentadmin)；分页字段见 [0.2](API.md#pagination)。

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

接口省略 `parentId` 时，对全部层级记录按 `createTime DESC,commentId DESC` 平铺分页。正 ID 的直接回复查询继续兼容，后台不套用公开审核状态或文章可见性过滤。

**管理端交互。** 评论表格在默认和筛选时都省略 `parentId`，主评论和各层回复在同一套分页中平铺。通过顶部审核状态下拉框和“查询”提交筛选，表头复选框只选择本页记录。“查看”打开详情弹窗。审核前读取详情 `X-Revision`，删除前确认将一并删除全部层级回复。操作后清空选择，保留已提交筛选和当前页。

---

<a id="api-64"></a>

### 1.9.2 查询后台评论详情

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
Cookie: __Host-dl-admin-session=<opaque>
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



<a id="admin-comments-conversation"></a>

### 1.9.3 查询整组评论对话

`GET /api/v1/admin/comments/{commentId}/conversation`，无请求体，使用后台会话并要求 `admin:access` 及 `blog:comment:query`，SELF 按所属文章作者限定，ALL 查询全部；目标或其父链不在范围返回404。

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `commentId` | ID | 是 | Path；当前选中记录的原始字符串 ID，可以是任意深度 |
| `page` | integer | 否 | 1..100000；不传时自动定位该记录所在的回复页，选中主记录时为第1页 |
| `pageSize` | integer | 否 | 默认20，1..50 |

响应 `data` 为 [评论对话模型](#response-discussionconversation)，包含 `root`（[CommentAdmin](#response-commentadmin) 主记录）及标准分页字段 `page/pageSize/total/totalPages/records`。`records` 只包含当前页回复，合并主记录下全部层级，按 `createTime ASC,commentId ASC` 排序；`total/totalPages` 为字符串且不包含主记录。无回复时仍返回主记录，超末页保留请求页码和空 `records`。响应 `parentId` 保留真实直接回复目标；`replyTarget` 含直接目标昵称和最多120字符原文摘要。根及回复的 `hasChildren` 在此查询中为null，不用于递归展开。

只在上述后台查询范围内展示待审、驳回及私密内容；评论正文按纯文本显示，留言的 Markdown 使用后端清理后的 `htmlContent`。该入口不返回编辑版本，审核仍先通过原详情接口读取 `X-Revision`，按现有批量审核规则提交。

此接口用于读取评论整组对话。管理端评论表格通过评论详情接口打开“查看”弹窗。

#### 请求与响应示例

以下为虚构记录。选中回复42且省略page，服务端返回该回复所在页；root单独返回，不重复放入records。

```http
GET /api/v1/admin/comments/42/conversation?pageSize=20
Cookie: __Host-dl-admin-session=<opaque>
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "root": {
      "commentId": "41",
      "articleId": "7",
      "userId": "12",
      "parentId": null,
      "content": "示例主评论",
      "location": null,
      "userAgentOs": null,
      "userAgentBrowser": null,
      "status": 1,
      "createTime": "2026-11-05T12:00:00.000Z",
      "updateTime": "2026-11-05T12:00:00.000Z",
      "author": {
        "userId": "12",
        "nickname": "示例作者",
        "avatar": null
      },
      "articleTitle": "示例文章",
      "hasChildren": null,
      "replyTarget": null,
      "articleAuthor": true,
      "siteAuthor": true,
      "siteAdmin": false
    },
    "page": 1,
    "pageSize": 20,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "commentId": "42",
        "articleId": "7",
        "userId": "19",
        "parentId": "41",
        "content": "示例回复",
        "location": null,
        "userAgentOs": null,
        "userAgentBrowser": null,
        "status": 1,
        "createTime": "2026-11-05T12:01:00.000Z",
        "updateTime": "2026-11-05T12:01:00.000Z",
        "author": {
          "userId": "19",
          "nickname": "示例读者",
          "avatar": null
        },
        "articleTitle": "示例文章",
        "hasChildren": null,
        "replyTarget": {
          "nickname": "示例作者",
          "content": "示例主评论"
        },
        "articleAuthor": false,
        "siteAuthor": false,
        "siteAdmin": false
      }
    ]
  },
  "errors": []
}
```

| HTTP / code | 说明 |
| --- | --- |
| 400 / VALIDATION_ERROR | 路径ID、页码、页大小不合法，或提交未知/重复/空白查询参数、请求体 |
| 401 / UNAUTHENTICATED | 无有效后台会话。仅携带博客会话 Cookie 时也按未登录处理 |
| 403 / ACCOUNT_DISABLED | 当前账号已禁用 |
| 403 / FORBIDDEN | 后台认证方式无效，或缺少后台入口权限、本次查询权限 |
| 404 / NOT_FOUND | 记录不存在、父链无法完整定位，或评论不在当前文章作者范围 |
| 503 / SERVICE_UNAVAILABLE | 身份、权限或数据依赖不可用 |


---

<a id="admin-group-10"></a>

## 1.10 留言板与审核

<a id="api-115"></a>

### 1.10.1 分页查询后台留言

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
| `status` | integer | 否 | 0/1/2 |
| `userId` | ID | 否 | 作者筛选 |
| `isSecret` | integer | 否 | 0/1 |
| `begin` | Time | 否 | createTime含下界 |
| `end` | Time | 否 | createTime不含上界 |
| `parentId` | string | 否 | 未传时平铺全部层级。`"0"` 只查根留言。正十进制 ID 只查该留言的直接回复，父留言不存在返回 404 |

#### 请求示例

```http
GET /api/v1/admin/messages?page=1&pageSize=10
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [MessagePrivate](API.md#response-messageprivate)；分页字段见 [0.2](API.md#pagination)。

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
        "location": "江苏省 南京市", "userAgentOs": "Windows", "userAgentBrowser": "Edge",
        "content": "你好，欢迎交流。", "htmlContent": "<p>你好，欢迎交流。</p>", "contentFormat": 0, "status": 1
      }
    ]
  },
  "errors": []
}
```

筛选AND组合；begin<end；createTime DESC,messageId DESC。后台可读私密内容，不向前台公开后台投影。

正 ID 的直接回复查询继续兼容。后台 query 仍须 ALL，不使用公开留言的审核和私密父链过滤。后台回复仍检查 review 的 ALL、完整父链审核及最早私密范围。

**管理端交互。** 留言表格在默认和筛选时都省略 `parentId`，主留言和各层回复在同一套分页中平铺。顶部通过“查询”提交筛选，表头复选框只选择本页记录。表格保留悄悄话标记、回复对象及行内回复入口，“查看”打开详情弹窗。写入后按已提交筛选和当前页刷新。

---

<a id="api-116"></a>

### 1.10.2 查询后台留言详情

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
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：[MessagePrivate](API.md#response-messageprivate)。

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
    "location": "江苏省 南京市", "userAgentOs": "Windows", "userAgentBrowser": "Edge",
    "content": "你好，欢迎交流。", "htmlContent": "<p>你好，欢迎交流。</p>", "contentFormat": 0, "status": 1
  },
  "errors": []
}
```

---



<a id="api-119"></a>

### 1.10.5 后台回复留言

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
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
Content-Type: application/json

{"content":"你好，欢迎交流。","contentFormat":0,"notifyReply":0}
```

#### 响应参数

`data`：[MessagePrivate](API.md#response-messageprivate)。

#### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "messageId": "301", "parentId": null,
    "author": {
      "userId": "12", "nickname": "落叶", "avatar": null
    },
    "location": "江苏省 南京市", "userAgentOs": "Windows", "userAgentBrowser": "Edge",
    "content": "你好，欢迎交流。", "htmlContent": "<p>你好，欢迎交流。</p>", "contentFormat": 0, "status": 1
  },
  "errors": []
}
```

不要求图形验证码，也无需发送 X-Captcha-Id/X-Captcha-Code；仍须后台登录、blog:message:review 的 ALL 范围及用户/IP 限流。作者为当前后台用户，父节点固定Path，继承整条私密范围，初始status=1。父链必须允许展示，待审/驳回/占位目标409；message.enabled=false时拒绝。不接受isAdminReply标志，展示管理员身份时实时核验有效角色。 创建成功不设置Location，通过响应ID及本人/后台详情入口定位。

---

<a id="admin-messages-conversation"></a>

### 1.10.4 查询整组留言对话

`GET /api/v1/admin/messages/{id}/conversation`，无请求体，使用后台会话并要求 `admin:access` 及 `blog:message:query` 的 ALL；缺少 ALL 返回403，目标不存在返回404。

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | ID | 是 | Path；当前选中记录的原始字符串 ID，可以是任意深度 |
| `page` | integer | 否 | 1..100000；不传时自动定位该记录所在的回复页，选中主记录时为第1页 |
| `pageSize` | integer | 否 | 默认20，1..50 |

响应 `data` 为 [留言对话模型](#response-discussionconversation)，包含 `root`（[MessagePrivate](API.md#response-messageprivate) 主记录）及标准分页字段 `page/pageSize/total/totalPages/records`。`records` 只包含当前页回复，合并主记录下全部层级，按 `createTime ASC,messageId ASC` 排序；`total/totalPages` 为字符串且不包含主记录。无回复时仍返回主记录，超末页保留请求页码和空 `records`。响应 `parentId` 保留真实直接回复目标；`replyTarget` 含直接目标昵称和最多120字符原文摘要。根及回复的 `hasChildren` 在此查询中为null，不用于递归展开。

只在上述后台查询范围内展示待审、驳回及私密内容；评论正文按纯文本显示，留言的 Markdown 使用后端清理后的 `htmlContent`。该入口不返回编辑版本，审核仍先通过原详情接口读取 `X-Revision`，按现有批量审核规则提交。

此接口用于读取留言整组对话。管理端留言表格通过留言详情接口打开“查看”弹窗。

#### 请求与响应示例

以下为虚构记录。选中回复52且省略page，服务端返回该回复所在页；root单独返回，不重复放入records。

```http
GET /api/v1/admin/messages/52/conversation?pageSize=20
Cookie: __Host-dl-admin-session=<opaque>
```

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "root": {
      "messageId": "51",
      "parentId": null,
      "author": {
        "userId": "12",
        "nickname": "示例读者",
        "avatar": null
      },
      "content": "示例私密留言",
      "htmlContent": "<p>示例私密留言</p>",
      "contentFormat": 0,
      "isSecret": 1,
      "status": 1,
      "editedAt": null,
      "isOwn": false,
      "createTime": "2026-11-05T12:00:00.000Z",
      "updateTime": "2026-11-05T12:00:00.000Z",
      "notifyReply": 0,
      "location": null,
      "userAgentOs": null,
      "userAgentBrowser": null,
      "hasChildren": null,
      "replyTarget": null,
      "siteAuthor": false,
      "siteAdmin": false
    },
    "page": 1,
    "pageSize": 20,
    "total": "1",
    "totalPages": "1",
    "records": [
      {
        "messageId": "52",
        "parentId": "51",
        "author": {
          "userId": "1",
          "nickname": "示例管理员",
          "avatar": null
        },
        "content": "示例后台回复",
        "htmlContent": "<p>示例后台回复</p>",
        "contentFormat": 0,
        "isSecret": 1,
        "status": 1,
        "editedAt": null,
        "isOwn": true,
        "createTime": "2026-11-05T12:01:00.000Z",
        "updateTime": "2026-11-05T12:01:00.000Z",
        "notifyReply": 0,
        "location": null,
        "userAgentOs": null,
        "userAgentBrowser": null,
        "hasChildren": null,
        "replyTarget": {
          "nickname": "示例读者",
          "content": "示例私密留言"
        },
        "siteAuthor": true,
        "siteAdmin": true
      }
    ]
  },
  "errors": []
}
```

| HTTP / code | 说明 |
| --- | --- |
| 400 / VALIDATION_ERROR | 路径ID、页码、页大小不合法，或提交未知/重复/空白查询参数、请求体 |
| 401 / UNAUTHENTICATED | 无有效后台会话。仅携带博客会话 Cookie 时也按未登录处理 |
| 403 / ACCOUNT_DISABLED | 当前账号已禁用 |
| 403 / FORBIDDEN | 后台认证方式无效、缺少后台入口或查询权限，或留言查询权限没有 ALL 范围 |
| 404 / NOT_FOUND | 记录不存在、父链无法完整定位 |
| 503 / SERVICE_UNAVAILABLE | 身份、权限或数据依赖不可用 |


---

<a id="admin-group-11"></a>

## 1.11 友链

后台列表、详情、编辑、状态切换和删除统一使用同一个 `linkId`。博客申请和后台直接创建的友链都进入此列表。管理接口使用有效后台会话，要求账号启用、`admin:access` 和各操作的业务权限。友链是全局资源，`SELF` 授权同样可操作所有友链，包括其他账号提交的申请。写请求按[通用协议](API.md#common)校验请求来源和 `X-Expected-User-Id`。

会话失效返回 `401/UNAUTHENTICATED`，账号禁用返回 `403/ACCOUNT_DISABLED`，权限不足返回 `403/FORBIDDEN`。写请求缺少预期账号或与实际账号不符时返回 `409/SESSION_ACCOUNT_CHANGED`。其他通用错误见[错误码](API.md#errors)。

<a id="api-71"></a>

### 1.11.1 分页查询后台友链

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
| `keyword` | string | 否 | 首尾去空白后 1..100 字符，按友站名称或网址字面包含匹配 |
| `status` | integer | 否 | 0 待审核、1 展示、2 驳回/下架 |
| `source` | string | 否 | 精确匹配 `APPLICATION`、`ADMIN` 或 `UNKNOWN`，取值区分大小写 |
| `applicantId` | ID | 否 | 精确匹配当前绑定的账号 ID，使用 Long 范围内的十进制正整数字符串 |

#### 请求示例

```http
GET /api/v1/admin/links?page=1&pageSize=10
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [AdminLink](#response-admin-link)；分页字段见 [0.2](API.md#pagination)。

#### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "linkId": "1", "linkName": "示例友站", "url": "https://example.com", "avatar": null, "description": null,
        "sortOrder": 0, "status": 1,
        "createTime": "2026-10-29T02:00:00.000Z", "updateTime": "2026-10-29T02:00:00.000Z",
        "source": "ADMIN", "applicant": {"userId": "12", "nickname": "落叶"}
      }
    ]
  },
  "errors": []
}
```

按 `sortOrder`、`linkId` 依次升序。

<a id="admin-link-source"></a>

#### 友链来源和绑定账号

列表和[详情](#api-72)均返回来源、当前绑定账号的摘要、站点资料、排序、状态、创建时间和更新时间。来源记录友链最初由博客提交还是后台创建。

例如 `GET /api/v1/admin/links?applicantId=12&status=1` 查询账号 `12` 当前展示中的本人申请。多个筛选条件同时匹配，列表与总数使用相同条件。未声明、重复、空白或非法参数返回 `400/VALIDATION_ERROR`，合法的账号 ID 无匹配时返回 `200` 空页。

账号摘要随友链查询返回，沿用 `blog:link:query` 权限。通过[用户列表](#api-16)选择账号或通过[用户详情](#api-17)查看完整账号资料需要 `sys:user:query`。具有友链创建或编辑权限时，可直接用已知账号 ID 绑定。

---

<a id="api-72"></a>

### 1.11.2 查询友链详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/links/{linkId}` |
| 接口描述 | 查询友链详情 |
| Content-Type | 无请求体 |
| 访问要求 | 使用后台登录会话，账号启用且具有 `admin:access` 和 `blog:link:query`；全局查询，SELF 不缩小范围 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `linkId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
GET /api/v1/admin/links/1
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data` 为 [AdminLink](#response-admin-link)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
X-Revision: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "linkId": "1", "linkName": "示例友站", "url": "https://example.com", "avatar": null, "description": null,
    "sortOrder": 0, "status": 1,
    "createTime": "2026-10-29T02:00:00.000Z", "updateTime": "2026-10-29T02:00:00.000Z",
    "source": "ADMIN", "applicant": {"userId": "12", "nickname": "落叶"}
  },
  "errors": []
}
```

任一状态、任一来源的友链均可查看。详情与 `X-Revision` 在同一只读事务中读取，ID 为十进制字符串，时间为 UTC 毫秒格式。版本由 `linkId`、`linkName`、`url`、`avatar`、`description`、`sortOrder`、`status` 和 `applicantId` 计算。

管理列表提供“详情”按钮，只需查询权限，不要求编辑或删除权限。点击后请求本接口，在只读弹窗展示最新资料；支持加载提示、错误重试、关闭时取消请求，不使用列表行或上一次结果替代当前详情。描述按文本展示，可空字段显示未填写；友链头像复用图片预览组件，地址只展示，不由服务端抓取。

#### 错误响应

| HTTP 状态 | code | 说明 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | ID 非合法十进制正整数或超出 Long 范围，或请求包含查询参数、请求体 |
| 401 | `UNAUTHENTICATED` | 未登录或会话失效 |
| 403 | `ACCOUNT_DISABLED` | 当前操作者账号已禁用 |
| 403 | `FORBIDDEN` | 缺少后台入口或本接口查询权限，或使用非后台登录会话 |
| 404 | `NOT_FOUND` | 目标友链不存在 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库或会话服务不可用 |

---

<a id="api-73"></a>

### 1.11.3 创建友链

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
| `url` | string | 是 | 绝对 HTTP(S) URL，最长 2048 字符，不要求唯一 |
| `avatar` | string&#124;null | 否 | 公开 HTTP(S) 图片地址，最长 2048 字符，默认 null |
| `description` | string&#124;null | 否 | 纯文本，最长 255 字符，默认 null |
| `sortOrder` | integer | 否 | int32，默认 0 |
| `status` | integer | 否 | 0 待审、1 展示、2 驳回/下架，默认 0 |
| `applicantId` | ID&#124;null | 否 | 绑定为该用户的本人申请，须为启用账号。省略或传 null 表示未绑定 |

#### 请求示例

```http
POST /api/v1/admin/links
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
Content-Type: application/json

{"linkName":"示例友站","url":"https://example.com","avatar":null,"description":null,"sortOrder":0,"status":0,"applicantId":"12"}
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
    "sortOrder": 0, "status": 0,
    "createTime": "2026-10-29T02:00:00.000Z", "updateTime": "2026-10-29T02:00:00.000Z"
  },
  "errors": []
}
```

后台创建的友链来源为 `ADMIN`。绑定后，目标用户可在博客查询和撤回这条本人申请，并占用一条申请名额。后台绑定不扣减每日提交次数。目标用户已有申请时返回 `409/STATE_CONFLICT`。账号 ID 格式无效、账号不存在或已禁用，或传入 `source`、`applicant` 等未声明字段时返回 `400/VALIDATION_ERROR`。名称和 URL 可以重复。创建后可通过 `Location` 查询完整详情并取得版本。

---

<a id="api-74"></a>

### 1.11.4 修改友链资料

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/links/{linkId}` |
| 接口描述 | 修改友链资料 |
| Content-Type | application/json |
| 访问要求 | 后台：`blog:link:update` |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `linkId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `linkName` | string | 是 | 1..100，不要求唯一 |
| `url` | string | 是 | 绝对 HTTP(S) URL，最长 2048 字符，不要求唯一 |
| `avatar` | string&#124;null | 是，可 null | 公开 HTTP(S) 图片地址，最长 2048 字符 |
| `description` | string&#124;null | 是，可 null | 纯文本，最长 255 字符 |
| `sortOrder` | integer | 是 | int32 |
| `applicantId` | ID&#124;null | 否 | 省略保留绑定，传 null 解除绑定，传账号 ID 绑定为该用户的本人申请 |

#### 请求示例

```http
PUT /api/v1/admin/links/1
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
If-Revision: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
Content-Type: application/json

{"linkName":"示例友站","url":"https://example.com","avatar":null,"description":"友站简介","sortOrder":0,"applicantId":"12"}
```

#### 响应参数

`data`：[Link](#response-link)。

| 响应头 | 类型 | 说明 |
| --- | --- | --- |
| `X-Revision`（Header） | string | 当前聚合版本；编辑时原样用于 If-Revision |

#### 响应示例

```http
X-Revision: "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
```
```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "linkId": "1", "linkName": "示例友站", "url": "https://example.com", "avatar": null, "description": "友站简介",
    "sortOrder": 0, "status": 1,
    "createTime": "2026-10-29T02:00:00.000Z", "updateTime": "2026-10-29T02:05:00.000Z"
  },
  "errors": []
}
```

五个资料字段须全部提交，可空字段传 `null` 清空。改绑后，新账号接收本人申请，旧账号失去查询和撤回这条记录的权限。解绑后释放原账号的一条申请名额。编辑保留友链状态和来源，审核使用[批量切换友链状态](#batch-links-status)。

目标账号已有另一条申请时返回 `409/STATE_CONFLICT`，资料和绑定均保持原样。新选账号不存在或已禁用、账号 ID 格式非法，或传入 `status`、`source`、`applicant` 等未声明字段时返回 `400/VALIDATION_ERROR`。保留已绑定的禁用账号时仍可编辑站点资料。

缺少 `If-Revision` 返回 `428/PRECONDITION_REQUIRED`，格式错误返回 `400/VALIDATION_ERROR`，版本已变化返回 `412/REVISION_CONFLICT`，目标已被申请人或其他管理员删除时返回 `404/NOT_FOUND`。成功响应携带当前 `X-Revision`。

---


<a id="admin-group-12"></a>

## 1.12 站点配置

<a id="api-77"></a>

### 1.12.1 查询后台配置列表

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
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：[Config](API.md#response-config) 数组。

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

### 1.12.2 查询配置详情

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
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：[Config](API.md#response-config)。

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

### 1.12.3 修改配置

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
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
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

`data`：[Config](API.md#response-config)。

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

<a id="admin-group-13"></a>

## 1.13 文件管理

<a id="api-80"></a>

### 1.13.1 分页查询文件元数据

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
| `keyword` | string | 否 | 可选 S(255)，匹配原始文件名或存储文件名中的字面内容，不按 SQL 通配符解释 |
| `uploaderId` | ID | 否 | 可选，上传者筛选，不扩大 SELF |
| `mimeType` | string | 否 | 可选 ≤100，按已确认 MIME 类型筛选 |

#### 请求示例

```http
GET /api/v1/admin/files?page=1&pageSize=10
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [File](#response-file)；分页字段见 [0.2](API.md#pagination)。

#### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "fileId": "42", "uploaderId": "12", "originalName": "leaf.png", "storedName": "P7m4fb3Wj3svuNzY.png", "mimeType": "image/png",
        "sizeBytes": "2048", "storageType": "local", "createTime": "2026-11-14T03:14:00.000Z",
        "previewUrl": "/api/v1/admin/files/42/content", "publicImage": false, "usage": "ARTICLE_COVER", "usages": ["ARTICLE_COVER"]
      }
    ]
  },
  "errors": []
}
```

SELF 仅本人；createTime DESC,fileId DESC。keyword 按 originalName 或 storedName 字面包含，mimeType 按服务端确认的 MIME 精确匹配；uploaderId 筛选不能扩大 SELF。

---

<a id="api-81"></a>

### 1.13.2 查询文件元数据

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
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：[File](#response-file)。

#### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "fileId": "42", "uploaderId": "12", "originalName": "leaf.png", "storedName": "P7m4fb3Wj3svuNzY.png", "mimeType": "image/png",
    "sizeBytes": "2048", "storageType": "local", "createTime": "2026-11-14T03:14:00.000Z",
    "previewUrl": "/api/v1/admin/files/42/content", "publicImage": false, "usage": "ARTICLE_COVER", "usages": ["ARTICLE_COVER"]
  },
  "errors": []
}
```

元数据不可修改，不返回 X-Revision；previewUrl 为需鉴权的后台路径。

---

<a id="api-82"></a>

### 1.13.3 上传普通附件

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/files` |
| 接口描述 | 上传图片、音频或歌词附件 |
| Content-Type | multipart/form-data |
| 访问要求 | 后台：`blog:file:upload` |
| 成功状态 | 201 Created |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | binary | 是 | 唯一文件字段，非空；PNG/JPEG≤10MiB、MP3/WAV/OGG/FLAC≤60MiB、UTF-8 LRC/TXT/JSON≤1MiB，不收路径/上传者 |
| `usage` | string | 否 | 单个 multipart 文本字段，使用 [File 用途代码](#response-file)。省略时按文件类型填写 |

#### 请求示例

```http
POST /api/v1/admin/files
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
Content-Type: multipart/form-data; boundary=example

--example
Content-Disposition: form-data; name="file"; filename="leaf.png"
Content-Type: image/png

<PNG binary>
--example
Content-Disposition: form-data; name="usage"

IMAGE
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
    "fileId": "42", "uploaderId": "12", "originalName": "leaf.png", "storedName": "P7m4fb3Wj3svuNzY.png", "mimeType": "image/png",
    "sizeBytes": "2048", "storageType": "oss", "createTime": "2026-11-14T03:14:00.000Z",
    "previewUrl": "/api/v1/admin/files/42/content", "publicImage": false, "usage": "IMAGE", "usages": ["IMAGE"]
  },
  "errors": []
}
```

单文件非空：PNG/JPEG≤10MiB、MP3/WAV/OGG/FLAC≤60MiB、UTF-8 LRC/TXT/JSON≤1MiB；multipart≤61MiB，内容识别与扩展名一致；存储和失败补偿见[文件开发约定](API_CONTRACT.md#file-consistency)。新上传默认启用服务端压缩：目标 500KiB、JPEG 起始质量 0.9、最长边 2560px；保留 PNG/JPEG 格式及 PNG 透明通道，仅采用更小的产物。sizeBytes 和内容响应长度对应实际存储字节，不保留压缩前副本；JPEG 可能有损，关闭压缩可保留原字节。目标大小不是硬上限，PNG 无损编码及 JPEG 最低质量 0.3 可能无法达到目标；压缩结果不更小时沿用原字节。每个请求按用户和可信 IP 共用每 60 秒 10 次窗口，仅计一次；超限 429/RATE_LIMITED 并返回 Retry-After。当前默认启用OSS，新上传为oss；需由环境提供OSS凭据，缺失时启动失败，旧对象不迁移，响应不返回直链。

---

<a id="api-83"></a>

### 1.13.4 预览后台原文件

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
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

| 字段/响应头 | 类型 | 说明 |
| --- | --- | --- |
| 响应体 | binary | 已存储文件字节（图片可能已压缩），不是 Result；支持 Range/206/416 |
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

需要文件查询权限及其范围；前端通过同源请求读取后使用临时 object URL，不把 Token 拼到 img URL。

---

<a id="display-image-upload"></a>

### 1.13.5 上传公开展示图片

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/files/display-images` |
| 访问要求 | 后台 `blog:file:upload`，与普通上传共用限流 |
| 请求 | `multipart/form-data`，一个 `file`（非空 PNG/JPEG，最多 10 MiB），可选一个 `usage` 文本字段（图片用途，省略时为 `IMAGE`） |
| 响应 | 201 Created，`data` 和 `Location` 为绝对 HTTP(S) 图片地址，使用 `app.sites.api-base-url` 生成 |

上传成功即可匿名读取。后台用返回地址填入头像、Logo 或图标字段，保存表单后应用到对应页面。文件管理显示上传时保存的用途。部署时 `app.sites.api-base-url` 对应的反代须转发 `/api/v1/blog/display-images/`，使公开站点能加载图片。OSS 对象仍为私有。

---


<a id="admin-group-14"></a>

## 1.14 站长资料与社交链接

<a id="api-89"></a>

### 1.14.1 查询后台站长资料

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/home/profile` |
| 接口描述 | 查询后台站长资料 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`site:ownerProfile:query`（ALL） |
| 并发校验 | 返回 X-Revision |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
GET /api/v1/admin/home/profile
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：[SiteOwnerProfile](#response-homeprofile)，未配置/无记录时可为 null。

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
    "profileId": "1", "nickname": "落叶", "tagline": "记录与分享", "description": null,
    "avatar": null
  },
  "errors": []
}
```

固定profileId=1。展示资料与登录资料独立，只有publicEmail可公开；禁止从sys_user.email自动补全。头像和URL遵守公开资源约束。未配置时data=null，仍返回代表空资源的X-Revision。

---

<a id="api-90"></a>

### 1.14.2 设置站长资料

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/home/profile` |
| 接口描述 | 设置站长资料 |
| Content-Type | application/json |
| 访问要求 | 后台：`site:ownerProfile:update`（ALL） |
| 并发校验 | 必填 If-Revision |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `If-Revision` | string | 是 | Header；原样携带详情 X-Revision |
| `nickname` | S(64) | 是 | 公开昵称 |
| `tagline` | S0(100) | 是 | 个人标签或一句话介绍 |
| `description` | S(500)? | 是，可null | 公开简介，null清空 |
| `avatar` | URL? | 是，可null | 公开头像，HTTP(S) 图片地址 |
| `website` | URL? | 是，可null | 个人网站 |
| `publicEmail` | string&#124;null | 是，可null | ≤254，合法联系邮箱；明确自愿公开，不自动读取登录邮箱 |
| `githubUrl` | URL? | 是，可null | GitHub主页 |
| `location` | S(100)? | 是，可null | 自愿公开的所在地 |

#### 请求示例

```http
PUT /api/v1/admin/home/profile
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "nickname": "落叶", "tagline": "记录与分享", "description": null, "avatar": null,
  "website": "https://example.test", "publicEmail": null, "githubUrl": null, "location": null
}
```

#### 响应参数

`data`：[SiteOwnerProfile](#response-homeprofile)。

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
    "profileId": "1", "nickname": "落叶", "tagline": "记录与分享", "description": null,
    "avatar": null
  },
  "errors": []
}
```

固定profileId=1。展示资料与登录资料独立，只有publicEmail可公开；禁止从sys_user.email自动补全。头像和URL遵守公开资源约束。首次设置也通过本PUT，先GET取得空资源版本；在同一事务内检查版本，并创建或完整替换资料，始终200。

---

<a id="api-92"></a>

### 1.14.3 查询后台社交链接

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
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [Social](#response-social)；分页字段见 [0.2](API.md#pagination)。

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

### 1.14.4 查询社交链接详情

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
Cookie: __Host-dl-admin-session=<opaque>
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

### 1.14.5 创建社交链接

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
| `icon` | string&#124;null | 是，可null | ≤2048；公开 HTTP(S) 图片地址或前端注册图标键，禁止任意HTML |
| `url` | URL | 是 | 外部HTTP(S)目标链接，邮箱链接可使用经过校验的mailto:邮箱 |
| `sortOrder` | int32 | 是 | 越小越靠前 |
| `status` | integer | 是 | 0隐藏 1展示 |

#### 请求示例

```http
POST /api/v1/admin/home/social-links
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
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

### 1.14.6 修改社交链接

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
| `icon` | string&#124;null | 是，可null | ≤2048；公开 HTTP(S) 图片地址或前端注册图标键，禁止任意HTML |
| `url` | URL | 是 | 外部HTTP(S)目标链接，邮箱链接可使用经过校验的mailto:邮箱 |
| `sortOrder` | int32 | 是 | 越小越靠前 |

#### 请求示例

```http
PUT /api/v1/admin/home/social-links/21
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"name":"GitHub","icon":"github","url":"https://github.com/example","sortOrder":0}
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


本接口只修改资料，保留当前 status。状态统一使用[批量切换社交链接状态](#batch-home-social-links-status)，单个目标也提交一项 items。创建时仍可指定初始状态。

---


<a id="admin-group-15"></a>

## 1.15 简历经历与技能

<a id="api-98"></a>

### 1.15.1 查询后台简历经历

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
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [Experience](#response-experience)；分页字段见 [0.2](API.md#pagination)。

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

### 1.15.2 查询简历经历详情

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
Cookie: __Host-dl-admin-session=<opaque>
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

### 1.15.3 创建简历经历

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
| `logoUrl` | URL? | 是，可null | 公开 HTTP(S) 图片地址 |
| `content` | string | 是 | 非空Markdown，UTF-8最多65535字节，与MySQL TEXT容量一致 |
| `startDate` | date | 是 | 合法YYYY-MM-DD，1000..9999年 |
| `endDate` | date&#124;null | 是，可null | 结束日期不得早于开始；null表示至今 |
| `sortOrder` | int32 | 是 | 越小越靠前 |
| `status` | integer | 是 | 0隐藏 1展示 |

#### 请求示例

```http
POST /api/v1/admin/cv/experiences
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
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

### 1.15.4 修改简历经历

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
| `logoUrl` | URL? | 是，可null | 公开 HTTP(S) 图片地址 |
| `content` | string | 是 | 非空Markdown，UTF-8最多65535字节，与MySQL TEXT容量一致 |
| `startDate` | date | 是 | 合法YYYY-MM-DD，1000..9999年 |
| `endDate` | date&#124;null | 是，可null | 结束日期不得早于开始；null表示至今 |
| `sortOrder` | int32 | 是 | 越小越靠前 |

#### 请求示例

```http
PUT /api/v1/admin/cv/experiences/31
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "experienceType": 0, "title": "示例大学", "subtitle": "计算机科学", "logoUrl": null, "content": "学习与项目实践",
  "startDate": "2022-09-01", "endDate": null, "sortOrder": 0
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


本接口只修改资料，保留当前 status。状态统一使用[批量切换简历经历状态](#batch-cv-experiences-status)，单个目标也提交一项 items。创建时仍可指定初始状态。

---


<a id="api-104"></a>

### 1.15.6 查询后台简历技能

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
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [Skill](#response-skill)；分页字段见 [0.2](API.md#pagination)。

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

### 1.15.7 查询技能详情

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
Cookie: __Host-dl-admin-session=<opaque>
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

### 1.15.8 创建简历技能

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
| `icon` | string&#124;null | 是，可null | ≤2048；公开 HTTP(S) 图片地址或注册图标键 |
| `sortOrder` | int32 | 是 | 越小越靠前 |
| `status` | integer | 是 | 0隐藏 1展示 |

#### 请求示例

```http
POST /api/v1/admin/cv/skills
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
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

### 1.15.9 修改简历技能

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
| `icon` | string&#124;null | 是，可null | ≤2048；公开 HTTP(S) 图片地址或注册图标键 |
| `sortOrder` | int32 | 是 | 越小越靠前 |

#### 请求示例

```http
PUT /api/v1/admin/cv/skills/41
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"name":"Java","description":"后端开发","icon":null,"sortOrder":0}
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

完整替换。图标使用白名单键或公开 HTTP(S) 图片地址，不接受任意SVG/HTML。


本接口只修改资料，保留当前 status。状态统一使用[批量切换技能状态](#batch-cv-skills-status)，单个目标也提交一项 items。创建时仍可指定初始状态。

---


<a id="admin-group-16"></a>

## 1.16 背景音乐

<a id="api-121"></a>

### 1.16.1 分页查询后台音乐

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
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [Music](#response-music)；分页字段见 [0.2](API.md#pagination)。

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

### 1.16.2 查询音乐详情

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
Cookie: __Host-dl-admin-session=<opaque>
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

### 1.16.3 创建音乐

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
| `coverUrl` | string? | 是，可null | 外部HTTP(S)封面或规范音乐文件路径 |
| `audioUrl` | string | 是 | 外部HTTP(S)音频或规范音乐文件路径；外链保存不触发下载 |
| `lyricUrl` | string? | 是，可null | 外部HTTP(S)歌词或规范音乐文件路径；无歌词时为null |
| `lyricType` | string&#124;null | 是，可null | lrc/json/txt；必须与lyricUrl同时为空或同时非空 |
| `sortOrder` | int32 | 是 | 越小越靠前 |
| `status` | integer | 是 | 0隐藏 1展示 |

#### 请求示例

```http
POST /api/v1/admin/music
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
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

### 1.16.4 修改音乐

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
| `coverUrl` | string? | 是，可null | 外部HTTP(S)封面或规范音乐文件路径 |
| `audioUrl` | string | 是 | 外部HTTP(S)音频或规范音乐文件路径；外链保存不触发下载 |
| `lyricUrl` | string? | 是，可null | 外部HTTP(S)歌词或规范音乐文件路径；无歌词时为null |
| `lyricType` | string&#124;null | 是，可null | lrc/json/txt；必须与lyricUrl同时为空或同时非空 |
| `sortOrder` | int32 | 是 | 越小越靠前 |

#### 请求示例

```http
PUT /api/v1/admin/music/51
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{
  "title": "秋日", "artist": null, "durationSeconds": 180, "coverUrl": null,
  "audioUrl": "https://example.test/audio.mp3", "lyricUrl": null, "lyricType": null, "sortOrder": 0
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


本接口只修改资料，保留当前 status。状态统一使用[批量切换音乐状态](#batch-music-status)，单个目标也提交一项 items。创建时仍可指定初始状态。

---


<a id="admin-group-17"></a>

## 1.17 邮件订阅管理

<a id="api-131"></a>

### 1.17.1 分页查询后台邮件订阅

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
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [SubscriptionAdmin](#response-subscriptionadmin)；分页字段见 [0.2](API.md#pagination)。

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

### 1.17.2 查询邮件订阅详情

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
Cookie: __Host-dl-admin-session=<opaque>
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



<a id="admin-group-18"></a>

## 1.18 访客管理

<a id="api-138"></a>

### 1.18.1 分页查询访客

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
| `isBlocked` | integer | 否 | 可选0/1，按当前时刻是否处于生效中的封禁筛选 |

#### 请求示例

```http
GET /api/v1/admin/visitors?page=1&pageSize=10
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [Visitor](#response-visitor)；分页字段见 [0.2](API.md#pagination)。

#### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "visitorId": "501", "fingerprint": "d681988a-cbd0-4ef6-bdec-f84bfa234e16", "ipAddress": "192.0.2.1",
        "userAgent": null, "country": "中国", "province": "湖南省", "city": "长沙市",
        "totalViews": "24", "isBlocked": 0, "blockedUntil": null, "blockedIpAddress": null,
        "firstVisitTime": "2026-10-28T00:22:00.000Z", "lastVisitTime": "2026-11-01T12:53:00.000Z"
      }
    ]
  },
  "errors": []
}
```

需ALL；lastVisitTime DESC,visitorId DESC。仅后台返回统计标识、IP与UA，不公开原始访客清单。`totalViews` 为该访客在所有站点当前保留的浏览明细条数，以十进制字符串返回，无明细为 `"0"`；删除明细后减少，不是独立维护的历史累计值。列表按 IP、国家、省份、城市、浏览次数、状态、首次访问、最近访问和封禁到期展示，保留详情与管理操作；限时封禁到期后按正常状态返回（`isBlocked=0`、`blockedUntil=null`、`blockedIpAddress=null`），访客再次访问时同步清理数据库中的过期封禁字段。

---

<a id="api-139"></a>

### 1.18.2 查询访客详情

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
Cookie: __Host-dl-admin-session=<opaque>
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
    "userAgent": null, "country": "中国", "province": "湖南省", "city": "长沙市",
    "totalViews": "24", "isBlocked": 0, "blockedUntil": null, "blockedIpAddress": null,
    "firstVisitTime": "2026-10-28T00:22:00.000Z", "lastVisitTime": "2026-11-01T12:53:00.000Z"
  },
  "errors": []
}
```

X-Revision 覆盖封禁标记、到期时间和封禁目标。未保存封禁 IP 时，最近访问 IP 变化会使版本过期；浏览次数、访问时间及 UA 不参与版本。

---




<a id="admin-group-19"></a>

## 1.19 浏览记录管理

<a id="api-143"></a>

### 1.19.1 分页查询浏览记录

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
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [PageView](#response-pageview)；分页字段见 [0.2](API.md#pagination)。

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

需ALL；筛选AND组合，begin<end；viewTime DESC,viewId DESC，不输出任何认证凭据。管理列表按页面标题、页面路径、访客 IP、来源和浏览时间展示；详情继续使用当前行的完整 PageView 数据，保留站点、记录/访客/用户/文章 ID 与 User-Agent，无需新增详情接口。

---


<a id="admin-group-20"></a>

## 1.20 统计报表

<a id="api-146"></a>

### 1.20.1 查询后台统计总览

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
Cookie: __Host-dl-admin-session=<opaque>
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

### 1.20.2 查询浏览趋势

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
Cookie: __Host-dl-admin-session=<opaque>
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

### 1.20.3 查询新访客趋势

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
Cookie: __Host-dl-admin-session=<opaque>
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

### 1.20.4 查询访客省份分布

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
Cookie: __Host-dl-admin-session=<opaque>
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

### 1.20.5 查询文章访问排行

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
Cookie: __Host-dl-admin-session=<opaque>
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

需ALL；只统计blog站点且articleId非NULL的明细，排除已回收文章；允许含当前草稿/私密文章的历史访问记录，仅后台可见。pageViewCount DESC,articleId DESC。该排行表示区间浏览明细，区别于[博客端累计热度榜](API_BLOG.md#api-87)。

---

### 1.20.6 查询访问来源排行

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/admin/statistics/referers` |
| 接口描述 | 按来源域名查询访问排行 |
| Content-Type | 无请求体 |
| 访问要求 | 后台：`blog:statistics:query`（ALL） |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `begin` | date | 否 | 可选；与end同时出现；UTC日期含下界 |
| `end` | date | 否 | 可选；UTC日期含当日，最多367天，end不得早于begin |
| `siteCode` | string | 否 | 可选：`home` / `blog` / `cv` |
| `limit` | integer | 否 | 默认10，1..100 |

#### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": [
    {
      "source": "driftingleaves.xyz", "pageViewCount": "12", "visitorCount": "8"
    },
    {
      "source": "", "pageViewCount": "5", "visitorCount": "4"
    }
  ],
  "errors": []
}
```

需ALL；按来源域名聚合统计指定时间段与站点范围内的浏览量与独立访客数，无来源记录归入 `source=""`（前端展示为「直接访问」）；按 `pageViewCount DESC, source ASC` 排序。

---

<a id="admin-group-21"></a>

## 1.21 操作日志

<a id="api-151"></a>

### 1.21.1 分页查询操作日志

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
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [OperationLog](#response-operationlog)；分页字段见 [0.2](API.md#pagination)。

#### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "logId": "701", "userId": "12", "operationType": "update", "operationTarget": "home:profile",
        "targetId": "1", "permissionCode": "site:ownerProfile:update"
      }
    ]
  },
  "errors": []
}
```

筛选AND组合；begin<end；operationTime DESC,logId DESC。列表展示 logId（ID 列）和中文操作类型标签；operationType 继续返回原始 ASCII 动作值并用于精确筛选，不改写历史日志。共用批量接口的操作在选中一条时记录单条类型，选中多条时记录 `batch-*`。列表和详情按中文名称展示，已有 `batch-*` 日志在 `selectedCount` 为 `"1"` 时显示对应的单条名称，未知动作原样展示。列表不展开operationData，详情向具有sys:log:query及ALL范围的管理员展示已记录的完整业务字段。

---

<a id="api-152"></a>

### 1.21.2 查询操作日志详情

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
Cookie: __Host-dl-admin-session=<opaque>
```

#### 响应参数

`data`：[OperationLogDetail](#response-operationlogdetail)。

#### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "logId": "701", "userId": "12", "operationType": "update", "operationTarget": "home:profile",
    "targetId": "1", "permissionCode": "site:ownerProfile:update"
  },
  "errors": []
}
```

日志不可编辑，无X-Revision；不返回原始HTTP请求体、异常堆栈、SQL或任何凭据。

---


<a id="admin-batch"></a>

## 1.22 后台统一操作（单条与批量）

本节共 38 个统一入口：原有 26 项替换对应旧单条管理写路由，另新增用户批量启用、禁用和删除 3 项，以及分类状态 1 项、其他资源状态 8 项。一次提交 1 项即为单条操作，多项即为批量操作，使用相同的权限、版本、事务与结果协议。详情查询、创建、完整编辑、用户角色/角色 grants、本人设置及本人互动接口不在合并范围。源码依据与验收要求见[统一操作开发约定](API_CONTRACT.md#batch-contract)。

### 公共请求与结果

全部入口要求有效后台会话、启用账号、`admin:access` 和各接口列出的业务权限，使用 `Content-Type: application/json`、`Cache-Control: no-store`。不接受 Query、通用 action、筛选条件或“全选所有查询结果”；仅操作 Body 中明确选中的同类 ID。删除使用 POST 动作路径，避免在 DELETE 中携带批量 JSON；对应的旧单条管理写入口已移除，单个目标也使用此协议。

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
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
Content-Type: application/json

{"items":[{"id":"301","revision":"\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\""},{"id":"302","revision":"\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\""}],"status":1}
```

```json
{"code":"OK","msg":"成功","data":{"items":[{"id":"301","revision":"\"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\""},{"id":"302","revision":"\"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd\""}]},"errors":[]}
```

原删除业务要求版本的资源使用如下请求，不附加 status：

```http
POST /api/v1/admin/categories/batch-delete
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
Content-Type: application/json

{"items":[{"id":"101","revision":"\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\""}]}
```

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

不要求版本的分支清理使用 ID 数组；同时选中父子节点时按分支并集清理，具体规则见对应接口：

```http
POST /api/v1/admin/messages/batch-delete
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
Content-Type: application/json

{"ids":["401","402"]}
```

批量封禁沿用 V2 的明确到期时间，不沿用老项目固定封禁一天的默认值：

```http
PUT /api/v1/admin/visitors/batch-block
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
Content-Type: application/json

{"items":[{"id":"501","revision":"\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\""}],"blockedUntil":null}
```

普通整批错误示例：HTTP 412，全部数据库写入回滚，不自动获取新版本重试：

```json
{"code":"REVISION_CONFLICT","msg":"资料已修改，请重新获取","data":null,"errors":[]}
```

所有入口均沿用[错误码](API.md#errors)：未认证 401，无权限或缺少规定的 ALL 为 403，不存在/范围外 404，状态或引用冲突 409，旧版本 412，缺版本 428，请求超限 413，限流 429，数据库/Redis/存储不可用 503；未预期异常 500。权限检查不因目标已处于同状态而省略。超时或响应丢失先重新查询确认；删除后重放返回 404，旧版本重放返回 412，不承诺通用幂等键。

<a id="api-44"></a>

<a id="batch-article-status"></a>

### 1.22.1 批量发布或下架文章

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/articles/batch-status` |
| 业务权限 | `blog:article:publish`，逐篇检查 ALL/SELF |
| 请求 → 响应 | `BatchStatusRequest`（status=1 发布、2 下架）→ `BatchVersions` |

逐篇继承[既有业务规则](API_CONTRACT.md#model-12-4)的正文、来源、关联和文件可用性校验。回收站文章不得操作；草稿不能直接下架。首次发布各自写服务端发布时间，重发保留原首次时间；首次公开发布通知仅在整批提交后触发，不因同状态重放重复发送。

<a id="api-45"></a>

<a id="batch-article-recycle"></a>

### 1.22.2 批量将文章移入回收站

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/articles/batch-recycle` |
| 业务权限 | `blog:article:delete`，逐篇检查 ALL/SELF |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

继承[既有业务规则](API_CONTRACT.md#model-12-4)，仅设置 deletedAt，保留原状态、正文、标签、slug、评论和点赞，不删除文件。同目标已回收的处理沿用单条接口，仍先校验版本，不扩大为永久删除。

<a id="api-46"></a>

<a id="batch-article-restore"></a>

### 1.22.3 批量恢复回收站文章

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/articles/batch-restore` |
| 业务权限 | `blog:article:restore`，逐篇检查 ALL/SELF |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchVersions` |

全部目标必须在回收站，否则整批 409。继承[既有业务规则](API_CONTRACT.md#model-12-4)，统一恢复为下架 status=2、deletedAt=null，保留首次发布时间，不自动公开或发送发布通知。

<a id="api-159"></a>

<a id="batch-article-permanent"></a>

### 1.22.4 批量永久删除回收站文章

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/articles/batch-permanent-delete` |
| 业务权限 | 同时具备 `blog:article:delete`、`blog:article:purge`；两项范围分别逐篇检查 |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

全部目标须先回收，不能混入正常文章。按[既有业务规则](API_CONTRACT.md#model-12-4)清理正文、标签关联、点赞及完整评论树；保留分类/标签字典、文件对象、历史审计和浏览明细，浏览关联 articleId 置 null。整批不可恢复，不能改成循环提交多个永久删除请求。

<a id="api-52"></a>

<a id="batch-category-delete"></a>

### 1.22.5 批量删除分类

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/categories/batch-delete` |
| 业务权限 | `blog:category:delete`；全局字典，沿用原权限规则 |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

按[既有业务规则](API_CONTRACT.md#model-12-5)将所选分类下文章的 categoryId 置 null，不删除文章；关联变化使文章业务版本失效，并在整批提交后清理依赖分类的公开缓存。

<a id="api-58"></a>

<a id="batch-tag-delete"></a>

### 1.22.6 批量删除标签

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/tags/batch-delete` |
| 业务权限 | `blog:tag:delete`；全局字典，沿用原权限规则 |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

按[既有业务规则](API_CONTRACT.md#model-12-5)清理相应文章标签关联，不删除文章或其他标签；关联集合变化使文章业务版本失效，缓存清理覆盖文章列表/详情等原有依赖。

<a id="api-65"></a>

<a id="batch-comment-review"></a>

### 1.22.7 批量审核评论

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/comments/batch-review` |
| 业务权限 | `blog:comment:review`；SELF 按每条评论所属文章的 authorId 检查 |
| 请求 → 响应 | `BatchStatusRequest`（status=1 通过、2 驳回）→ `BatchVersions` |

继承[既有业务规则](API_CONTRACT.md#model-12-6)，只改显式选中的节点，不递归审核后代。审核通过子节点不自动通过祖先，也不绕过公开父链条件；驳回父节点会隐藏整条分支，后代自身状态保持。

<a id="api-66"></a>

<a id="batch-comment-delete"></a>

### 1.22.8 批量删除评论分支

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/comments/batch-delete` |
| 业务权限 | `blog:comment:moderate`；SELF 按每个目标所属文章的 authorId 检查 |
| 请求 → 响应 | `BatchIdsRequest` → `BatchDeleted` |

不要求版本，按[既有业务规则](API_CONTRACT.md#model-12-6)物理清理目标及任意作者、任意审核状态的全部层级回复，不生成占位。先核验所有显式 ID 存在且可访问，再求分支并集；父子同时选中不因先删父节点导致后续误报 404，重叠节点只清理一次。未选中的祖先和兄弟保留。

<a id="api-117"></a>

<a id="batch-message-review"></a>

### 1.22.9 批量审核留言

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/messages/batch-review` |
| 业务权限 | `blog:message:review`（ALL） |
| 请求 → 响应 | `BatchStatusRequest`（status=1 通过、2 驳回）→ `BatchVersions` |

继承[既有业务规则](API_CONTRACT.md#model-12-10)的完整父链与私密性规则，占位不可复活；只变更所选节点。通过校验基于整批拟提交的最终状态：被选中且本次通过的祖先可满足子节点条件，未选中的隐藏祖先仍导致整批 409，不由请求数组顺序决定结果。实际从非通过变为通过的节点在整批提交后通知，不泄露悄悄话原文。

<a id="api-118"></a>

<a id="batch-message-delete"></a>

### 1.22.10 批量删除留言分支

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/messages/batch-delete` |
| 业务权限 | `blog:message:moderate`（ALL） |
| 请求 → 响应 | `BatchIdsRequest` → `BatchDeleted` |

不要求版本，继承[既有业务规则](API_CONTRACT.md#model-12-10)，物理清理目标与全部后代，含悄悄话、其他作者回复。全部显式 ID 先校验，再对重叠分支求并集并按子到父清理；任一原始目标不存在整批 404。关闭留言开关不阻止历史清理。

<a id="api-75"></a>

<a id="batch-link-delete"></a>

### 1.22.11 批量删除友链

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/links/batch-delete` |
| 业务权限 | `blog:link:delete`；全局资源，SELF 授权也可操作全部目标 |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

可物理删除任一状态的友链。若友链已绑定账号，[本人申请查询](API_BLOG.md#link-application-get)随即不再返回该申请。申请名额释放，当日已用次数保留，申请人可在剩余额度内重新提交。展示中的记录从公开列表移除，外部站点和图标资源不受影响。

按[批量公共约定](#admin-batch)提交每条详情的版本，任一目标已删除或版本不符时整批失败。当前后台单条删除按钮检查 `blog:link:delete`，多选删除按钮额外要求 `ALL`。待实现的后台接入需统一两处按钮的权限判断，按接口的全局权限开放。

<a id="api-84"></a>

<a id="batch-file-delete"></a>

### 1.22.12 批量删除文件（逐项提交）

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/files/batch-delete` |
| 业务权限 | `blog:file:delete`，逐项检查上传者 ALL/SELF |
| 请求 → 响应 | `BatchIdsRequest` → `BatchFileResults` |

本接口是唯一的**逐项数据库事务**批量操作，不承诺所有文件共同回滚。请求结构和入口鉴权失败时使用普通错误 Result，尚未执行任何文件删除；开始处理后，按请求顺序逐项返回结果，不以首项错误掩盖后续状态。已完成的成功项不回滚，单项业务/存储失败后继续检查其余项；身份或权限失效后不再写入，剩余项分别记相应 401/403，仍保留每个请求 ID 的结果。

逐项继承[既有业务规则](API_CONTRACT.md#model-12-8)的锁、引用检查和失败恢复：任何文章（含草稿、私密、回收站）仍引用时为 409/RESOURCE_IN_USE；不存在/SELF 范围外为 404；存储失败为 503 并保留元数据。对象已删除但数据库提交失败时仍可能暂留元数据，沿用单条重试清理规则，不声称恢复了文件对象。不要求版本，不输出物理路径、对象键或引用文章的敏感信息。

```http
POST /api/v1/admin/files/batch-delete
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
Content-Type: application/json

{"ids":["601","602"]}
```

```json
{"code":"OK","msg":"成功","data":{"results":[{"id":"601","httpStatus":200,"code":"OK","msg":"成功"},{"id":"602","httpStatus":409,"code":"RESOURCE_IN_USE","msg":"文件仍被文章引用"}]},"errors":[]}
```

请求超时或连接中断可能拿不到已完成项结果，先按原文件详情确认，不盲目重发全部 ID，也不能把重试时的 404 当成证明上次全部成功。

<a id="api-96"></a>

<a id="batch-social-delete"></a>

### 1.22.13 批量删除社交链接

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/home/social-links/batch-delete` |
| 业务权限 | `home:social:delete`（ALL） |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

继承[既有业务规则](API_CONTRACT.md#model-12-9)，提交后清理各公开端共享的社交链接缓存；不修改站长资料，不删除外部图标。

<a id="api-102"></a>

<a id="batch-experience-delete"></a>

### 1.22.14 批量删除简历经历

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/cv/experiences/batch-delete` |
| 业务权限 | `cv:experience:delete`（ALL） |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

继承[既有业务规则](API_CONTRACT.md#model-12-9)，允许混选教育、工作和项目类型，提交后清理经历缓存，不修改其他经历或主人资料。

<a id="api-108"></a>

<a id="batch-skill-delete"></a>

### 1.22.15 批量删除简历技能

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/cv/skills/batch-delete` |
| 业务权限 | `cv:skill:delete`（ALL） |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

继承[既有业务规则](API_CONTRACT.md#model-12-9)，提交后清理公开技能缓存，不删除外部图标或改写其他技能排序。

<a id="api-125"></a>

<a id="batch-music-delete"></a>

### 1.22.16 批量删除音乐

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/music/batch-delete` |
| 业务权限 | `blog:music:delete`（ALL） |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

继承[既有业务规则](API_CONTRACT.md#model-12-11)，只删除曲目和歌词元数据，清理播放列表缓存，不下载或删除外部音频、歌词与封面。

<a id="api-133"></a>

<a id="batch-subscription-status"></a>

### 1.22.17 批量管理邮件订阅状态

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/rss-subscriptions/batch-status` |
| 业务权限 | `blog:rss:update`（ALL） |
| 请求 → 响应 | `BatchStatusRequest`（status=1 激活、2 退订）→ `BatchVersions` |

继承[既有业务规则](API_CONTRACT.md#model-12-12)。激活须逐条确认订阅邮箱与当前账号邮箱一致、订阅邮箱已验证、所属账号启用，不补写 verifiedAt、不替换邮箱或触发确认邮件；任一不满足则整批 409。实际再次激活才开始新投递轮次，同状态不重写时间。退订释放邮箱占用，换邮箱时同时清除旧验证。数据库整批回滚不代表撤销 Redis 已完成的轮次变更，失败后重新查询确认。

<a id="api-134"></a>

<a id="batch-subscription-delete"></a>

### 1.22.18 批量删除邮件订阅

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/rss-subscriptions/batch-delete` |
| 业务权限 | `blog:rss:delete`（ALL） |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

继承[既有业务规则](API_CONTRACT.md#model-12-12)，清理记录后旧确认/退订链接不能恢复该记录，不删除本站用户，也不把账号联系邮箱作为已验证订阅邮箱。

<a id="api-140"></a>

<a id="batch-visitor-block"></a>

### 1.22.19 批量封禁访客 IP

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/visitors/batch-block` |
| 业务权限 | `blog:visitor:block`（ALL） |
| 请求 → 响应 | `BatchBlockRequest` → `BatchVersions` |

使用各记录已保存的封禁 IP，首次设置时固定为该记录最近的可信客户端 IP。无可用地址返回 `400/VALIDATION_ERROR`，整批回滚。所选记录使用同一个 `blockedUntil`，明确传 `null` 表示永久。该地址的公开站点 API 返回 `403/ACCESS_BLOCKED`，包括内容读取、评论及留言。旧统计标识封禁维持原有采集限制，直到管理员解封或重新封禁。

<a id="api-141"></a>

<a id="batch-visitor-unblock"></a>

### 1.22.20 批量解除访客封禁

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/visitors/batch-unblock` |
| 业务权限 | `blog:visitor:unblock`（ALL） |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchVersions` |

将所选记录的 `isBlocked` 设为 0，清空 `blockedUntil` 和 `blockedIpAddress`。同一 IP 若还有其他记录上的有效封禁，仍然受限。已解封同样校验版本。

<a id="api-142"></a>

<a id="batch-visitor-delete"></a>

### 1.22.21 批量删除访客统计记录

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/visitors/batch-delete` |
| 业务权限 | `blog:visitor:delete`（ALL） |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

继承[既有业务规则](API_CONTRACT.md#model-12-13)，浏览明细保留且 visitorId 置 null，因此 PV 不变、基于非 null 访客的 UV 可能减少；不删除浏览记录、不减少文章累计阅读、不删除本站用户。同一 fingerprint 后续采集可重新生成访客记录。

记录有生效的 IP 封禁时返回 `409/RESOURCE_IN_USE`，需先解封或等到期。

<a id="api-144"></a>

<a id="batch-page-view-delete"></a>

### 1.22.22 批量清理浏览记录

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/page-views/batch-delete` |
| 业务权限 | `blog:view:delete`（ALL） |
| 请求 → 响应 | `BatchIdsRequest` → `BatchDeleted` |

不要求版本，按[既有业务规则](API_CONTRACT.md#model-12-13)删除所选明细并清理统计缓存；基于保留明细的 PV/UV 及报表随之重算，不反向扣减文章累计 viewCount，不删除访客。

<a id="api-153"></a>

<a id="batch-log-delete"></a>

### 1.22.23 批量清理操作日志

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/operation-logs/batch-delete` |
| 业务权限 | `sys:log:delete`（ALL） |
| 请求 → 响应 | `BatchIdsRequest` → `BatchDeleted` |

不要求版本，沿用[既有业务规则](API_CONTRACT.md#model-12-14)的显式 ID 清理与审计要求；整批清理本身新增一条审计，随事务提交，不被本次目标集合删除，也不递归产生清理日志。不存在按条件清空所有日志或公开创建/编辑日志入口。

<a id="api-25"></a>

<a id="batch-role-delete"></a>

### 1.22.24 批量删除角色

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/roles/batch-delete` |
| 业务权限 | `sys:role:delete`；全局资源，沿用原权限规则 |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

继承[既有业务规则](API_CONTRACT.md#model-12-2)，任何内置 admin/author/member 角色均令整批 409。清理用户角色、角色权限和角色菜单关联，撤销受影响用户会话并清理授权缓存，不删除用户、权限字典或菜单；整批最终结果必须保留有效管理员。撤销失败则数据库整批回滚，但已撤销的会话不恢复。

<a id="api-31"></a>

<a id="batch-permission-delete"></a>

### 1.22.25 批量删除权限字典项

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/permissions/batch-delete` |
| 业务权限 | `sys:permission:delete`；全局资源，沿用原权限规则 |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

继承[既有业务规则](API_CONTRACT.md#model-12-2)，固定后端权限禁止删除（409/STATE_CONFLICT），被任意菜单引用时整批 409/RESOURCE_IN_USE；只级联角色权限关联，不自动删菜单或角色。统一检查整批最终管理员能力，撤销所有受影响用户会话、清理授权缓存；失败不留下部分字典删除。

<a id="api-36"></a>

<a id="batch-menu-delete"></a>

### 1.22.26 批量删除菜单叶子节点

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/menus/batch-delete` |
| 业务权限 | `sys:menu:delete`；全局资源，沿用原权限规则 |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted` |

继承[既有业务规则](API_CONTRACT.md#model-12-3)的叶子限制：以写入前锁定的整棵树判断，任一目标有子节点即整批 409/RESOURCE_IN_USE；即使子节点也在本次集合中，仍须先另批删除子节点，不隐式递归删除目录。只级联角色菜单关联，清理受影响菜单缓存，不删除权限或撤销接口权限。

<a id="batch-user-enable"></a>

### 1.22.27 批量启用用户

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/users/batch-enable` |
| 业务权限 | `sys:user:update`；全局资源 |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchVersions`；HTTP 200 |

只将所选用户 status 改为 1，不修改资料或角色，不恢复已撤销的会话。已启用用户仍须校验版本，状态无变化时保留版本。不接受 status 或其他附加字段。

<a id="batch-user-disable"></a>

### 1.22.28 批量禁用用户

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/users/batch-disable` |
| 业务权限 | `sys:user:update`；全局资源 |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchVersions`；HTTP 200 |

只将所选用户 status 改为 0，不修改资料或角色；实际从启用转为禁用时撤销该账号全部本站会话。同状态也须校验版本。按整批最终状态保护最后有效管理员，违反返回 `409/LAST_ADMIN`，整批回滚。允许选择操作者本人，须在主动撤销会话前完成全部校验及结果版本计算。

<a id="batch-user-delete"></a>

### 1.22.29 批量删除用户（支持单个用户）

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/admin/users/batch-delete` |
| 业务权限 | `sys:user:delete`；全局资源 |
| 请求 → 响应 | `BatchRevisionRequest` → `BatchDeleted`；HTTP 200，data=null |

物理删除用户；一个用户也提交只有一项的 items，不另设单条删除入口。先锁固定管理员保护行，再按数值升序锁操作者及全部目标，复核权限、存在性及每项版本后才能写入。任一目标仍被文章（含回收站）、评论、上传文件或留言引用，返回 `409/RESOURCE_IN_USE`，整批保留；须先按各模块规则处理引用，不能以删除用户为由自动删除内容。删除后的整批结果必须保留最后有效管理员，否则 `409/LAST_ADMIN`。

数据库按既有外键级联清理角色关联、OAuth 绑定、点赞及邮件订阅；浏览记录和操作日志保留，userId 置 null。删除前持有用户锁，提交前撤销所有目标会话；撤销或成功审计失败则数据库整批回滚，已撤销会话不恢复。审计记录所选 ID 和数量，不记录密码、Token、OAuth 身份。删除成功清理统计缓存；文章点赞计数沿用明细实时统计规则。

请求继承本节公共约束：items 为 1..100 项，不重复，ID 为十进制字符串，revision 为各自用户详情的 X-Revision，不发送 If-Revision。无权限为 403；目标不存在为 404；版本缺失/错误/过期分别为 428/400/412；数据库或 Redis 不可用为 503。任一失败不返回部分成功。

单个用户删除示例（批量时向 items 添加其余用户及各自版本）：

```http
POST /api/v1/admin/users/batch-delete
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
Content-Type: application/json

{"items":[{"id":"12","revision":"\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\""}]}
```

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```


<a id="batch-category-status"></a>

### 1.22.30 批量切换分类状态（支持单个分类）

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/categories/batch-status` |
| 业务权限 | `blog:category:update`；全局字典，SELF 也可操作全部分类 |
| 请求 → 响应 | `BatchStatusRequest` → `BatchVersions`；HTTP 200 |
| `status` | 必填整数，0 隐藏、1 展示；全部目标采用同一目标状态 |

单个分类也提交一项 items，不另设单条状态接口；不执行“取反”，同状态请求通过版本校验后返回成功且保持原版本。items 为 1..100 项且 ID 不重复，每项 revision 取自该分类详情的 X-Revision；不发送 If-Revision 或查询参数。版本缺失/格式错误/过期分别返回 428/400/412；状态缺失、null、超出 0/1 或选择数组非法返回 400；无权限 403，分类不存在 404。

先锁住操作者，再按分类 ID 数值升序锁住所有目标，等待锁后复核原 Token、后台入口和更新权限；全部存在性和版本检查通过后，仅更新发生变化的 status。任一失败则整批数据库修改与成功审计回滚，不返回部分成功；新版本按请求顺序逐项返回。成功提交后清理分类及依赖分类资料的公开缓存，与分类资料编辑使用相同的缓存范围。

隐藏分类不下架文章，不清空文章关联；前台分类列表和按 slug 查询详情不再返回该分类，公开文章仍可阅读且 category 为 null。创建时仍可指定初始 status（默认 1），编辑资料不再修改 status。管理端提供“批量展示/批量隐藏”和行内“展示/隐藏”，单条和批量按钮共用此接口。

```http
PUT /api/v1/admin/categories/batch-status
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
Content-Type: application/json

{"items":[{"id":"2","revision":"\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\""},{"id":"1","revision":"\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\""}],"status":0}
```

```json
{"code":"OK","msg":"成功","data":{"items":[{"id":"2","revision":"\"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\""},{"id":"1","revision":"\"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd\""}]},"errors":[]}
```


### 状态入口补充约定

以下 8 个入口与分类状态入口使用相同的 `BatchStatusRequest` → `BatchVersions` 协议，每次明确选择 1..100 项，各项携带自己详情的 X-Revision；不发送 If-Revision 或查询参数。请求必须有后台登录、admin:access 及表中业务权限，字段、逐条版本与整批事务错误沿用本节公共约定。

先按既有锁顺序锁住操作者及全部目标，全部权限、存在性和版本检查通过后才写状态；同状态仍检查版本，不更新记录、不撤销会话并保留原版本。结果按请求顺序返回；数据库修改与成功审计在一个事务中提交，任一失败整批回滚。公开内容状态变化清理所属业务缓存。行内操作与多选操作共用请求，不再通过资料编辑修改 status。

<a id="batch-links-status"></a>

### 1.22.31 批量切换友链状态（支持单个目标）

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/links/batch-status` |
| 业务权限 | `blog:link:update`；全局资源，SELF 也可操作全部目标 |
| `status` | 必填整数；0 待审核、1 通过并展示、2 驳回/下架 |
| 请求 → 响应 | `BatchStatusRequest` → `BatchVersions`；HTTP 200 |

任一来源的友链都可切换到任一状态。设置为 2 后，待审核申请被驳回，展示中的友链下架，两种结果共用同一个状态值。只有 `status=1` 的记录进入公开列表。状态切换只改状态，[本人申请查询](API_BLOG.md#link-application-get)返回同一 `linkId` 的新状态，申请仍占用该账号的申请名额。同状态提交仍校验版本，成功时沿用原版本。

状态切换和删除均使用[批量公共约定](#admin-batch)，各项提交对应详情的 `X-Revision`。申请人先删除时，对该目标返回 `404/NOT_FOUND`。任一目标缺版本返回 `428/PRECONDITION_REQUIRED`，格式错误返回 `400/VALIDATION_ERROR`，版本过期返回 `412/REVISION_CONFLICT`。失败时整批回滚，客户端可重新查询列表或详情确认最新状态。

<a id="batch-music-status"></a>

### 1.22.32 批量切换音乐状态（支持单个目标）

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/music/batch-status` |
| 业务权限 | `blog:music:update`；须 ALL 范围 |
| `status` | 必填整数；0隐藏、1展示 |
| 请求 → 响应 | `BatchStatusRequest` → `BatchVersions`；HTTP 200 |

展示前先按数值升序锁全部引用文件，再升序锁曲目；锁后校验文件查询范围、类型、歌词后缀和存储可用性。文件权限不能由音乐 ALL 权限替代。隐藏不建立新的公开引用，不改文件或曲目资料。

<a id="batch-home-social-links-status"></a>

### 1.22.33 批量切换社交链接状态（支持单个目标）

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/home/social-links/batch-status` |
| 业务权限 | `home:social:update`；须 ALL 范围 |
| `status` | 必填整数；0隐藏、1展示 |
| 请求 → 响应 | `BatchStatusRequest` → `BatchVersions`；HTTP 200 |

保留其他资料及关联，只修改展示状态。

<a id="batch-cv-experiences-status"></a>

### 1.22.34 批量切换简历经历状态（支持单个目标）

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/cv/experiences/batch-status` |
| 业务权限 | `cv:experience:update`；须 ALL 范围 |
| `status` | 必填整数；0隐藏、1展示 |
| 请求 → 响应 | `BatchStatusRequest` → `BatchVersions`；HTTP 200 |

保留其他资料及关联，只修改展示状态。

<a id="batch-cv-skills-status"></a>

### 1.22.35 批量切换技能状态（支持单个目标）

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/cv/skills/batch-status` |
| 业务权限 | `cv:skill:update`；须 ALL 范围 |
| `status` | 必填整数；0隐藏、1展示 |
| 请求 → 响应 | `BatchStatusRequest` → `BatchVersions`；HTTP 200 |

保留其他资料及关联，只修改展示状态。

<a id="batch-roles-status"></a>

### 1.22.36 批量切换角色状态（支持单个目标）

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/roles/batch-status` |
| 业务权限 | `sys:role:update`；全局资源，SELF 也可操作全部目标 |
| `status` | 必填整数；0禁用、1启用 |
| 请求 → 响应 | `BatchStatusRequest` → `BatchVersions`；HTTP 200 |

先锁管理员保护行，再按用户 ID 升序锁操作者与所有关联用户，最后按角色 ID 升序锁目标。按整批最终结果保护最后管理员，违反返回409/LAST_ADMIN。只撤销状态确有变化的角色关联用户的全部会话，去重后执行；版本和校验须在撤销操作者会话前完成。角色权限、菜单关联及 dataScope 保留，撤销失败使数据库整批回滚，已撤销会话不恢复。

<a id="batch-permissions-status"></a>

### 1.22.37 批量切换权限状态（支持单个目标）

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/permissions/batch-status` |
| 业务权限 | `sys:permission:update`；全局资源，SELF 也可操作全部目标 |
| `status` | 必填整数；0禁用、1启用 |
| 请求 → 响应 | `BatchStatusRequest` → `BatchVersions`；HTTP 200 |

先锁管理员保护行，再升序锁操作者及全部关联用户，最后升序锁权限。管理员必需权限状态变化后按整批最终结果保护最后管理员，违反返回409/LAST_ADMIN；状态实际变化才去重撤销全部关联用户会话。角色授权及菜单门禁关联保留；不因停用权限放宽菜单限制，恢复启用不恢复旧 Token。撤销失败使数据库整批回滚，已撤销会话不恢复。

<a id="batch-menus-status"></a>

### 1.22.38 批量切换菜单状态（支持单个目标）

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/admin/menus/batch-status` |
| 业务权限 | `sys:menu:update`；全局资源，SELF 也可操作全部目标 |
| `status` | 必填整数；0禁用、1启用 |
| 请求 → 响应 | `BatchStatusRequest` → `BatchVersions`；HTTP 200 |

在菜单结构保护锁下锁住操作者及全部选中分支的关联用户，再升序锁目标菜单并检查版本。只修改显式选中节点的 status，不级联改写后代状态；禁用父节点仍截断整支可见菜单。保留路由、isVisible、keepAlive、权限及角色关联，不撤销独立接口授权或会话。父节点可以参与状态批量操作，批量删除仍仅限叶子。

请求示例（多条时向 items 添加其余目标及各自版本）：

```http
PUT /api/v1/admin/music/batch-status
Origin: https://admin.example.com
Cookie: __Host-dl-admin-session=<opaque>
X-Expected-User-Id: 12
Content-Type: application/json

{"items":[{"id":"10","revision":"\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\""}],"status":0}
```

```json
{"code":"OK","msg":"成功","data":{"items":[{"id":"10","revision":"\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\""}]},"errors":[]}
```


---

<a id="models"></a>

## 本端响应模型

字段说明见下表，分页规则见[通用协议](API.md#pagination)。

<a id="response-useradmin"></a>

### 12.9 UserAdmin（3.1）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `username` | string | 登录名 |
| `nickname` | string | 展示昵称 |
| `email` | string&#124;null | 当前账号绑定的邮箱，可 null |
| `avatar` | string&#124;null | 公开头像 URL，可 null |
| `intro` | string&#124;null | 个人简介，可 null |
| `status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `hasPassword` | boolean | 是否设置有效本站密码，不返回哈希 |
| `lastLoginTime` | Time | 最近登录 UTC 时间，未登录可 null |
| `lastLoginIp` | string&#124;null | 最近成功登录的完整 IP，仅管理端可见；历史缺失为 null |
| `lastLoginLocation` | string&#124;null | 最近登录的省市或国家，回环为本地 |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |
| `roleIds` | ID[] | 已分配角色 ID 集合，包含禁用角色 |
| `roles` | object[] | 按角色 ID 升序的已分配角色，包含禁用角色；每项仅含 roleId（ID）、roleCode、roleName（string）、status（0/1），无角色为 [] |

<a id="response-useradmin-2"></a>

### 12.10 UserAdmin（3.3）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `username` | string | 登录名 |
| `nickname` | string | 展示昵称 |
| `email` | string&#124;null | 当前账号绑定的邮箱，可 null |
| `avatar` | string&#124;null | 公开头像 URL，可 null |
| `intro` | string&#124;null | 个人简介，可 null |
| `status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `hasPassword` | boolean | 是否设置有效本站密码，不返回哈希 |
| `lastLoginTime` | string&#124;null | 最近登录 UTC 时间，未登录可 null |
| `lastLoginIp` | string&#124;null | 最近成功登录的完整 IP，仅管理端可见；历史缺失为 null |
| `lastLoginLocation` | string&#124;null | 最近登录的省市或国家，回环为本地 |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |
| `roleIds` | ID[] | 已分配角色 ID 集合，包含禁用角色 |
| `roles` | object[] | 按角色 ID 升序的已分配角色，包含禁用角色；每项仅含 roleId（ID）、roleCode、roleName（string）、status（0/1），无角色为 [] |

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

<a id="response-articleadminsummary"></a>

### 12.17 ArticleAdminSummary

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `articleId` | ID | 文章 ID |
| `authorId` | ID | 文章作者 ID |
| `author` | object | 作者公开资料 |
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
| `author` | object | 作者公开资料 |
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
| `markdownContent` | string | 后台可见范围内的 Markdown 原文，供编辑和预览使用 |

<a id="response-articleadmindetail-2"></a>

### 12.19 ArticleAdminDetail（7.8）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `articleId` | ID | 文章 ID |
| `authorId` | ID | 文章作者 ID |
| `author` | object | 作者公开资料 |
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
| `markdownContent` | string | 后台可见范围内的 Markdown 原文，供编辑和预览使用 |

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

<a id="response-tag"></a>

### 12.24 Tag

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `tagId` | ID | 标签 ID |
| `tagName` | string | 标签名称 |
| `slug` | string | 稳定 URL 标识，区分大小写 |
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
| `content` | string | 纯文本评论 |
| `location` | string&#124;null | 发布时的省市或国家；回环为本地，未知时可 null |
| `userAgentOs` | string&#124;null | 从请求 User-Agent 识别的操作系统类型，未知为 null |
| `userAgentBrowser` | string&#124;null | 浏览器类型，未知为 null；不公开原始 User-Agent/IP |
| `status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |
| `author` | object | 作者公开资料 |
| `author.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `author.nickname` | string | 展示昵称 |
| `author.avatar` | string&#124;null | 公开头像 URL，可 null |
| `articleTitle` | string | 所属文章标题 |
| `hasChildren` | boolean&#124;null | 后台列表是否存在直接回复，不限审核状态；详情未查询此标记时为null |
| `replyTarget` | object&#124;null | 主记录为null；回复返回直接目标的 `nickname` 和最多120字符的 `content` 原文摘要 |
| `articleAuthor` | boolean | 当前记录作者是否为所属文章的当前作者 |
| `siteAuthor` | boolean | 启用用户具有启用的 author 或 admin 角色 |
| `siteAdmin` | boolean | 启用用户具有启用的 admin 角色；同时显示本站作者 |

<a id="response-link"></a>

### 12.30 Link

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `linkId` | ID | 友链 ID |
| `linkName` | string | 友链名称 |
| `url` | string | 绝对 HTTP(S) 地址 |
| `avatar` | string&#124;null | 公开头像 URL，可 null |
| `description` | string&#124;null | 友站简介，纯文本 |
| `sortOrder` | integer | int32 升序排序值 |
| `status` | integer | 0 待审核、1 展示、2 驳回/下架 |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |

<a id="response-admin-link"></a>

#### AdminLink

包含 [Link](#response-link) 的全部字段，并始终返回以下字段。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `source` | string | `APPLICATION` 博客账号提交、`ADMIN` 后台直接创建、`UNKNOWN` 历史来源无法确认 |
| `applicant` | object&#124;null | 当前绑定的申请账号。未绑定或账号已删除时为 null，账号禁用时仍返回摘要 |
| `applicant.userId` | ID | 当前绑定的账号 ID，十进制正整数字符串 |
| `applicant.nickname` | string | 当前绑定账号的昵称 |

绑定、改绑、解绑及账号删除均保留友链来源。后台创建和编辑响应、博客本人申请响应使用 Link，博客公开列表使用 [LinkPublic](API_BLOG.md#response-linkpublic)。

博客申请的后台详情 `data` 示例

```json
{
  "linkId": "35",
  "linkName": "示例友站",
  "url": "https://example.com",
  "avatar": null,
  "description": "友站简介",
  "sortOrder": 0,
  "status": 0,
  "createTime": "2026-10-29T02:00:00.000Z",
  "updateTime": "2026-10-29T02:00:00.000Z",
  "source": "APPLICATION",
  "applicant": { "userId": "12", "nickname": "落叶" }
}
```

<a id="response-file"></a>

### 12.32 File

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `fileId` | ID | 文件 ID |
| `uploaderId` | ID | 本站上传者 ID |
| `originalName` | string | 上传时的原始文件名，无路径 |
| `storedName` | string | OSS 或本地存储生成的文件名，无目录或 Bucket |
| `mimeType` | string | 服务端确认的 MIME 类型 |
| `sizeBytes` | string | 文件实际字节数字符串 |
| `storageType` | string | 当前默认 oss，历史 local 按原元数据读取 |
| `createTime` | Time | 创建 UTC 时间 |
| `previewUrl` | string | 需后台会话的后台文件内容路径 |
| `publicImage` | boolean | 是否通过公开展示图片入口上传 |
| `usage` | string | 上传时保存的用途代码，随列表、详情和普通上传返回 |
| `usages` | string[] | 兼容字段，只包含 `usage` |

| 用途代码 | 含义 |
| --- | --- |
| `ARTICLE_COVER` | 文章封面 |
| `ARTICLE_IMAGE` | 文章正文配图 |
| `MUSIC_COVER` | 音乐封面 |
| `AVATAR` | 用户或站长头像 |
| `LINK_AVATAR` | 友链头像 |
| `EXPERIENCE_LOGO` | 经历标志 |
| `ICON` | 社交链接或技能图标 |
| `IMAGE` | 通用图片 |
| `SONG` | 音频文件 |
| `LYRIC` | LRC、TXT 或 JSON 歌词文件 |
| `ATTACHMENT` | 其他附件 |

图片用途适用于 PNG/JPEG，`SONG` 适用于支持的音频，`LYRIC` 适用于支持的歌词。`ATTACHMENT` 保留给其他附件，当前接口只接收图片、音频和歌词。省略用途时按文件类型使用 `IMAGE`、`SONG`、`LYRIC` 或 `ATTACHMENT`。空值、未知代码或类型不匹配返回 `400/VALIDATION_ERROR`。

用途在上传时保存，此后变更引用也保留原用途。`publicImage`、原有读取权限和删除时的实际引用检查按各自规则执行。

<a id="response-homeprofile"></a>

### 12.34 SiteOwnerProfile

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `profileId` | ID | 固定为1 |
| `nickname` | S(64) | 公开昵称 |
| `tagline` | S0(100) | 个人标签或一句话介绍 |
| `description` | S(500)? | 公开简介，null清空 |
| `avatar` | URL? | 公开头像，HTTP(S) 图片地址 |
| `website` | URL? | 个人网站 |
| `publicEmail` | string&#124;null | ≤254，合法联系邮箱；明确自愿公开，不自动读取登录邮箱 |
| `githubUrl` | URL? | GitHub主页 |
| `location` | S(100)? | 自愿公开的所在地 |
| `createTime` | Time | 创建时间，UTC |
| `updateTime` | Time | 最后更新时间，UTC |

<a id="response-social"></a>

### 12.36 Social

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `socialId` | ID | 社交链接ID |
| `name` | S(64) | 社交平台或链接名称 |
| `icon` | string&#124;null | ≤2048；公开 HTTP(S) 图片地址或前端注册图标键，禁止任意HTML |
| `url` | URL | 外部HTTP(S)目标链接，邮箱链接可使用经过校验的mailto:邮箱 |
| `sortOrder` | int32 | 越小越靠前 |
| `status` | integer | 0隐藏 1展示 |
| `createTime` | Time | 创建时间，UTC |
| `updateTime` | Time | 最后更新时间，UTC |

<a id="response-experience"></a>

### 12.38 Experience

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `experienceId` | ID | 经历ID |
| `experienceType` | integer | 0教育 1工作/实习 2项目 |
| `title` | S(200) | 学校、公司或项目名称 |
| `subtitle` | S(200)? | 专业、职位或项目角色 |
| `logoUrl` | URL? | 公开 HTTP(S) 图片地址 |
| `content` | string | 非空Markdown，UTF-8最多65535字节，与MySQL TEXT容量一致 |
| `startDate` | date | 合法YYYY-MM-DD，1000..9999年 |
| `endDate` | date&#124;null | 结束日期不得早于开始；null表示至今 |
| `sortOrder` | int32 | 越小越靠前 |
| `status` | integer | 0隐藏 1展示 |
| `createTime` | Time | 创建时间，UTC |
| `updateTime` | Time | 最后更新时间，UTC |

<a id="response-skill"></a>

### 12.40 Skill

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `skillId` | ID | 技能ID |
| `name` | S(64) | 技能名称 |
| `description` | S(500)? | 技能描述 |
| `icon` | string&#124;null | ≤2048；公开 HTTP(S) 图片地址或注册图标键 |
| `sortOrder` | int32 | 越小越靠前 |
| `status` | integer | 0隐藏 1展示 |
| `createTime` | Time | 创建时间，UTC |
| `updateTime` | Time | 最后更新时间，UTC |

<a id="response-music"></a>

### 12.44 Music

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `musicId` | ID | 音乐ID |
| `title` | S(200) | 曲目标题 |
| `artist` | S(200)? | 作者或歌手 |
| `durationSeconds` | integer&#124;null | 0..4294967295秒，未知为null |
| `coverUrl` | string? | 外部HTTP(S)封面或规范音乐文件路径 |
| `audioUrl` | string | 外部HTTP(S)音频或规范音乐文件路径；外链保存不触发下载 |
| `lyricUrl` | string? | 外部HTTP(S)歌词或规范音乐文件路径；无歌词时为null |
| `lyricType` | string&#124;null | lrc/json/txt；必须与lyricUrl同时为空或同时非空 |
| `sortOrder` | int32 | 越小越靠前 |
| `status` | integer | 0隐藏 1展示 |
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

<a id="response-visitor"></a>

### 12.48 Visitor

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `visitorId` | ID | 统计访客ID，不是登录身份 |
| `fingerprint` | string | ASCII统计标识，不可作为鉴权凭据 |
| `totalViews` | string | 当前保留的关联浏览明细条数，所有站点合计，无明细为"0"；清理明细后减少，不参与 X-Revision |
| `ipAddress` | string&#124;null | 最近IP，仅后台可见 |
| `userAgent` | string&#124;null | ≤1024，最近User-Agent |
| `country` | string&#124;null | 国家 |
| `province` | string&#124;null | 省份 |
| `city` | string&#124;null | 城市 |
| `isBlocked` | integer | 数据库封禁标记0/1；实际生效还需检查blockedUntil |
| `blockedUntil` | Time&#124;null | 封禁结束，已封禁且null表示永久 |
| `blockedIpAddress` | string&#124;null | 此记录固定的封禁 IP；null 时没有 IP 封禁 |
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

<a id="response-adminstatistics"></a>

### 12.51 AdminStatistics

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pageViewCount` | string | 筛选日期内保留浏览明细条数 |
| `visitorCount` | string | 筛选日期内不同非NULL visitorId；不按IP或账号去重 |
| `newVisitorCount` | string | firstVisitTime在日期区间且符合站点访问条件的访客数 |
| `articleCount` | string | 当前未回收文章数，不受访问日期筛选 |
| `publishedArticleCount` | string | 当前PUBLIC文章数 |
| `commentCount` | string | 当前评论数，含待审/驳回 |
| `messageCount` | string | 当前留言数，含私密/待审/驳回 |
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
| `selectedCount` | string&#124;null | 共用批量接口时选中的目标数，其他情况为空 |

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

<a id="response-discussionconversation"></a>

### 12.63 DiscussionConversation

后台评论对话的 `T` 为 [CommentAdmin](#response-commentadmin)，后台留言对话的 `T` 为 [MessagePrivate](API.md#response-messageprivate)。此对象放在 `Result.data` 中，不再套一层 PageResult。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `root` | T | 选中记录所属对话的主记录，固定返回，不计入回复总数 |
| `page` | integer | 实际回复页码；省略请求page时定位选中回复，主记录或无回复时为1 |
| `pageSize` | integer | 默认20，范围1..50 |
| `total` | string | 主记录下全部后代回复数，不包含root |
| `totalPages` | string | 回复总页数；没有回复时为字符串"0" |
| `records` | T[] | 当前页全部层级的回复，按createTime和记录ID升序；超末页为空数组 |

`root` 和 `records` 的 `parentId` 保留真实直接回复目标；`replyTarget` 使用[回复对象模型](API.md#response-discussionreplytarget)，`hasChildren` 在对话读取中为null。权限范围、定位和错误分别见[评论对话](#admin-comments-conversation)及[留言对话](#admin-messages-conversation)。不返回 X-Revision；审核前仍需读取原详情版本。
