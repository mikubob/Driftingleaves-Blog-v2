<a id="client-home"></a>

# 主页端接口

本端接口使用 `/api/v1/home` 前缀。

[本端接口索引](API_INDEX.md#index-home) · [通用协议](API.md#common) · [共享响应模型](API.md#models) · [错误码](API.md#errors)

<a id="home-group-1"></a>

## 3.1 站点配置

<a id="home-api-76"></a>

### 3.1.1 查询公开站点配置

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/home/configs` |
| 接口描述 | 查询公开站点配置 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

#### 请求参数

无 Path、Query 或请求体参数；GET/无模型写操作不发送请求体。

#### 请求示例

```http
GET /api/v1/home/configs
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

<a id="home-group-2"></a>

## 3.2 站长资料与社交链接

<a id="api-88"></a>

### 3.2.1 查询站长公开资料

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `GET /api/v1/home/profile` |
| 接口描述 | 查询站长公开资料 |
| Content-Type | 无请求体 |
| 访问要求 | 公开 |

#### 请求参数

无业务参数。

#### 请求示例

```http
GET /api/v1/home/profile
```

#### 响应参数

`data`：[SiteOwnerProfilePublic](API.md#response-homeprofilepublic)，未配置/无记录时可为 null。

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

<a id="api-91"></a>

### 3.2.2 查询公开社交链接

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

<a id="home-group-3"></a>

## 3.3 浏览统计采集

<a id="home-api-137"></a>

### 3.3.1 记录站点页面浏览

#### 基本信息

| 项目 | 说明 |
| --- | --- |
| 接口路径 | `POST /api/v1/home/analytics/page-views` |
| 接口描述 | 记录站点页面浏览 |
| Content-Type | application/json |
| 访问要求 | 公开，仅匿名 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fingerprint` | string | 是 | ASCII字母数字或连字符16..150；浏览器随机统计标识，可伪造，不能作为身份 |
| `siteCode` | string | 是 | 固定为 `home` |
| `pagePath` | S(2048) | 是 | 规范站内路径，必须以/起始，剔除fragment及全部query |
| `pageTitle` | S(200)? | 是，可null | 展示标题，不作为鉴权或资源定位依据 |
| `referer` | URL? | 是，可null | 来源URL；服务端再次脱敏并去掉query/fragment |
| `articleId` | null | 是 | 本端固定提交 null |

#### 请求示例

```http
POST /api/v1/home/analytics/page-views
Origin: https://home.example.com
Content-Type: application/json

{
  "fingerprint": "d681988a-cbd0-4ef6-bdec-f84bfa234e16", "siteCode": "home", "pagePath": "/",
  "pageTitle": "DriftingLeaves", "referer": null, "articleId": null
}
```

#### 响应参数

`data`：[TrackingResult](API.md#response-trackingresult)。

#### 响应示例

```json
{"code":"OK","msg":"成功","data":{"recorded":true},"errors":[]}
```

IP/UA、viewTime和userId由服务端读取；不接受visitorId、用户ID、会话ID或Token字段。该端只记录匿名访问，不创建sys_user。事务写访客首次/最近时间及一条page_view后返回recorded=true；失败503不伪造已记录。此请求只记录站点浏览，不更新文章阅读数。受限统计标识返回403/VISITOR_BLOCKED，只影响此采集入口；可伪造标识不能用于阻止公开阅读。重复POST可能产生多条浏览，无通用幂等键。

已实现的[采集限流](API.md#request-protection)在访客创建、地域解析与明细写入前执行；429不得写入数据或返回recorded=true。fingerprint只增加统计维度限制，不能代替独立IP限流；本POST响应不缓存。

`siteCode` 与端不一致返回 400/VALIDATION_ERROR。请求须携带本端的 `Origin`。来源或路径与站点配置不匹配返回 403/FORBIDDEN。

---

<a id="home-group-4"></a>

## 3.4 实时在线人数

<a id="home-api-160"></a>

### 3.4.1 申请一次性在线票据

| 项目 | 约定 |
| --- | --- |
| 接口 | `POST /api/v1/home/analytics/online/tickets` |
| 身份 | 公开，仅匿名 |
| 成功 | HTTP 201，`Cache-Control: no-store`，无版本头 |
| 请求体 | `fingerprint`：ASCII `[A-Za-z0-9-]{16,150}`；`siteCode`：固定为 `home`；`pagePath`：S(2048)站内路径，不能包含查询/fragment |
| 来源 | 必须提供 Origin，并匹配 `app.sites` 的所属站点与最长路径前缀 |
| 参数 | 三个 Body 字段均必填；拒绝查询参数、未知/重复字段和类型错误 |
| 限流 | 60秒每可信IP120次、每站点/Origin/fingerprint统计键30次；429带Retry-After |

```http
POST /api/v1/home/analytics/online/tickets
Origin: https://home.example.test
Content-Type: application/json

{"fingerprint":"d681988a-cbd0-4ef6-bdec-f84bfa234e16","siteCode":"home","pagePath":"/"}
```

该端按匿名身份领取票据。示例 Origin 必须在部署中明确允许。

```json
{"code":"OK","msg":"成功","data":{"ticket":"<43-character-single-use-ticket>","expiresIn":30},"errors":[]}
```

票据随机且只用于一次 join，Redis TTL 30 秒，申请本身不增加人数。`siteCode` 与端不一致返回 400/VALIDATION_ERROR。来源或路径与站点配置不匹配返回 403/FORBIDDEN。Redis 不可用返回 503。

---

<a id="home-online-websocket"></a>

### 3.4.2 加入原生 WebSocket 并接收快照

`ws(s)://<API主机>/api/v1/home/ws/online`；从既有 API 地址推导，只映射协议并追加 `/home/ws/online`。握手只允许精确 GET Upgrade 和明确 Origin，拒绝查询参数、Authorization 和子协议传身份；普通 GET 返回400。握手只使用一次性票据，代理转发 Upgrade 时移除 Cookie。建连后五秒内发送：

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
