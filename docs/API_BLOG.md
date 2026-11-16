<a id="client-blog"></a>

# 博客端接口

本端接口使用 `/api/v1/blog` 前缀。

[本端接口索引](API_INDEX.md#index-blog) · [通用协议](API.md#common) · [共享响应模型](API.md#models) · [错误码](API.md#errors) · [本端响应模型](#models)

共用接口见[图形验证码](API.md#captcha)和[未知路由上报](API.md#not-found-protection)。

<a id="group-1"></a>

<a id="blog-group-1"></a>

## 2.1 认证与第三方授权

<a id="api-1"></a>

### 2.1.1 注册账号

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/auth/register` |
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
POST /api/v1/blog/auth/register
Origin: https://blog.example.com
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

<a id="api-2"></a>

### 2.1.2 账号密码登录

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/auth/login` |
| 接口描述 | 账号密码登录 |
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
POST /api/v1/blog/auth/login
Origin: https://blog.example.com
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
    "expiresIn": 259200,
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

### 2.1.3 退出当前登录

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/auth/logout` |
| 接口描述 | 退出当前登录 |
| Content-Type | 无请求体 |
| 访问要求 | 幂等注销；无会话或已失效仍返回 200 |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
POST /api/v1/blog/auth/logout
Origin: https://blog.example.com
Cookie: __Host-dl-blog-session=<opaque>
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

<a id="api-4"></a>

### 2.1.4 查询可用第三方平台

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/auth/oauth/providers` |
| 接口描述 | 查询可用第三方平台 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

#### 请求参数

无 Path、Query 或请求体参数；GET/无模型写操作不发送请求体。

#### 请求示例

```http
GET /api/v1/blog/auth/oauth/providers
```

#### 响应参数

`data`：[Provider](API.md#response-provider) 数组。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":[{"source":"GITHUB","displayName":"GitHub"}],"errors":[]}
```

只列本端配置完整且启用的平台，示例 GITHUB 不代表已经开通；不返回 App ID/Secret。

---

<a id="api-5"></a>

### 2.1.5 发起第三方登录或绑定

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/auth/oauth/{source}/authorize` |
| 接口描述 | 发起第三方登录或绑定 |
| Content-Type | application/json |
| 访问要求 | LOGIN 公开，免图形验证码，超限返回 `429/RATE_LIMITED`；BIND 登录本人 + 最近 300 秒认证；按通用规则检查来源 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `source` | string | 是 | Path；服务端已启用平台的大写标识；未知或未启用 404 |
| `operation` | string | 是 | LOGIN 登录；BIND 绑定当前用户；不接收 redirectUri/userId/uuid |
| `__Host-dl-oauth-browser` | string | 否（缺失时由后端签发） | Cookie；Secure、HttpOnly、SameSite=Lax、Path=/，仅绑定浏览器，不是业务登录凭证 |
| `X-Expected-User-Id` | string | BIND 是，LOGIN 否 | Header。页面预期账号，使用十进制用户 ID。 |
| `Origin` | string | 与 Referer 至少提供一项 | Header。匹配所属端 HTTPS Origin，缺失时按通用规则核对 Referer。 |

#### 请求示例

```http
POST /api/v1/blog/auth/oauth/GITHUB/authorize
Cookie: __Host-dl-oauth-browser=<browser-id>
Origin: https://blog.example.com
Content-Type: application/json

{"operation":"LOGIN"}
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

LOGIN 公开且仅接受已绑定第三方身份；未绑定返回 OAUTH_NOT_BOUND，必须先通过邮箱注册，再登录绑定。BIND 需当前有效会话及最近 300 秒认证。两者均校验 Origin，并设置/读取 OAuth 浏览器 Cookie；绑定使用弹窗保留发起页面。

---

<a id="api-6"></a>

### 2.1.6 第三方平台授权回调

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/auth/oauth/{source}/callback` |
| 接口描述 | 第三方平台授权回调 |
| Content-Type | 无请求体 |
| 访问要求 | 仅平台回调；校验 state、OAuth 流程 Cookie、流程及原会话；回调不需要登录 Cookie |
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
GET /api/v1/blog/auth/oauth/GITHUB/callback?code=<platform-code>&state=<random-state>&iss=https%3A%2F%2Fgithub.com%2Flogin%2Foauth
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

平台顶层 GET 回调使用 OAuth 流程 Cookie。后端凭 state、浏览器 Cookie 和 Redis 流程定位操作；BIND 再校验服务端保存的原会话仍有效且属于目标用户。可信流程成功后 303 到固定完成页，不在回调直接向浏览器发本站 Token。已消费的可信流程遇到数据库访问或事务提交失败时，303 到固定完成页的 `#error=SERVICE_UNAVAILABLE`，不签发成功票据，且不能重放本次 state。

---

<a id="api-7"></a>

### 2.1.7 兑换一次性登录或绑定结果

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/auth/oauth/exchange` |
| 接口描述 | 兑换一次性登录或绑定结果 |
| Content-Type | application/json |
| 访问要求 | 同浏览器的 OAuth 流程 Cookie；BIND 须原会话 Cookie，LOGIN 可匿名兑换 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ticket` | string | 是 | 回调得到的本站一次性票据；有效期 60 秒，不是平台 code 或本站 Token |
| `__Host-dl-oauth-browser` | string | 是 | Cookie；Secure、HttpOnly、SameSite=Lax、Path=/，仅绑定浏览器，不是业务登录凭证 |
| `X-Expected-User-Id` | string | BIND 是，LOGIN 否 | Header。页面预期账号，使用十进制用户 ID。 |
| `Origin` | string | 与 Referer 至少提供一项 | Header。匹配所属端 HTTPS Origin，缺失时按通用规则核对 Referer。 |

#### 请求示例

```http
POST /api/v1/blog/auth/oauth/exchange
Cookie: __Host-dl-oauth-browser=<browser-id>
Origin: https://blog.example.com
Content-Type: application/json

{"ticket":"<one-time-ticket>"}
```

#### 响应参数

`data`：[OAuthResult](API.md#response-oauthresult)。

#### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "operation": "LOGIN",
    "session": {
      "expiresIn": 259200,
      "user": {
        "userId": "12", "username": "leaf", "nickname": "落叶", "email": null, "avatar": null, "intro": null,
        "status": 1
      }
    }
  },
  "errors": []
}
```

BIND 成功分支（仍为 200，原会话必须有效，不返回新 Session）：

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

必须携带同一浏览器的 OAuth 流程 Cookie。Blog LOGIN 签发博客 Cookie 并返回 Session；BIND 必须带发起时的原会话 Cookie 和 `X-Expected-User-Id`，只返回 binding。票据有效期 60 秒且仅消费一次。

---

<a id="blog-auth-email-code"></a>

### 2.1.8 发送注册或登录邮箱验证码

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/auth/email-code` |
| Content-Type | application/json |
| 访问要求 | 公开；无需登录，仍执行图形验证码及接口限流 |
| 成功状态 | 200 OK |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `X-Captcha-Id` | string | 是 | Header；当前端图形验证码的随机标识 |
| `X-Captcha-Code` | string | 是 | Header；图形验证码的6位数字，保留前导零 |
| `email` | string | 是 | JSON；非空合法邮箱，最多254字符；服务端去首尾空白并转小写 |
| `purpose` | string | 是 | JSON；仅 REGISTER 或 LOGIN，不接受后台登录用途 |

JSON只接收表中标为JSON的字段，不接受查询参数、额外字段或重复JSON属性。图形验证码与邮件验证码是两项独立校验，不可互换。

#### 请求示例

```http
POST /api/v1/blog/auth/email-code
Origin: https://blog.example.com
X-Captcha-Id: <captchaId>
X-Captcha-Code: 012345
Content-Type: application/json

{"email":"leaf@example.test","purpose":"REGISTER"}
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

先消费图形验证码，再检查发送频率。REGISTER 用于注册，允许向新邮箱发送；LOGIN 用于博客邮箱登录，只发送到已绑定本站账号的邮箱。再次发送会替换同用途、同邮箱的旧验证码。 邮件验证码五分钟有效，按邮箱和用途隔离；所有用途与端共享每邮箱每60秒1次、每可信IP每60秒5次的发送预算。

| HTTP / code | 说明 |
| --- | --- |
| 400 / VALIDATION_ERROR | 必填字段、邮箱、验证码格式、用途、JSON或查询参数不合法 |
| 400 / CAPTCHA_INVALID | 图形验证码缺失、无效、过期或已消费 |
| 401 / AUTH_FAILED | LOGIN 用途下该邮箱未注册 |
| 429 / RATE_LIMITED | 邮件发送限额超出，返回 Retry-After |
| 503 / SERVICE_UNAVAILABLE | 邮件未启用、SMTP发送失败或依赖不可用；发送失败清理已生成验证码 |

每次发送或登录结束后重新获取图形验证码；业务失败保留输入，不自动重放请求。通用响应与安全边界见[邮箱验证与登录方式](API.md#captcha)。

---

<a id="blog-auth-email-login"></a>

### 2.1.9 邮箱验证码登录

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/auth/email-login` |
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
POST /api/v1/blog/auth/email-login
Origin: https://blog.example.com
X-Captcha-Id: <captchaId>
X-Captcha-Code: 012345
Content-Type: application/json

{"email":"leaf@example.test","emailCode":"012345"}
```

#### 响应参数

成功时通过 `Set-Cookie` 签发登录 Cookie，`data` 为 [Session](API.md#response-session)，`expiresIn=259200`（3天）；`user` 为 [Me](API.md#response-me)，不包含完整IP、密码哈希或权限快照。

```json
{
  "code": "OK",
  "msg": "成功",
  "data": {
    "expiresIn": 259200,
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

先检查登录失败限额并消费图形验证码，再锁定邮箱所属账号；找不到账号时清除该用途的旧验证码并提示邮箱未注册。找到账号后消费 LOGIN 用途邮件验证码，不接受注册或另一端登录的验证码。邮件验证码正确后检查账号启用状态，成功才签发本站会话。邮箱方式不要求已设置本站密码。邮件校验无论正确、错误或后续业务失败，当前验证码都不能再次使用。错误邮件验证码或 AUTH_FAILED 会计入登录失败预算，CAPTCHA_INVALID 不计入密码/邮箱登录失败次数；图形验证码失败时不会进入邮件校验。

| HTTP / code | 说明 |
| --- | --- |
| 400 / VALIDATION_ERROR | 必填字段、邮箱、验证码格式、用途、JSON或查询参数不合法 |
| 400 / CAPTCHA_INVALID | 图形验证码缺失、无效、过期或已消费 |
| 400 / EMAIL_CODE_INVALID | 邮件验证码错误、过期、用途不符或已消费 |
| 401 / AUTH_FAILED | 该邮箱未注册 |
| 403 / ACCOUNT_DISABLED | 账号已禁用 |
| 429 / RATE_LIMITED | 登录入口预算或失败次数超限，返回 Retry-After |
| 503 / SERVICE_UNAVAILABLE | Redis、数据库等依赖不可用，不能跳过校验或返回成功 |

每次发送或登录结束后重新获取图形验证码；业务失败保留输入，不自动重放请求。通用响应与安全边界见[邮箱验证与登录方式](API.md#captcha)。

---

<a id="blog-auth-math-captcha"></a>

### 2.1.10 获取评论和留言数字题目

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/auth/math-captcha` |
| 访问要求 | 公开；无需登录 或已有验证码 |
| 请求参数 | 无；不接受查询参数或请求体 |
| 成功状态 | 200 OK，`Cache-Control: no-store` |
| 响应 | `Result<ArithmeticCaptcha>`，完整字段见[数字题目模型](#response-arithmeticcaptcha) |

```http
GET /api/v1/blog/auth/math-captcha
```

```json
{"code":"OK","msg":"成功","data":{"captchaId":"<43位URL安全随机字符串>","question":"9 × 9 = ?","expiresIn":120},"errors":[]}
```

题目使用两个1至9整数的加、减或乘法，减法不产生负数；答案只存服务端。提交评论或留言时，将答案作为无前导零的字符串放入 `X-Captcha-Code`，`X-Captcha-Id` 使用本次返回标识。与登录图形验证码按用途隔离，不能互换。

与 Admin/Blog 图形验证码生成共用每可信IP每60秒30次预算；超限返回429/RATE_LIMITED和Retry-After，非法参数返回400/VALIDATION_ERROR，依赖不可用返回503/SERVICE_UNAVAILABLE。使用入口、一次消费及失败刷新规则见[数字题目验证](API.md#评论和留言的数字题目验证)。

---

<a id="group-2"></a>

<a id="blog-group-2"></a>

## 2.2 本人资料与安全设置

<a id="api-8"></a>

### 2.2.1 查询本人资料

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/me` |
| 接口描述 | 查询本人资料 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
GET /api/v1/blog/me
Cookie: __Host-dl-blog-session=<opaque>
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

<a id="api-9"></a>

### 2.2.2 修改本人资料

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PATCH /api/v1/blog/me/profile` |
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
PATCH /api/v1/blog/me/profile
Origin: https://blog.example.com
Cookie: __Host-dl-blog-session=<opaque>
X-Expected-User-Id: 12
If-Revision: "<revision-from-detail>"
Content-Type: application/json

{"nickname":"新落叶","email":null,"avatar":null,"intro":null}
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
    "userId": "12", "username": "leaf", "nickname": "新落叶", "email": null, "avatar": null, "intro": null,
    "status": 1
  },
  "errors": []
}
```

至少一项；缺失保持，null 清空可空字段，可空字符串去首尾空白后为空也清空。username、status、角色、权限不可改。头像接受公开 HTTP(S) 图片地址，包括[公开展示图片](#display-image-content)。附件 API 路径及其等价绝对 URL（含路径编码、归一化后的形式）会被拒绝。

---

<a id="api-10"></a>

### 2.2.3 设置或修改本站密码

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/blog/me/password` |
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
PUT /api/v1/blog/me/password
Origin: https://blog.example.com
Cookie: __Host-dl-blog-session=<opaque>
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

<a id="api-11"></a>

### 2.2.4 清除本站密码

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/blog/me/password` |
| 接口描述 | 清除本站密码 |
| Content-Type | application/json |
| 访问要求 | 登录本人 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `currentPassword` | string | 是 | 验证现有密码；无密码账号返回 STATE_CONFLICT |

#### 请求示例

```http
DELETE /api/v1/blog/me/password
Origin: https://blog.example.com
Cookie: __Host-dl-blog-session=<opaque>
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

<a id="api-12"></a>

### 2.2.5 查询本人有效角色与权限

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/me/capabilities` |
| 接口描述 | 查询本人有效角色与权限 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
GET /api/v1/blog/me/capabilities
Cookie: __Host-dl-blog-session=<opaque>
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

<a id="api-13"></a>

### 2.2.6 查询本人可访问菜单树

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/me/menus` |
| 接口描述 | 查询本人可访问菜单树 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
GET /api/v1/blog/me/menus
Cookie: __Host-dl-blog-session=<opaque>
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

<a id="api-14"></a>

### 2.2.7 查询本人第三方绑定

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/me/oauth-bindings` |
| 接口描述 | 查询本人第三方绑定 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
GET /api/v1/blog/me/oauth-bindings
Cookie: __Host-dl-blog-session=<opaque>
```

#### 响应参数

`data`：[OAuthBinding](API.md#response-oauthbinding) 数组。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":[{"source":"GITHUB","createTime":"2026-10-29T02:00:00.000Z"}],"errors":[]}
```

source 升序，只列当前配置完整且启用的平台绑定，仅返回平台和绑定时间，不暴露 uuid、绑定记录 ID 或第三方 Token。未启用或配置不完整的绑定记录仍保留在库中，不计为可用登录方式。

---

<a id="api-15"></a>

### 2.2.8 解绑本人第三方账号

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/blog/me/oauth-bindings/{source}` |
| 接口描述 | 解绑本人第三方账号 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `source` | string | 是 | Path；服务端已启用平台的大写标识；未知或未启用 404 |

#### 请求示例

```http
DELETE /api/v1/blog/me/oauth-bindings/GITHUB
Origin: https://blog.example.com
Cookie: __Host-dl-blog-session=<opaque>
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

<a id="blog-group-3"></a>

## 2.3 文章与附件

<a id="api-37"></a>

### 2.3.1 分页查询公开文章

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
| `authorId` | ID | 否 | 可选，作者筛选；与分类、标签、关键词和月份同时满足，不作为登录身份 |
| `month` | string | 否 | 可选，合法 YYYY-MM，年份 1000..9999；按 UTC 月份过滤 |

#### 请求示例

```http
GET /api/v1/blog/articles?page=1&pageSize=10
```

#### 响应参数

`data`：分页对象，`records[]` 为 [ArticlePublic](#response-articlepublic)；分页字段见 [0.2](API.md#pagination)。

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

按分类、标签、关键词和月份查询的独立调用示例见 [7.12](#article-scenarios)。公开查询支持可选 `authorId`，与其他筛选按 AND 组合；完整约定见 [22.1](#article-author-query)。

---

<a id="api-38"></a>

### 2.3.2 按 slug 查询公开文章详情

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
    "category": null, "title": "第一片落叶", "slug": "first-leaf", "summary": "记录与分享",
    "markdownContent": "# 第一片落叶\n\n记录与分享。",
    "markdownContent": "# 第一片落叶\n\n记录与分享。"
  },
  "errors": []
}
```

每次成功原子增加 view_count；失败不计数，这是阅读次数而非独立访客。详情返回 `markdownContent` 原文供网页 MdPreview 渲染；列表不增加正文字段。原文在锁定文章、再次确认 PUBLIC 并检查正文附件后返回。携带 Token 也不能读取私密、草稿、下架、回收站或未到发布时间的正文；不满足条件仍返回 404。字数/阅读时长、上下篇和相关推荐见 [文章导航与阅读统计](API_CONTRACT.md#model-12-15)，所有关联文章也必须PUBLIC。

原文不是可信 HTML，网页仍需清理本地渲染结果；写入接口继续仅接收 Markdown，由后端 CommonMark + jsoup 生成 HTML，不接受前端生成的 `htmlContent`。作者/读者排版偏好仅保存在各自浏览器，不属于请求或响应模型；博客正文明暗仅跟随现有网站黑白天开关/系统主题，管理端编辑和预览固定亮色。正文必须包含字符串类型的 markdownContent，缺失或类型错误均拒绝响应；空字符串是有效正文。解析差异见 [Markdown 阅读说明](Markdown阅读说明.md)。

已实现的[详情缓存](API.md#public-cache)只复用公开内容片段，命中仍执行上述成功计数及当前PUBLIC校验；详情与[公开GET限流](API.md#request-protection)同样适用于缓存命中，不引入访客去重口径。

---

<a id="api-39"></a>

### 2.3.3 分页查询文章月归档

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

`data`：分页对象，`records[]` 为 [Archive](#response-archive)；分页字段见 [0.2](API.md#pagination)。

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

<a id="api-87"></a>

### 2.3.4 查询文章热门榜

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
| 删除文章 | `POST /api/v1/admin/articles/batch-recycle` | 已实现；[统一回收](API_ADMIN.md#api-45)，Body 提交 items（含 id=101 和详情 revision），移入回收站 |
| 查询回收站 | `GET /api/v1/admin/articles?trash=1&page=1&pageSize=10` | 已实现；仍按 query 范围过滤，不与正常文章混查 |
| 恢复文章 | `POST /api/v1/admin/articles/batch-restore` | 已实现；[统一恢复](API_ADMIN.md#api-46)，Body 提交 items（含 id=101 和回收站详情 revision），恢复为下架，不自动公开 |
| 后台预览草稿或下架内容 | `GET /api/v1/admin/articles/101` | 已实现；[7.5](API_ADMIN.md#api-41) 返回 Markdown 与安全 HTML，公开详情不会因 Token 放宽可见性 |
| 阅读上下篇和同分类推荐 | `GET /api/v1/blog/articles/first-leaf` | 已实现；[7.2](#api-38) 返回 prevArticle、nextArticle、relatedArticles，同分类推荐最多 6 条；详情成功会增加阅读量 |
| 前台按作者查询公开文章 | `GET /api/v1/blog/articles?authorId=12&page=1&pageSize=10` | 已实现；[22.1](#article-author-query)，与其他筛选按 AND 组合 |

删除流程：在对应权限范围内获取后台详情及 X-Revision → 将 ID 与版本放入 items 数组提交统一回收接口 → 从正常列表移除；回收站若要恢复，重新取详情取得最新版本。缺少版本返回 428，版本过期返回 412。回收站文章仍占用 slug，恢复时沿用原 slug；永久删除的单独设计见 [22.7](API_ADMIN.md#api-159)。

分类/标签页目前可从分页字典列表取得 ID，再调用上述文章列表。直接打开 `/categories/{slug}` 或 `/tags/{slug}` 时定位单个字典的补充设计见 [22.4](#api-156)、[22.5](#api-157)，无需让客户端遍历所有字典分页。

---

<a id="api-59"></a>

### 2.3.5 分页查询文章公开评论

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
| `parentId` | ID | 否 | 缺失仅根评论；提供时分页查询该可见评论下的全部层级回复，不包含该评论本身 |

#### 请求示例

```http
GET /api/v1/blog/articles/101/comments?page=1&pageSize=10
```

#### 响应参数

`data`：分页对象，`records[]` 为 [CommentPublic](#response-commentpublic)；分页字段见 [0.2](API.md#pagination)。

#### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "commentId": "201", "articleId": "101", "parentId": null, "content": "感谢分享。", "status": 1,
        "replyCount": "2", "replyTarget": null, "replyable": true,
        "createTime": "2026-10-29T02:00:00.000Z"
      }
    ]
  },
  "errors": []
}
```

每页最多50；不传parentId为根节点，按createTime DESC,commentId DESC排序；传入后将该节点下的全部层级回复合并分页，按createTime ASC,commentId ASC排序。响应parentId仍是真实的直接回复目标，不改写为根节点。带有效会话时保留本人待审、驳回内容，全部祖先也须审核通过或属于本人；其他用户不能读取未通过内容。total按当前查询范围计数，replyCount统计当前读者可见的全部后代。replyTarget返回直接回复对象的昵称及最多120字符的原文摘要，跨页也可读取；replyable仅在当前节点及全部祖先通过审核时为true，发表仍检查会话权限及文章开关。匿名结果使用公开缓存，有效会话查询直接回源。

---

<a id="api-60"></a>

### 2.3.6 发表评论或回复

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
| `X-Captcha-Id` | string | 是 | Header；见[验证码契约](API.md#captcha) |
| `X-Captcha-Code` | string | 是 | Header；6 位数字，保留前导零 |
| `articleId` | ID | 是 | Path；十进制正整数 ID 字符串 |
| `content` | string | 是 | trim 后 1..2000 字符纯文本 |
| `parentId` | ID&#124;null | 否 | 默认 null；非 null 须同篇文章、已通过且祖先可见 |

#### 请求示例

```http
POST /api/v1/blog/articles/101/comments
Origin: https://blog.example.com
Cookie: __Host-dl-blog-session=<opaque>
X-Expected-User-Id: 12
X-Captcha-Id: <captchaId>
X-Captcha-Code: 012345
Content-Type: application/json

{"content":"感谢分享。","parentId":null}
```

#### 响应参数

`data`：[CommentOwn](#response-commentown)。

#### 响应示例

以下为普通用户且 `comment.reviewRequired=true` 的待审示例；符合[免审规则](#discussion-review)或关闭审核时，响应结构相同，`status` 为 `1`。

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "commentId": "201", "articleId": "101", "userId": "12", "parentId": null, "content": "感谢分享。", "status": 0
  },
  "errors": []
}
```

需要 `blog:comment:create`；文章 PUBLIC，站点和文章评论开关均开。

<a id="discussion-review"></a>

#### 评论和留言审核规则

以下规则同时适用于根评论、根留言和回复。身份由服务端查询当前用户的有效角色，文章归属以当前文章的 `authorId` 为准。

| 当前有效身份 | 评论或回复自己的文章 | 评论或回复他人的文章 | 发表或回复留言 |
| --- | --- | --- | --- |
| 包含 `admin` 角色 | 直接通过，`status=1` | 直接通过，`status=1` | 直接通过，`status=1` |
| 包含 `author`，不含 `admin` | 直接通过，`status=1` | 按 `comment.reviewRequired` 决定 | 按 `message.reviewRequired` 决定 |
| 其他用户 | 按 `comment.reviewRequired` 决定 | 按 `comment.reviewRequired` 决定 | 按 `message.reviewRequired` 决定 |

按配置审核时，`reviewRequired=true` 保存为 `status=0`（待审核），`false` 保存为 `status=1`（通过）。禁用角色不参与免审判断；仅拥有文章但没有有效 `author` 角色不自动免审。

免审仍须满足账号、发表权限、验证码、限流、业务开关和父链可回复性检查；留言仍保留全部私密限制。客户端不能指定角色、作者或审核状态，前端以创建响应中的 `status` 显示结果。此规则仅影响新发表内容，已有待审记录不自动变更。

---

<a id="api-67"></a>

### 2.3.7 查询本人点赞状态

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
Cookie: __Host-dl-blog-session=<opaque>
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

### 2.3.8 点赞文章

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
Origin: https://blog.example.com
Cookie: __Host-dl-blog-session=<opaque>
X-Expected-User-Id: 12
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

### 2.3.9 取消文章点赞

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
Origin: https://blog.example.com
Cookie: __Host-dl-blog-session=<opaque>
X-Expected-User-Id: 12
```

#### 响应参数

`data`：[LikeState](#response-likestate)。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":{"liked":false,"likeCount":"0"},"errors":[]}
```

不存在也成功且 liked=false；下架后不开放此路径，既有明细保留。

---

<a id="api-85"></a>

### 2.3.10 读取公开文章附件

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

文章 PUBLIC 且正文/封面实际引用该文件；非公开即使携带 Token 也 404。

---

<a id="article-author-query"></a>

### 2.3.11 前台按作者查询公开文章（扩展既有接口）

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/articles`，新增可选 `authorId` |
| 接口描述 | 作者主页分页读取该作者的公开文章，并可与分类、标签、关键词和月份组合 |
| Content-Type | 无请求体 |
| 访问要求 | 公开，只查询 PUBLIC 文章 |
| 实施状态 | 已实现参数扩展，不新增路由计数 |

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

<a id="blog-group-4"></a>

## 2.4 作者主页

<a id="api-154"></a>

### 2.4.1 分页查询公开作者

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/authors` |
| 接口描述 | 作者发现页及作者筛选器，只列出至少有一篇 PUBLIC 文章的作者 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |
| 实施状态 | 已实现 |

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

`data`：分页对象，`records[]` 为 [AuthorPublic](#response-authorpublic)；分页字段见 [0.2](API.md#pagination)。

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

### 2.4.2 查询作者公开主页资料

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/authors/{userId}` |
| 接口描述 | 公开作者主页的资料区与公开文章数 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；目标须至少有一篇 PUBLIC 文章 |
| 实施状态 | 已实现 |

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

<a id="blog-group-5"></a>

## 2.5 分类与标签

<a id="api-47"></a>

### 2.5.1 分页查询公开分类

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/categories` |
| 接口描述 | 分页查询公开分类 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

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

`data`：分页对象，`records[]` 与[公开分类详情](#api-156)字段相同，包含字符串 `articleCount`；分页字段见 [0.2](API.md#pagination)。

#### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "categoryId": "1", "categoryName": "技术", "slug": "tech", "description": null, "sortOrder": 0,
        "articleCount": "3"
      }
    ]
  },
  "errors": []
}
```

仅返回 status=1 的分类，按 sortOrder、categoryId 升序。没有公开文章时 `articleCount` 为 `"0"`。

---

<a id="api-53"></a>

### 2.5.2 分页查询公开标签

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/tags` |
| 接口描述 | 分页查询公开标签 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

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

`data`：分页对象，`records[]` 与[公开标签详情](#api-157)字段相同，包含字符串 `articleCount`；分页字段见 [0.2](API.md#pagination)。

#### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "tagId": "1", "tagName": "Java", "slug": "java", "articleCount": "3"
      }
    ]
  },
  "errors": []
}
```

按 tagId 升序。没有关联公开文章时 `articleCount` 为 `"0"`。

---

<a id="api-156"></a>

### 2.5.3 按 slug 查询公开分类详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/categories/{slug}` |
| 接口描述 | 直接打开分类页时解析 slug，取得分类资料、ID 和公开文章数 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；分类 status=1 |
| 实施状态 | 已实现 |

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

不存在或隐藏分类统一 `404/NOT_FOUND`，包括携带后台 Token 的情况；展示中但没有公开文章的分类返回详情及 articleCount="0"。取得 ID 后调用 `GET /api/v1/blog/articles?categoryId=1`，文章数口径与该列表一致。slug 改名后旧值 404，不新增重定向历史。

---

<a id="api-157"></a>

### 2.5.4 按 slug 查询公开标签详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/tags/{slug}` |
| 接口描述 | 直接打开标签页时解析 slug，取得标签 ID 和公开文章数 |
| Content-Type | 无请求体 |
| 访问要求 | 公开；标签无 status 字段 |
| 实施状态 | 已实现 |

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

不存在返回 `404/NOT_FOUND`；已存在但没有公开文章返回详情及 articleCount="0"。取得 ID 后调用 `GET /api/v1/blog/articles?tagId=1`；数量与该列表使用同一 PUBLIC 条件。slug 改名后旧值 404。分类/标签详情都不返回后台 X-Revision 或非公开文章计数。

---

<a id="blog-group-6"></a>

## 2.6 留言板与审核

<a id="api-109"></a>

### 2.6.1 分页读取可见留言或回复

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/messages` |
| 接口描述 | 分页读取可见留言或回复 |
| Content-Type | 无请求体 |
| 访问要求 | 公开。提供预期账号时校验有效博客会话，否则按匿名处理 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..50 |
| `parentId` | ID | 否 | 缺失仅根留言；提供时分页查询该可见留言下的全部层级回复，不包含该留言本身 |

#### 请求示例

```http
GET /api/v1/blog/messages?page=1&pageSize=10
```

#### 响应参数

`data`：分页对象，`records[]` 为 [MessagePublic](#response-messagepublic)；分页字段见 [0.2](API.md#pagination)。

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
        "content": "你好，欢迎交流。", "htmlContent": "<p>你好，欢迎交流。</p>", "contentFormat": 0, "status": 1, "replyCount": "2",
        "replyTarget": null, "replyable": true
      }
    ]
  },
  "errors": []
}
```

匿名只读取审核通过的公开节点；合法会话可额外读取本人待审/驳回节点及当前私密线程允许的内容。完整祖先链必须可见，total统计当前查询范围内的可见节点。replyCount为当前读者可见的全部后代数量，以字符串返回，与以该节点为parentId查询整组回复的total一致。根留言及整组回复均按createTime ASC,messageId ASC排序。不可见父节点404。响应parentId保留真实的直接回复目标；replyTarget返回直接回复对象的昵称及最多120字符原文摘要，跨页仍可查看。replyable要求当前节点及全部祖先通过审核；私密范围、会话权限及开关仍由发表接口复核。

---

<a id="api-110"></a>

### 2.6.2 发表或回复留言

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
| `X-Captcha-Id` | string | 是 | Header；见[验证码契约](API.md#captcha) |
| `X-Captcha-Code` | string | 是 | Header；6 位数字，保留前导零 |
| `parentId` | ID&#124;null | 是，可null | 直接回复目标；根留言为null，回复创建后不可改 |
| `content` | S(2000) | 是 | 去首尾空白后非空；Markdown保留原文内部空白 |
| `contentFormat` | integer | 是 | 0纯文本 1Markdown |
| `isSecret` | integer | 是 | 0公开 1悄悄话；私密回复必须继承完整父链限制 |
| `notifyReply` | integer | 是 | 0/1；见留言通知边界 |

#### 请求示例

```http
POST /api/v1/blog/messages
Origin: https://blog.example.com
Cookie: __Host-dl-blog-session=<opaque>
X-Expected-User-Id: 12
X-Captcha-Id: <captchaId>
X-Captcha-Code: 012345
Content-Type: application/json

{"parentId":null,"content":"你好，欢迎交流。","contentFormat":0,"isSecret":0,"notifyReply":0}
```

#### 响应参数

`data`：[MessagePrivate](API.md#response-messageprivate)。

#### 响应示例

以下为普通用户且 `message.reviewRequired=true` 的待审示例；符合[免审规则](#discussion-review)或关闭审核时，响应结构相同，`status` 为 `1`。

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "messageId": "301", "parentId": null,
    "author": {
      "userId": "12", "nickname": "落叶", "avatar": null
    },
    "location": "江苏省 南京市", "userAgentOs": "Windows", "userAgentBrowser": "Edge",
    "content": "你好，欢迎交流。", "htmlContent": "<p>你好，欢迎交流。</p>", "contentFormat": 0, "status": 0
  },
  "errors": []
}
```

属地、系统和浏览器由服务端从本次请求采集，不接受客户端指定；编辑保留发布时的标识，历史空值显示未知，不公开完整 IP 或原始 User-Agent。

作者固定为当前用户。先锁message配置，再锁父链，检查enabled和父链可见性；按[评论和留言审核规则](#discussion-review)决定初始状态：有效 `admin` 角色直接通过，`author` 和其他用户按 `message.reviewRequired` 决定0/1；免审仍需满足发表权限、开关、私密范围和父链可回复性检查。客户端不能写作者、昵称、rootId、status、isAdminReply或HTML。 创建成功不设置Location，通过响应ID及本人/后台详情入口定位。

---

<a id="blog-group-7"></a>

## 2.7 本人评论

<a id="api-61"></a>

### 2.7.1 分页查询本人评论

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/me/comments` |
| 接口描述 | 分页查询本人评论 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..50 |
| `articleId` | ID | 否 | 可选，文章筛选 |
| `status` | integer | 否 | 可选，0/1/2；含义见该业务状态约定 |

#### 请求示例

```http
GET /api/v1/blog/me/comments?page=1&pageSize=10
Cookie: __Host-dl-blog-session=<opaque>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [CommentOwn](#response-commentown)；分页字段见 [0.2](API.md#pagination)。

#### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "commentId": "201", "articleId": "101", "userId": "12", "parentId": null, "content": "感谢分享。",
        "articleTitle": "秋日笔记", "articleSlug": "autumn-notes",
        "rootParentId": null, "page": 2, "status": 1
      }
    ]
  },
  "errors": []
}
```

每页最多 50；createTime DESC,commentId DESC。下架文章的本人记录仍可管理。记录内的 `page` 按当前登录用户看到的文章评论根列表计算，每页固定 10 条，与本人列表的筛选和分页参数无关。回复使用所属根评论的页码。目标未通过审核、父链不可见或文章不公开时，`page` 为 null。

---

### 本人评论详情

| 方法与路径 | 权限与返回 |
| --- | --- |
| `GET /api/v1/blog/me/comments/{commentId}` | 登录，仅本人；200，data为CommentOwn，保留X-Revision响应头；文章下架后仍可读取 |

评论发表后不可修改，待审、通过和驳回状态均相同。本人详情只读，不要求额外查询权限，非本人返回404。原本人评论PUT接口已移除；对详情路径发送PUT返回405，不接受正文覆盖。本人仍可查询和删除，后台保留审核与删除。

---

<a id="api-62"></a>

### 2.7.2 删除本人评论

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/blog/me/comments/{commentId}` |
| 接口描述 | 删除本人评论 |
| Content-Type | 无请求体 |
| 访问要求 | 登录 + `blog:comment:deleteOwn`；仅本人 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `commentId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
DELETE /api/v1/blog/me/comments/201
Origin: https://blog.example.com
Cookie: __Host-dl-blog-session=<opaque>
X-Expected-User-Id: 12
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

目标评论必须为本人，deleteOwn不受ALL扩大；核对归属后在同一事务中物理删除目标和全部层级子回复，子回复不按作者或审核状态过滤。祖先和兄弟分支保留；重复删除或目标不存在返回404。

---

<a id="blog-group-8"></a>

## 2.8 本人留言

留言发表后不可修改，待审、通过、驳回及悄悄话均相同。原本人留言PUT接口已移除；对详情路径发送PUT返回405。正文、格式、通知意愿、私密设置和父关系在发表后不能再次提交修改。保留本人查询和删除、前后台回复及后台审核。`editedAt`仅保留历史值，新留言为null。

<a id="api-111"></a>

### 2.8.1 分页查询本人留言

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/me/messages` |
| 接口描述 | 分页查询本人留言 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认1，1..100000 |
| `pageSize` | integer | 否 | 默认10，1..100 |
| `status` | integer | 否 | 0/1/2 |

#### 请求示例

```http
GET /api/v1/blog/me/messages?page=1&pageSize=10
Cookie: __Host-dl-blog-session=<opaque>
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
        "messageId": "301", "parentId": "302", "rootParentId": "290", "page": 2,
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

仅user_id=当前用户，包括待审、驳回和私密记录；createTime DESC,messageId DESC。无额外后台查询权限要求。

记录内的 `page` 按当前登录用户看到的留言板根列表计算，每页固定 10 条，与本人列表的筛选和分页参数无关。回复使用所属根留言的页码。目标未通过审核或父链对当前登录用户不可见时，`page` 为 null。

---

<a id="api-112"></a>

### 2.8.2 查询本人留言详情

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/me/messages/{messageId}` |
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
GET /api/v1/blog/me/messages/301
Cookie: __Host-dl-blog-session=<opaque>
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

他人留言一律404；只读查询保留X-Revision响应头，不提供修改接口。

---

<a id="api-114"></a>

### 2.8.3 删除本人留言

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/blog/me/messages/{messageId}` |
| 接口描述 | 删除本人留言 |
| Content-Type | 无请求体 |
| 访问要求 | 登录 + `blog:message:deleteOwn`；始终限制本人归属 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `messageId` | ID | 是 | Path；十进制正整数 ID 字符串 |

#### 请求示例

```http
DELETE /api/v1/blog/me/messages/301
Origin: https://blog.example.com
Cookie: __Host-dl-blog-session=<opaque>
X-Expected-User-Id: 12
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

无If-Revision，与本人评论删除一致；核对目标归属后在同一事务中物理删除目标及全部层级子回复，包括其他作者、私密和各审核状态的后代，不保留占位。祖先和兄弟分支保留；他人、目标不存在或重复删除返回404。

---

<a id="blog-group-9"></a>

## 2.9 本人点赞记录

<a id="api-158"></a>

### 2.9.1 分页查询本人点赞的文章

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/me/liked-articles` |
| 接口描述 | 个人中心回看自己点赞过、当前仍公开的文章 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人且账号启用，无额外后台权限 |
| 实施状态 | 已实现 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | integer | 否 | 默认 1，范围 1..100000 |
| `pageSize` | integer | 否 | 默认 10，范围 1..100 |

#### 请求示例

```http
GET /api/v1/blog/me/liked-articles?page=1&pageSize=10
Cookie: __Host-dl-blog-session=<opaque>
```

#### 响应参数

`data`：分页对象，`records[]` 为 [LikedArticle](#response-likedarticle)；分页字段见 [0.2](API.md#pagination)。

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

<a id="blog-group-10"></a>

## 2.10 本人邮件订阅

<a id="api-126"></a>

### 2.10.1 查询本人邮件订阅

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/me/rss-subscription` |
| 接口描述 | 查询本人邮件订阅 |
| Content-Type | 无请求体 |
| 访问要求 | 登录本人 |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
GET /api/v1/blog/me/rss-subscription
Cookie: __Host-dl-blog-session=<opaque>
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

### 2.10.2 申请或重发本人邮件订阅

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `PUT /api/v1/blog/me/rss-subscription` |
| 接口描述 | 使用当前登录账号邮箱申请或重发邮件订阅 |
| Content-Type | application/json |
| 访问要求 | 登录 + `blog:rss:subscribe`；始终限制本人归属 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `email` | string | 是 | 合法邮箱≤254；trim并转小写后必须等于当前登录用户的 `sys_user.email`；不得指定其他邮箱 |

#### 请求示例

```http
PUT /api/v1/blog/me/rss-subscription
Origin: https://blog.example.com
Cookie: __Host-dl-blog-session=<opaque>
X-Expected-User-Id: 12
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

订阅页面的邮箱只读，固定显示当前登录账号邮箱，不能在订阅表单中修改。服务端锁定当前用户后读取并规范化 `sys_user.email`，请求邮箱不一致返回400/VALIDATION_ERROR，账号未设置邮箱返回409/STATE_CONFLICT，均不修改订阅或发信；需要先在个人资料中设置账号邮箱。实际保存和发信地址由服务端账号资料决定，不能通过篡改请求订阅其他邮箱。

首次申请，或账号邮箱修改后重新申请且与历史订阅邮箱不同时，写status=0、verifiedAt/subscribeTime/unsubscribeTime=null，旧邮箱推送立即停止；同一已激活邮箱幂等，不重复发信。历史订阅不会仅因资料修改而自动迁移，须用当前账号邮箱重新申请并收信确认。待验证邮箱重试会重发确认邮件，限流返回429。确认票据24小时有效，绑定用户、subscriptionId、当前email及本次申请版本。SMTP/Redis失败503，已提交待验证记录可保留，通过重发恢复；不返回已激活成功。账号与邮箱唯一冲突409，不挪用其他用户订阅。

---

<a id="api-128"></a>

### 2.10.3 确认本人订阅邮箱

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/me/rss-subscription/confirm` |
| 接口描述 | 确认本人订阅邮箱 |
| Content-Type | application/json |
| 访问要求 | 登录 + `blog:rss:subscribe`；始终限制本人归属 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ticket` | S(512) | 是 | 从确认邮件完成页读取的不透明票据；不得放日志 |

#### 请求示例

```http
POST /api/v1/blog/me/rss-subscription/confirm
Origin: https://blog.example.com
Cookie: __Host-dl-blog-session=<opaque>
X-Expected-User-Id: 12
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

需已登录的同一本站用户；可在另一个浏览器登录同一账号确认，不依赖旧访客存储。锁用户及订阅，验证当前pending状态、邮箱和申请版本，订阅邮箱还须与当前账号邮箱规范化后一致，Redis原子消费后设置verifiedAt、subscribeTime、status=1。过期/复用/错账号、账号邮箱已变化或已被新申请替换统一400/SUBSCRIPTION_TICKET_INVALID。数据库失败回滚，已消费票据需重新申请；不把Redis与MySQL描述为共同事务。

---

<a id="api-129"></a>

### 2.10.4 取消本人邮件订阅

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `DELETE /api/v1/blog/me/rss-subscription` |
| 接口描述 | 取消本人邮件订阅 |
| Content-Type | 无请求体 |
| 访问要求 | 登录 + `blog:rss:unsubscribe`；始终限制本人归属 |

#### 请求参数

无业务参数；鉴权见访问要求。

#### 请求示例

```http
DELETE /api/v1/blog/me/rss-subscription
Origin: https://blog.example.com
Cookie: __Host-dl-blog-session=<opaque>
X-Expected-User-Id: 12
```

#### 响应参数

`data` 固定为 null。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":null,"errors":[]}
```

身份只取会话，无If-Revision。将本人记录置status=2并写unsubscribeTime；不存在或已退订幂等200。保留已验证邮箱信息，重订需走申请及确认。后台ALL不扩大到他人。

---

<a id="blog-group-11"></a>

## 2.11 友链

<a id="api-70"></a>

### 2.11.1 分页查询公开友链

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/links` |
| 接口描述 | 分页查询公开友链 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

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

`data`：分页对象，`records[]` 为 [LinkPublic](#response-linkpublic)；分页字段见 [0.2](API.md#pagination)。

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

以下三个接口均要求有效的博客登录会话和启用的账号，申请归属只取自会话。写请求按[通用协议](API.md#common)校验请求来源和 `X-Expected-User-Id`，查询传入该请求头时也校验。本人申请响应不进入公开缓存。

<a id="link-application-get"></a>

### 2.11.2 查询本人友链申请

`GET /api/v1/blog/me/link-application`。无查询参数和请求体。成功返回 `200`，`data` 为本人的 [Link](API_ADMIN.md#response-link)，没有申请时为 `null`。后台编辑或审核后，本接口返回同一条记录的最新资料和状态。后台删除后，如尚未重新申请，返回 `null`。提交超时后，先用此接口确认结果。

<a id="link-application-create"></a>

### 2.11.3 提交友链申请

`POST /api/v1/blog/me/link-application`。请求使用 `application/json`，无查询参数，仅接受下表字段。

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `linkName` | string | 是 | 友站名称，1..100 字符 |
| `url` | string | 是 | 友站地址，绝对 HTTP(S) URL，最长 2048 字符 |
| `avatar` | string&#124;null | 否 | 公开 HTTP(S) 图片地址，最长 2048 字符，默认 null，见[地址规则](API_CONTRACT.md#model-12-7) |
| `description` | string&#124;null | 否 | 友站简介，纯文本，最长 255 字符，默认 null |

请求示例

```http
POST /api/v1/blog/me/link-application
Content-Type: application/json
Origin: https://blog.example.com
Cookie: __Host-dl-blog-session=<opaque>
X-Expected-User-Id: 12

{"linkName":"示例友站","url":"https://example.com"}
```

成功返回 `201 Created`，`Location` 为 `/api/v1/blog/me/link-application`，`data` 为新建的 [Link](API_ADMIN.md#response-link)。服务端设置 `status=0`（待审核）和 `sortOrder=0`。后台通过[友链状态接口](API_ADMIN.md#batch-links-status)审核，状态变为 `1` 后才进入公开列表。

- 每个账号最多保留一条本人申请。只要上一条仍存在，无论是待审核、展示、驳回还是下架，均返回 `409/STATE_CONFLICT`。申请人删除或后台[删除该友链](API_ADMIN.md#batch-link-delete)后，才可在剩余额度内重新申请。后台直接创建的友链不计入账号申请。
- 每个账号按 `Asia/Shanghai` 自然日最多成功提交 3 次，每天 00:00 重置。删除申请不返还次数。没有现存申请且当天额度用完时，返回 `429/RATE_LIMITED`，`Retry-After` 为距离下次重置的秒数。
- 同一账号的并发提交和删除按顺序生效。检查现存申请、检查额度、新建申请和记录次数须保证一致性，失败不创建申请或扣减额度。

<a id="link-application-delete"></a>

### 2.11.4 删除本人友链申请

`DELETE /api/v1/blog/me/link-application/{linkId}`。`linkId` 为查询或提交响应中的十进制 ID，无查询参数、请求体和 `If-Revision`。只删除本人提交的对应记录，各审核状态均可删除。成功返回 `200`，`data` 为 `null`。若该友链正在展示，删除成功后应从公开列表移除。记录不存在或不属于当前账号时统一返回 `404/NOT_FOUND`，重复删除旧 ID 不影响后来创建的申请。

申请接口的通用错误见[错误码](API.md#errors)。未登录或会话失效返回 `401/UNAUTHENTICATED`，账号禁用返回 `403/ACCOUNT_DISABLED`，写请求缺少预期账号或预期账号与会话不符时返回 `409/SESSION_ACCOUNT_CHANGED`，非法参数返回 `400/VALIDATION_ERROR`。

---

<a id="blog-group-12"></a>

## 2.12 站点配置

<a id="api-76"></a>

### 2.12.1 查询公开站点配置

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/configs` |
| 接口描述 | 查询公开站点配置 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

#### 请求参数

无 Path、Query 或请求体参数；GET/无模型写操作不发送请求体。

#### 请求示例

```http
GET /api/v1/blog/configs
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

仅允许公开的 site 且 isPublic=1；服务端过滤，configKey ASC。

---

<a id="blog-group-13"></a>

## 2.13 主人资料与社交链接

<a id="blog-api-88"></a>

### 2.13.1 查询站点主人公开资料

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/profile` |
| 接口描述 | 查询站点主人公开资料 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

#### 请求参数

无业务参数。

#### 请求示例

```http
GET /api/v1/blog/profile
```

#### 响应参数

`data`：[HomeProfilePublic](API.md#response-homeprofilepublic)，未配置/无记录时可为 null。

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

<a id="blog-api-91"></a>

### 2.13.2 查询公开社交链接

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/social-links` |
| 接口描述 | 查询公开社交链接 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

#### 请求参数

无业务参数。

#### 请求示例

```http
GET /api/v1/blog/social-links
```

#### 响应参数

`data`：[SocialPublic](API.md#response-socialpublic) 数组。

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

<a id="blog-group-14"></a>

## 2.14 背景音乐

<a id="api-120"></a>

### 2.14.1 分页查询公开播放列表

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

`data`：分页对象，`records[]` 为 [MusicPublic](#response-musicpublic)；分页字段见 [0.2](API.md#pagination)。

#### 响应示例

```json
{
  "code": "OK", "msg": "成功",
  "data": {
    "page": 1, "pageSize": 10, "total": "1", "totalPages": "1",
    "records": [
      {
        "musicId": "51", "title": "秋日", "artist": null, "durationSeconds": 180, "coverUrl": null,
        "audioUrl": "/api/v1/blog/music/files/601/content",
        "lyricUrl": "/api/v1/blog/music/files/602/content", "lyricType": "lrc", "sortOrder": 0
      }
    ]
  },
  "errors": []
}
```

仅status=1，sortOrder ASC,musicId ASC；不由服务端代理或抓取音频。是否有歌词由lyricUrl判断，不重复存储hasLyric。

<a id="music-file-content"></a>

### 2.14.2 读取公开音乐文件

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/music/files/{fileId}/content` |
| 访问要求 | 匿名；至少一首展示曲目实际引用此规范路径 |
| Path | `fileId`：十进制正整数ID字符串 |
| Header | `Range` 可选，如 `bytes=0-1023`、`bytes=1024-` 或 `bytes=-1024` |
| 请求体 / Query | 不接受 |
| 响应 | 原文件字节，200完整或206分段；无效范围416 |

```http
GET /api/v1/blog/music/files/42/content
Range: bytes=0-1023
```

成功响应包含实际MIME（文本为UTF-8）、Content-Length、Accept-Ranges、no-store、nosniff及安全编码的inline文件名；206另含Content-Range。只存在隐藏引用或未保存到曲目时404，管理员Token不放宽公开规则。存储不可用503；业务错误为JSON Result，Range错误由Spring MVC处理。后台预览沿用需后台会话的 `/api/v1/admin/files/{fileId}/content`，检查文件query范围。保存音乐及文件删除规则见[音乐模型](API_CONTRACT.md#model-12-11)。

---

<a id="blog-group-15"></a>

## 2.15 邮件订阅管理

<a id="api-130"></a>

### 2.15.1 通过邮件链接退订

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

这是显式公开的票据能力接口；无需登录，也不颁发会话。票据存Redis，有效30天，绑定subscriptionId、email、verifiedAt和本轮subscribeTime；只能将该订阅改为退订。相同有效票据重复退订200，邮箱或订阅轮次变化后400/SUBSCRIPTION_TICKET_INVALID。邮件链接指向固定前端完成页#unsubscribeTicket=...，页面清除fragment并由用户确认后POST；邮件扫描器GET不改变状态。不接受email/userId作为替代凭据。

---

<a id="blog-group-16"></a>

## 2.16 RSS 与站点地图

<a id="api-135"></a>

### 2.16.1 读取公开RSS Feed

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
<rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/"><channel><title>DriftingLeaves</title><link>https://blog.example.test/</link><description>记录与分享</description><item><title>第一片落叶</title><link>https://blog.example.test/articles/first-leaf</link><guid isPermaLink="true">https://blog.example.test/articles/first-leaf</guid><pubDate>Fri, 30 Oct 2026 02:00:00 GMT</pubDate><description>记录与分享</description><content:encoded>&lt;h1 id="第一片落叶"&gt;第一片落叶&lt;/h1&gt;</content:encoded></item></channel></rss>
```

成功Content-Type=application/rss+xml; charset=UTF-8，返回RSS2.0而非Result。取最近20篇PUBLIC文章，publishTime DESC,articleId DESC；guid使用稳定本站文章URL，link使用规范slug，pubDate为RFC822日期。description继续输出转义后的摘要；content:encoded输出经XML转义的正式HTML全文，复用文章详情的Markdown渲染、清理和附件检查，不输出原始Markdown、私密正文或邮箱。标题id按服务端规则生成并保留代码原文；网页使用另一套解析器，支持范围与差异见[Markdown阅读说明](Markdown阅读说明.md)；页内锚点和相对链接补全为文章地址，受管附件使用app.sites.api-base-url（含/api/v1）补全为公开接口地址，仍校验PUBLIC与附件引用。读取RSS不增加文章阅读量。配置的公开博客基址由app.sites.blog-base-url提供，文章前端规范路径为/articles/{slug}，不能信任任意Host头。

---

<a id="api-136"></a>

### 2.16.2 读取公开站点地图

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

<a id="blog-group-17"></a>

## 2.17 统计报表

<a id="api-145"></a>

### 2.17.1 查询公开博客统计

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

仅公开汇总，不接受任意userId或日期探索后台数据；当前未公开文章/分类及待审、私密互动不进入内容汇总。不输出IP、邮箱或UA。当前[公开统计缓存](API.md#public-cache)允许访问PV/UV最多延迟5分钟，内容发布/下架及统计清理主动失效，UTC跨日不复用昨日键。

---

<a id="blog-group-18"></a>

## 2.18 浏览统计采集

<a id="api-137"></a>

### 2.18.1 记录站点页面浏览

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/blog/analytics/page-views` |
| 接口描述 | 记录站点页面浏览 |
| Content-Type | application/json |
| 访问要求 | 公开。提供预期账号时校验有效博客会话，否则按匿名处理 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fingerprint` | string | 是 | ASCII字母数字或连字符16..150；浏览器随机统计标识，可伪造，不能作为身份 |
| `siteCode` | string | 是 | 固定为 `blog` |
| `pagePath` | S(2048) | 是 | 规范站内路径，必须以/起始，剔除fragment及全部query |
| `pageTitle` | S(200)? | 是，可null | 展示标题，不作为鉴权或资源定位依据 |
| `referer` | URL? | 是，可null | 来源URL；服务端再次脱敏并去掉query/fragment |
| `articleId` | ID&#124;null | 是，可null | 仅blog文章详情可传；需PUBLIC且与pagePath的规范slug匹配 |

#### 请求示例

```http
POST /api/v1/blog/analytics/page-views
Origin: https://blog.example.com
Content-Type: application/json

{
  "fingerprint": "d681988a-cbd0-4ef6-bdec-f84bfa234e16", "siteCode": "blog", "pagePath": "/",
  "pageTitle": "DriftingLeaves", "referer": null, "articleId": null
}
```

#### 响应参数

`data`：[TrackingResult](API.md#response-trackingresult)。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":{"recorded":true},"errors":[]}
```

IP/UA、viewTime和userId由服务端读取；不接受visitorId、用户ID、会话ID或Token字段。提供 `X-Expected-User-Id` 时，后端从有效且匹配的博客会话取得用户 ID。省略该头时按匿名采集。事务写访客首次/最近时间及一条page_view后返回recorded=true；失败503不伪造已记录。博客文章详情路径即使提交 `articleId: null`，也会校验该文章是否公开。非公开或不存在返回 404。关联 ID 与路径不匹配返回 400。采集只记录站点浏览，文章累计阅读数由[文章详情接口](#api-38)更新。受限统计标识返回403/VISITOR_BLOCKED，只影响此采集入口；可伪造标识不能用于阻止公开阅读。重复POST可能产生多条浏览，无通用幂等键。

已实现的[采集限流](API.md#request-protection)在访客创建、地域解析与明细写入前执行；429不得写入数据或返回recorded=true。fingerprint只增加统计维度限制，不能代替独立IP限流；本POST响应不缓存。

`siteCode` 与端不一致返回 400/VALIDATION_ERROR。按[同源来源规则](API.md#路径与鉴权)核对 `Origin`，缺失时从 `Referer` 提取来源。来源或路径与站点配置不匹配返回 403/FORBIDDEN。

---

<a id="blog-group-19"></a>

## 2.19 实时在线人数

<a id="api-160"></a>

### 2.19.1 申请一次性在线票据

| 项目 | 约定 |
| --- | --- |
| 接口 | `POST /api/v1/blog/analytics/online/tickets` |
| 身份 | 公开。提供预期账号时校验有效博客会话，否则按匿名处理 |
| 成功 | HTTP 201，`Cache-Control: no-store`，无版本头 |
| 请求体 | `fingerprint`：ASCII `[A-Za-z0-9-]{16,150}`；`siteCode`：固定为 `blog`；`pagePath`：S(2048)站内路径，不能包含查询/fragment |
| 来源 | 按[同源来源规则](API.md#路径与鉴权)核对 Origin，缺失时从 Referer 提取来源，再匹配 `app.sites` 的站点与最长路径前缀 |
| 参数 | 三个 Body 字段均必填；拒绝查询参数、未知/重复字段和类型错误 |
| 限流 | 60秒每可信IP120次、每站点/Origin/fingerprint统计键30次；429带Retry-After |

```http
POST /api/v1/blog/analytics/online/tickets
Origin: https://blog.example.test
Cookie: __Host-dl-blog-session=<opaque>
X-Expected-User-Id: 12
Content-Type: application/json

{"fingerprint":"d681988a-cbd0-4ef6-bdec-f84bfa234e16","siteCode":"blog","pagePath":"/"}
```

浏览器在博客同源 HTTP 请求中自动携带有效 Cookie；匿名请求也可领取票据。

```json
{"code":"OK","msg":"成功","data":{"ticket":"<43-character-single-use-ticket>","expiresIn":30},"errors":[]}
```

票据随机且只用于一次 join，Redis TTL 30 秒，申请本身不增加人数。无预期账号按匿名签票。提供预期账号时，会话无效返回 401，账号禁用返回 403/ACCOUNT_DISABLED，账号不匹配返回 409/SESSION_ACCOUNT_CHANGED。`siteCode` 与端不一致返回 400/VALIDATION_ERROR。来源或路径与站点配置不匹配返回 403/FORBIDDEN。Redis 或身份查询不可用返回 503。

---

<a id="online-websocket"></a>

### 2.19.2 加入原生 WebSocket 并接收快照

`ws(s)://<API主机>/api/v1/blog/ws/online`；从既有 API 地址推导，只映射协议并追加 `/blog/ws/online`。握手只允许精确 GET Upgrade 和明确 Origin，拒绝查询参数、Authorization 和子协议传身份；普通 GET 返回400。握手只使用一次性票据，代理转发 Upgrade 时移除 Cookie。建连后五秒内发送：

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

join 的票据和身份查询异步执行，五秒期限包含查询等待；查询完成后仍须确认连接未关闭、未超时。每个连接最多进行一次在途身份复查。消息按该连接的入队顺序发送，慢查询或慢发送不占用全站成员锁；待发消息超过16KiB时移除该连接并安排1011关闭，不影响其他连接，客户端按既有故障退避规则重新签票。

---

<a id="display-image-content"></a>

## 2.20 读取公开展示图片

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/blog/display-images/{fileId}`，支持 HEAD |
| 访问要求 | 匿名，仅允许通过[公开展示图片上传入口](API_ADMIN.md#display-image-upload)上传的图片 |
| 响应 | PNG/JPEG 原始响应体，含 `Cache-Control: no-store`、`X-Content-Type-Options: nosniff` 和固定的 inline 文件名 |

`fileId` 为十进制正整数 ID。普通附件、不存在和已删除的图片返回 404，存储不可用返回 503。

---

<a id="models"></a>

## 本端响应模型

字段说明见下表，分页规则见[通用协议](API.md#pagination)。

<a id="response-articlepublic"></a>

### 12.14 ArticlePublic

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `articleId` | ID | 文章 ID |
| `author` | object | 作者公开资料 |
| `author.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `author.nickname` | string | 展示昵称 |
| `author.avatar` | string&#124;null | 公开头像 URL，可 null |
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
| `author` | object | 作者公开资料 |
| `author.userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `author.nickname` | string | 展示昵称 |
| `author.avatar` | string&#124;null | 公开头像 URL，可 null |
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
| `markdownContent` | string | PUBLIC 文章的 Markdown 原文；网页 MdPreview 渲染后仍需清理，列表不返回 |
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

<a id="response-tagpublic"></a>

### 12.23 TagPublic

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `tagId` | ID | 标签 ID |
| `tagName` | string | 标签名称 |
| `slug` | string | 稳定 URL 标识，区分大小写 |

<a id="response-commentpublic"></a>

### 12.25 CommentPublic

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `commentId` | ID | 评论 ID |
| `articleId` | ID | 文章 ID |
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
| `replyCount` | string | 当前读者可见的全部后代回复数 |
| `replyTarget` | object&#124;null | 直接回复对象；根评论为null |
| `replyTarget.nickname` | string&#124;null | 回复对象的当前昵称 |
| `replyTarget.content` | string | 回复对象的原文摘要，最多120字符，按文本展示 |
| `replyable` | boolean | 当前评论及全部祖先是否通过审核；发表仍须满足权限与文章开关 |
| `siteAuthor` | boolean | 评论者当前有启用的作者或管理员角色，显示本站作者 |
| `siteAdmin` | boolean | 评论者当前有启用的管理员角色 |

<a id="response-commentown"></a>

### 12.26 CommentOwn

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `commentId` | ID | 评论 ID |
| `articleId` | ID | 文章 ID |
| `userId` | ID | 本站用户 ID；响应为十进制字符串 |
| `parentId` | ID&#124;null | 父节点 ID，根为 null |
| `articleTitle` | string&#124;null | 本人列表中当前公开文章的标题；文章不公开或为本人详情、发表响应时为 null |
| `articleSlug` | string&#124;null | 本人列表中当前公开文章的 slug；文章不公开或为本人详情、发表响应时为 null |
| `rootParentId` | ID&#124;null | 本人列表中回复所属的根评论 ID，按十进制字符串返回；根评论、本人详情及发表响应为 null |
| `page` | integer&#124;null | 本人列表中目标根评论在当前读者文章评论列表的页码，从 1 起，每页 10 条；文章不公开、目标未通过审核、父链不可见或为本人详情、发表响应时为 null |
| `content` | string | 纯文本评论 |
| `location` | string&#124;null | 发布时的省市或国家；回环为本地，未知时可 null |
| `userAgentOs` | string&#124;null | 从请求 User-Agent 识别的操作系统类型，未知为 null |
| `userAgentBrowser` | string&#124;null | 浏览器类型，未知为 null；不公开原始 User-Agent/IP |
| `status` | integer | 当前状态，枚举见对应模型与业务规则 |
| `createTime` | Time | 创建 UTC 时间 |
| `updateTime` | Time | 修改 UTC 时间 |

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
| `avatar` | string&#124;null | 公开头像 URL，可 null |
| `description` | string&#124;null | 说明，可 null；site 内 description 可为空串 |
| `sortOrder` | integer | int32 升序排序值 |

<a id="response-messagepublic"></a>

### 12.41 MessagePublic

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `messageId` | ID | 留言ID |
| `parentId` | ID&#124;null | 直接回复目标；根留言为null |
| `replyCount` | string | 当前读者可见的全部后代回复数量；无回复为"0" |
| `replyTarget` | object&#124;null | 直接回复对象；根留言为null |
| `replyTarget.nickname` | string&#124;null | 回复对象的当前昵称 |
| `replyTarget.content` | string | 回复对象的原文摘要，最多120字符，按文本展示 |
| `replyable` | boolean | 当前留言及全部祖先是否通过审核；发表仍须满足私密范围、权限与开关 |
| `siteAuthor` | boolean | 留言者当前有启用的作者或管理员角色，显示本站作者 |
| `siteAdmin` | boolean | 留言者当前有启用的管理员角色 |
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

<a id="response-musicpublic"></a>

### 12.43 MusicPublic

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

<a id="response-arithmeticcaptcha"></a>

### 12.61 ArithmeticCaptcha

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `captchaId` | string | 43位URL安全随机标识，独立于图形验证码用途 |
| `question` | string | 服务端生成的数字题目，如 `9 × 9 = ?`；不返回答案 |
| `expiresIn` | integer | 固定120秒，第一次校验即消费 |
