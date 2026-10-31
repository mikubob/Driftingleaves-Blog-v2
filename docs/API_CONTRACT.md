# 接口开发约定

本文保留接口实现需要的完整输入约束、状态、鉴权、事务与存储规则。日常调用请先阅读 [接口文档](API.md) 或 [接口索引](API_INDEX.md)。章节编号沿用原接口文档；当前共160个已实现HTTP路由，另有1个WebSocket入口；第23章在线能力不改变原有159个业务路由语义。2026-10-31新增的业务查询缓存与补充限流已接入，见[0.8](#cache-contract)；独立验证结果及恢复步骤见运行说明，既有路由数本身不代表缓存验收。历史验收和部署配置见 [后端运行说明](后端运行说明.md)。

- [通用协议与鉴权](#common)
- [实时在线人数：票据、WebSocket 与已实现约定](实时在线人数接口规划.md)
- [缓存、限流与后续验收](#cache-contract)
- [共享模型与状态](#models)
- [OAuth、授权、文件与事务](#business)
- [迁移对照](#legacy-sources)
- [补充设计验收](#planned-acceptance)

---

<a id="common"></a>

## 0. 通用协议与鉴权

### 0.1 路径、类型与输入

- 根路径 `/api/v1`。`/auth` 身份流程，`/me` 操作本人，`/blog` 博客前台，`/home` 主站展示，`/cv` 简历展示，`/analytics` 公开访问采集及在线票据，`/admin` 后台。下文基本信息与请求示例均使用含此前缀的完整调用路径。
- HTTPS；JSON 请求/响应 `application/json; charset=UTF-8`，属性 camelCase。设计决定JSON请求体上限8MiB（正文另有解码后1MiB限制）。文件上传为 multipart，文件内容和 OAuth 回调例外见各节。无请求体标为 `—`，不得用 GET 改变业务状态（OAuth 回调和阅读计数按各节约定）。
- `ID` 是十进制字符串，范围 `1..9223372036854775807`，拒绝 JSON 数字、负数、小数和前导零；所有主键、外键和 ID 数组均如此。非 ID 的 BIGINT 计数、字节数也用十进制字符串输出，避免精度丢失；分页统计同样见下表。
- `Time` 是 UTC、毫秒精度的 RFC 3339 字符串，如 `2026-10-29T02:00:00.000Z`。应用、连接统一使用 UTC，DATETIME(3) 按 UTC 存取；前端再转本地时区。请求中的审计时间和发布时间均不可由客户端写入；仅查询条件及明确声明的简历日期、建站日期、访客封禁到期时间可输入。
- 字符串长度按 Unicode 字符计数，密码另按 UTF-8 字节约束。除密码/Markdown 外，文本先去首尾空白；可空字符串的空串归一为 null，非空字段拒绝空白。简介/描述为纯文本。未知 JSON 字段、无效枚举、错误类型、重复 JSON 属性均返回 `400/VALIDATION_ERROR`，禁止把 DTO 直接批量映射到实体。
- `S(n)` 表示长度 1..n 的非空字符串，`S0(n)` 表示长度 0..n；`?` 表示可为 null；`[]` 表示数组。模型表内未标 `?` 的响应字段必有且非 null；列表无数据为 `[]`，不为 null。
- `URL?` 最大 2048 字符；外部 URL 默认只接受绝对 http/https（社交mailto链接的明确例外见12.9），不接受 userinfo、控制字符、javascript/data/file 协议。本站文件 URL 仅接受[文件与附件](API.md#group-11)生成的路径。写 URL 不触发服务端抓取。头像/封面只能使用已公开的资源，不能借展示字段公开私密附件。
- `slug` 为 ASCII `[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*`，文章最长 200，分类/标签最长 100；区分大小写，不自动转小写。名称/username/email 按库 `utf8mb4_0900_ai_ci` 唯一性判断，可能连重音差异也视为相等；不能仅靠前端查重。
- 本文所有“必填”字段均不允许缺失或 null，除非显式写 `?`。PUT 是完整替换该请求模型，可空字段也必须出现；POST 的可选默认值逐项列出。PATCH 仅用于本人资料，缺失保持、null 清空可空字段。
- 查询参数仅接受各接口声明的字段，每个字段只传一个非空白值；未知、重复或空白参数返回400，不静默忽略或套用默认值。未声明请求体的入口拒绝实际内容；OAuth回调允许平台错误说明，但不回显、不参与身份判断。JSON字段白名单、查询参数白名单与业务权限/SQL范围各自生效。

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

全部入口、窗口、独立/共享维度及当前实施状态统一见[接口限流表](API.md#request-protection)，避免在不同章节维护不一致阈值。缓存命中请求也限流，匿名与登录用户访问公开接口都不能绕过IP窗口；不按查询条件创建新的限流预算。Redis限流沿用固定窗口，拒绝时不延长窗口，Lua原子累计多个维度并设置过期；复用现有RedisRateLimiter、AuthRateLimiter及RateLimitAspect，不引入Bucket4j本地限流或第二套认证计数。新增窗口键与现有失败登录、密码、订阅等业务窗口分开，创建/编辑留言共享窗口按表中要求扩展。已经执行到限流阶段的请求即使业务随后失败也不返还配额；登录失败窗口仍只累计AUTH_FAILED，新增登录总请求窗口不改变此语义。

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

<a id="cache-contract"></a>

### 0.8 Redis业务缓存、事务失效与限流验收

本节为当前实现契约。已增加 RedisCacheManager、Spring Cache 注解及缺失限流入口，只新增 server 的 Boot cache starter，不新增路由或表。[业务缓存表](API.md#public-cache)是缓存范围及TTL的统一来源；[限流表](API.md#request-protection)是入口与阈值的统一来源。缓存只减少公开读取的重复工作，不能接管本站身份、安全票据或业务写入的正确性。

#### 0.8.1 参考依据与适配取舍

只读参考项目为 `D:/CodingFiles/Blog/fet/FeiTwnd-Website`，下表文件路径相对其 `Backend/FeiTwnd-server/src/main/java/cc/feitwnd/`。参考的是实际源码中的业务覆盖与缓存生命周期，不迁移其鉴权、部署值或凭据。

| 参考源码与行为 | V2采用方式 |
| --- | --- |
| `config/RedisConfiguration.java`：RedisCacheManager按业务设置TTL；静态展示1小时、分类标签/归档30分钟、列表10分钟、详情15分钟、统计5分钟、RSS/地图30分钟 | 沿用业务分区与主要TTL；V2搜索缩短为60秒，新增热门榜60秒、公开评论30秒及公开作者缓存，见业务缓存表 |
| `service/article/impl/ArticleServiceImpl.java`：按slug/分页/分类/标签/关键词缓存，文章增删改清理关联缓存 | 缓存键必须覆盖V2全部组合条件，含authorId、month、parentId及分页；失效还覆盖PUBLIC、回收/恢复/永久删除、祖先审核及用户公开资料变化 |
| `service/profile/impl/*ServiceImpl.java`、`service/system/impl/SystemConfigServiceImpl.java`：读缓存，后台写入清理对应业务缓存 | 仅迁移公开展示缓存；不照搬后台all结果缓存，comment/message写策略仍锁内实时读库 |
| `controller/blog/RssFeedController.java`、`SitemapController.java`：缓存XML | 缓存经过PUBLIC过滤与编码的XML；地图索引、各子图及站点基址分别识别，关联变更统一失效 |
| `controller/blog/ArticleController.java`：搜索及详情限流，详情缓存与阅读计数分开 | 沿用分开处理的职责；用本站Redis固定窗口代替参考项目进程内Bucket4j；不移植访客Token、30分钟阅读去重或Redis增量定时回写 |

<a id="reference-cache-eviction"></a>

##### 写入清理源码对照

参考项目不只是给读取加缓存，也在写方法声明 `CacheEvict(allEntries=true)`，多个缓存域通过 `Caching(evict=...)` 一起处理；内部直接写库的路径还会调用CacheManager清理。下表记录本次只读核对到的实际方法与缓存名，不能把本站补充要求说成参考项目已经具备的能力。

| 参考文件与写方法 | 源码明确清理的缓存域 | 本站吸取的经验及补齐范围 |
| --- | --- | --- |
| `service/article/impl/ArticleServiceImpl.java`：createArticle、updateArticle、batchDelete、publishOrCancel | articleList、articleDetail、articleArchive、blogReport，均为allEntries=true | 新增、修改、删除、发布/下架都联合清理，不能只删除当前文章详情；V2另补筛选计数、作者、热门榜、RSS/地图及可见性关联 |
| 同文件：toggleTop | articleList、articleDetail | 排序字段变化也会影响列表与详情；V2置顶通过既有文章PUT更新，沿用文章清理清单，不新增置顶接口 |
| `service/article/impl/ArticleCategoryServiceImpl.java`：addCategory、updateCategory、batchDelete | articleCategories、articleList、blogReport | 字典写入清自己及依赖业务；V2还要清嵌入分类资料的详情、旧/新slug与隐藏分类筛选 |
| `service/article/impl/ArticleTagServiceImpl.java`：addTag、updateTag、batchDelete | articleTags、blogReport | 标签写入不能仅清标签域；V2额外覆盖文章列表/详情中的当前标签及级联关联变化 |
| `service/profile/impl/PersonalInfoServiceImpl.java`：updatePersonalInfo | personalInfo | 清整个主页资料域，不能只清某一种返回模型 |
| `service/profile/impl/SocialMediaServiceImpl.java`：addSocialMedia、updateSocialMedia、batchDelete | socialMedia | 新增/修改/删除均清理整个链接域，覆盖visible及所有读取变体；V2只缓存公开变体 |
| `service/profile/impl/ExperienceServiceImpl.java`：addExperience、updateExperience、batchDelete | experiences | 按类型读缓存时，类型变化必须同时影响旧、新类型；采用整个经历域失效防漏删 |
| `service/profile/impl/SkillServiceImpl.java`：addSkill、updateSkill、batchDelete | skills | 技能增删改清整个技能域，涵盖公开展示结果 |
| `service/profile/impl/FriendLinkServiceImpl.java`：addFriendLink、updateFriendLink、batchDelete | friendLinks | 友链增删改清整个域；V2审核、隐藏与排序通过同一写入口清理 |
| `service/profile/impl/MusicServiceImpl.java`：addMusic、updateMusic、batchDelete | musicList | 音乐增删改清整个播放列表域，包含排序与显示状态变化 |
| `service/system/impl/SystemConfigServiceImpl.java`：addConfig、updateConfig、batchDelete | systemConfig | 更新/删除会使按configKey读取的旧值失效；V2只允许已有白名单键PUT，不照搬任意配置增删接口 |
| `service/async/impl/SummaryBackfillAsyncServiceImpl.java`：backfillSummaryAsync成功写库后调用evictArticleCaches | 通过CacheManager逐个clear articleList、articleDetail、articleArchive、blogReport | 内部写库同样清缓存，不能只覆盖HTTP写请求；V2本次不引入AI或异步摘要功能 |

源码检索中RSS/地图存在Cacheable和TTL配置，但上述写方法的清理列表未覆盖rssFeed/sitemap；分类/标签写入也未统一覆盖嵌入字典资料的文章详情。本站必须按实际响应依赖补齐这些失效，不原样复制遗漏。参考CacheManager未显式开启transactionAware，写方法注解也不能单凭形式证明事务提交后才清理；本站以0.8.4的提交、回滚、并发回填规则为准，不把注解调用结束视为事务一致性验收。

本站每个写接口的新增、更新和删除清理范围集中列于[接口缓存生命周期与清理清单](API.md#cache-lifecycle)，下文0.8.3提供依赖依据，0.8.4规定执行顺序；三者必须一起落实。

本站阅读量继续表示成功详情请求次数，不变成独立访客数。MySQL仍保存已提交的阅读/点赞/评论明细及计数事实；公开列表和详情不因缓存返回过期互动计数。仅排行榜的热度快照及公开统计PV/UV允许业务缓存表明确的短暂延迟，后台报表仍实时读库。

#### 0.8.2 缓存位置、键与公开范围

- 在现有公开查询Service接入Spring Cache注解，Controller仍负责入口和响应，Mapper继续负责数据库读取；复用现有Redis连接与JSON能力，不能为缓存新建平行业务Service或修改对外响应结构。简单缓存必须采用下述注解方式，统一落实事务与并发要求，不逐接口编写RedisTemplate的get/set/delete流程。
- 键统一在已配置的 `app.auth.redis-prefix` 下隔离为 `cache:public:<结构及渲染规则版本>:<业务名>:<业务代次>:<规范查询摘要>`；不得使用会话/OAuth/订阅/rate命名空间，不把Token、票据、邮箱或Markdown原文拼入键。代次是服务端内部失效标识，与客户端X-Revision无关；不能重用已失效代次。
- 先按现有DTO规则校验并补齐默认值，再按固定字段顺序生成键。文章查询包含page/pageSize、keyword、categoryId、tagId、authorId、month；评论包含articleId、parentId及分页；其他列表包含各自全部已声明筛选，XML包含索引/子页区别；排行榜包含period/metric/limit及适用UTC月份，公开统计包含UTC自然日。不得擅自小写化slug或改变关键词的既有匹配语义。
- 缓存值仅为明确的公开VO或公开查询片段，不保存后台实体、密码哈希、未公开邮箱、IP/UA、权限快照、私密或待审原文；主页明确公开的publicEmail仍按原白名单返回。只返回共同公开字段的接口可供匿名和登录请求复用同一数据缓存；登录校验、响应头与私人字段不能混入缓存。`/blog/messages`即使匿名请求也不缓存整页，以免以后复用时混入isOwn或悄悄话。
- PUBLIC以及字典显示、完整评论祖先链、文件引用等现有条件继续生效。文章详情命中仍在原有事务中校验当前PUBLIC、当前内容及引用；只有内容与渲染规则匹配才复用正文片段。列表/导航/推荐不能依靠旧候选ID向客户端返回已下架内容，也不能事后简单滤掉记录却保留错误total或漏补分页；无法确认缓存集合有效时整次查询回源。
- 详情正文缓存与阅读递增分开，不能给含incrementViews的publicDetail整体加Cacheable。列表/详情的viewCount、likeCount、commentCount在本次数据库读取中补充；详情只有公开读取整体成功才在原文章锁内加一次阅读，失败回滚。旧数据库html_content不作为可信缓存；服务端渲染规则升级必须换缓存版本或失效旧HTML。
- 合法空页/空集合可以按同样的失效规则缓存；失败、404、异常结果和未验证的外部输入不缓存。每个数据条目必须有有限TTL，命中不续期；列表和搜索只缓存业务表规定的前几页，深页和其他合法pageSize回源，不新增400或截断结果。本站不引入缓存预热、布隆过滤器、多级本地缓存或异步计数系统。

<a id="spring-cache-annotations"></a>

##### Spring Cache注解方式

简单缓存统一选择Spring Cache声明式缓存，由RedisCacheManager提供Redis存储；不新增自定义缓存注解或独立缓存Service。下表已接入实际 Service 与配置，真实隔离验收记录见运行说明。

| 场景 | 指定方式 |
| --- | --- |
| 启用和统一装配 | 在server配置层使用 `@EnableCaching`，装配 `RedisCacheManager`；复用本站连接，按0.7各业务配置TTL、本站前缀与明确的公开VO序列化，不使用默认永久缓存或本地Map替代Redis |
| 简单公开查询 | Service实现类上经Spring代理从外部调用的public查询方法使用 `@Cacheable(cacheNames="业务名", key="明确的SpEL键")`；无参数公开查询使用固定公开键，有参数查询包含实际全部筛选条件；真实键仍遵循上文版本及代次规则 |
| 条件与空值 | 用 `condition` 限定0.7规定的可缓存页码及pageSize；需要排除null时使用 `unless="#result == null"`，缓存管理器也禁用null缓存；正常空集合可以缓存，不把异常或Result错误包装作为可缓存业务值 |
| 新增、修改、删除及状态变更 | 在实际写Service方法上使用 `@CacheEvict`；简单列表业务采用 `@CacheEvict(cacheNames="业务名", allEntries=true)`，一次清掉该域所有分页/类型/公开变体；只有可准确定位且不影响集合的单条缓存才用key定点删除 |
| 多域关联清理 | 同一个写方法使用 `@Caching(evict={...})` 组合各域的 `@CacheEvict`；文章、分类、标签按接口0.8.2逐项声明关联域，主页/社交/简历/友链/音乐等只声明实际受影响的业务域 |
| 写入后缓存内容 | 采用删除后由下一次查询重建，不使用 `@CachePut` 更新公开DTO；不把写请求缺少的关联资料、PUBLIC条件和服务端派生字段当成完整缓存值 |

注解的命中会跳过查询方法体，因此鉴权、限流、当前PUBLIC检查及阅读计数必须放在仍会执行的外层流程；只给可以复用的纯查询部分加Cacheable，不能给publicDetail等带副作用的整体业务方法加缓存。同类内部直接调用不会经过默认Spring缓存代理，不能把注解只放在private方法或仅被self-invocation调用的方法上并宣称缓存生效。

写清理保留 `beforeInvocation=false`，不在执行数据库写入前无条件删除缓存；RedisCacheManager启用 `transactionAware()` 配合现有事务，落实成功提交后清理。注解声明、事务感知以及0.8.4的并发回填/故障要求必须同时成立，不能把默认CacheEvict的方法返回时机等同数据库提交。`allEntries=true`由统一缓存装配落实业务域失效；需要实际批量删除时使用SCAN批次策略，避免默认KEYS扫描。业务方法不手工重复执行同一份注解清理。

注解语义与代理规则依据[Spring声明式缓存官方文档](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html)；RedisCacheManager、按域TTL、事务感知及批量清理配置依据[Spring Data Redis官方文档](https://docs.spring.io/spring-data/redis/reference/redis/redis-cache.html)。适配仓库 Spring Data Redis 4.1.2，使用 TtlFunction API，按业务配置有限 TTL。

#### 0.8.3 写入影响与主动失效

以下失效由实际业务变化触发，覆盖该业务缓存的全部分页和组合查询。可以清理单条或更换业务代次来使旧条目不可达；不要在请求中使用Redis KEYS扫描全库，也不要为每次阅读清空整站缓存。

单条缓存删除与业务域失效均由真正执行写事务的Service发起，不在Controller里按客户端DTO猜测关联。修改/删除前在原锁内保存旧slug、旧分类/标签等清理所需信息，提交后同时覆盖旧、新关联；不在删除后为了找旧键再查询已不存在的对象。按类别/标签/作者/关键词/月/分页保存的列表无法只凭一个主键枚举全部键，应按对应公开业务域整体失效；文章详情域整体失效也覆盖其他文章中缓存的上下篇与推荐。参考项目的allEntries清理经验在本站按此执行，不新增面向前端的清缓存接口。

| 实际变更来源 | 必须失效的公开缓存 |
| --- | --- |
| 文章创建/编辑、发布/下架、回收/恢复/永久删除；正文、标题、slug、摘要、封面、作者关联、分类/标签关联、置顶、可见性变化 | 公开文章列表/搜索、相关详情及上下篇/推荐、归档、热门榜、分类/标签列表与详情计数、公开作者列表/详情、公开统计、RSS及全部地图索引/子页，以及该文章全部公开评论页；旧slug和新slug均覆盖 |
| 分类或标签增删改、重命名、slug、排序及分类显示状态变化 | 对应字典列表/详情，以及嵌入当前字典或依赖关联筛选的文章列表/搜索/详情、归档、热门榜、公开作者/统计、RSS/地图；包括外键SET NULL/CASCADE造成的关联变化 |
| 用户本人资料PATCH或后台修改用户昵称、头像、简介等公开字段 | 公开作者列表/详情、含该作者资料的文章列表/搜索/详情及公开评论；不能只失效后台用户缓存；账号状态是否影响文章PUBLIC仍按原业务定义判断，不擅自隐藏其作品 |
| 评论创建、审核、本人删除、管理删除及任一祖先状态变化 | 对应文章的全部公开评论页与回复页；列表/详情的评论数继续实时补充，不因一条评论清空所有正文缓存 |
| 点赞/取消点赞或一次成功详情读取 | 不失效正文；本次列表/详情互动数由数据库读取；热门榜快照最多60秒后重建，不引入Redis计数副本 |
| 主页、社交链接、经历、技能、友链、音乐的增删改、审核、排序或显示变化 | 对应公开业务全部查询；公开资料被隐藏或删除后，成功写入后的请求不能继续读到旧展示 |
| site配置内容/公开标记变化，或服务端站点基址、渲染规则改变 | 公开配置及依赖它的RSS/地图/HTML；部署配置或渲染规则变更应同时切换相关缓存版本，不能继续使用旧基址或旧清理规则的结果 |
| 新访问采集 | 不逐条清空公开统计，PV/UV最多5分钟延迟；UTC跨日使用新日期键，不能把昨日todayPageViewCount带到今日 |
| 后台删除浏览记录或访客统计记录 | 公开统计；保留“删除访客使UV减少但PV不变、删除明细不反扣文章阅读量”的原口径 |
| 角色/权限/菜单/授权、密码或第三方绑定变更；comment/message策略变化 | 继续现有会话撤销、实时授权和配置行锁；本节不新增权限、菜单、写策略或私人响应缓存，公共内容缓存不能替代锁后复核 |

不缓存未提交的事务结果。成功写入返回后开始的查询必须看见新公开状态、资料及集合；仅延迟清理旧键或给普通CacheEvict增加TTL不能满足此要求。特别要覆盖“读请求持有旧数据库快照，写请求清缓存后，旧读再把旧值填回”的路径。

#### 0.8.4 事务、并发回填与故障处理

为满足现有下架/私密/删除立即生效要求，采用业务代次和写入保护标记控制缓存使用，复用现有事务完成回调；这里的标记仅控制是否使用缓存，不承担业务行锁或授权。

1. 公开数据写事务在原有锁内校验通过、实际修改前，先登记受影响业务的写入保护标记；每笔事务使用自己的标识，多笔并发写不能互相清掉保护。登记失败返回503并回滚，不在无法保证缓存失效时继续提交公开数据修改。
2. 保护标记存在时，相应公开读取直接查数据库且不回填缓存；数据回源仍执行全部可见性、计数和事务规则。缓存未命中时须在建立用于回填的数据库读取快照前记住当前业务代次，查询完成后原子确认“代次未变且无写入保护”才能回填，避免旧快照在失效之后复活；不能先持有旧快照再读取新代次并回填。
3. 数据库提交后，使受影响业务旧代次失效，再移除本次保护标记；在这两个步骤确认完成后返回写入成功。数据库回滚则清除本次标记，不把未提交结果放入缓存；缓存操作失败不能吞掉原业务异常。旧代次条目按原TTL自然回收。
4. 保护标记不能仅因短TTL到期就恢复旧缓存。进程中断或事务完成后的Redis清理失败时，保留保护、继续绕过该业务缓存；待确认数据库事务已结束并轮换该业务代次后清理遗留标记。Redis恢复后也不得直接放行旧条目；从备份恢复Redis时，应在启用查询缓存前统一轮换公共业务代次。这里允许暂时降低命中率，不允许恢复已下架内容。
5. 提交后的缓存失效失败返回503时，数据库及同事务成功审计可能已经提交，不能声称已回滚，也不能自动重放业务写入；客户端先查询最新状态/版本再决定重试。MySQL与Redis没有共同事务，本节不引入跨存储事务或后台可靠消息系统。
6. 纯缓存读取/回填失败且本次限流、会话和其他必要校验已经通过时，可直接返回数据库查询结果且不使用旧缓存。Redis限流、会话或一次性票据校验失败依旧返回503；不得降级为匿名、成功空页或已记录采集。读取缓存结构不兼容时视为未命中并按当前规则回源，不返回未验证对象。

#### 0.8.5 实现验收清单

以下为实现验收要求，实际执行结果以[运行说明](后端运行说明.md#spring-cache-redis)和对应日志为准；复用现有 JUnit/MVC、真实隔离 MySQL/Redis，不连接业务数据库。

| 范围 | 必须验证的真实行为 |
| --- | --- |
| 命中、TTL与查询键 | 首次回源、再次命中、到期重新读取；数据键均有限TTL且命中不续期；默认参数等价，作者/分类/标签/关键词/月/parentId/分页/大小写slug互不串值；空结果新增后失效；深页及超出缓存pageSize正常查询 |
| Spring Cache注解接入 | 经容器代理调用真实Service，验证Cacheable第二次不重复查询、CacheEvict在增删改提交后清理、Caching覆盖全部关联域；回滚不发布未提交值，同类内部调用不能冒充注解命中；验证Redis中实际条目和TTL，不只检查源码存在注解 |
| 当前鉴权 | 过期/撤销Token和禁用账号仍按原入口规则拒绝；ALL/SELF及锁后权限不从缓存读取；管理员Token不扩大PUBLIC；本人点赞、私密留言、订阅、后台详情及文件响应不进入公共缓存 |
| 关联失效 | 各类写事务后验证上表所有受影响业务，包含旧/新slug、字典级联、修改作者昵称、评论祖先审核/占位、RSS/地图所有子页与total；统计清理及UTC日/月边界不复用旧结果 |
| 写接口逐项覆盖 | 对照接口0.8.2逐行验证POST新增、PUT/PATCH更新、DELETE及状态写入后的清理；先预热所有受影响查询再执行写入，确认无旧值、错误total或旧slug，下一次读取回源重建，随后才命中；未发生变化的幂等写不跳过遗留保护恢复 |
| 副作用与计数 | 详情冷/热缓存都只在成功时加一次阅读，失败不计；采集POST不重复加文章阅读；列表/详情实时互动数不被缓存覆盖；热门榜和公开PV/UV仅在规定时间内延迟，排名与分数快照一致 |
| 并发及故障 | 旧快照回填遇到代次变化被拒；写保护期间旁路；并发写不误清标记；回滚、提交后Redis故障、进程中断遗留标记、Redis恢复不暴露旧PUBLIC数据；缓存降级不能绕过安全Redis失败 |
| 限流 | 每个新增入口的阈值前后边界、共享窗口、独立IP/用户维度、并发原子计数、TTL不被拒绝请求续期；换slug/关键词/日期/fingerprint不重置IP预算；登录成功也消耗总请求预算，AUTH_FAILED单独计失败；冷/热缓存、不同实例共享限制；429含Retry-After且无写入/消费/邮件/计数副作用 |

实现与运行说明同步维护；当前159个路由数、历史测试记录及参考项目存在Cacheable均不能代替本节真实行为验收。

---

<a id="models"></a>

## 12. 共享模型与状态

<a id="model-12-1"></a>

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
| Session | tokenType/accessToken/expiresIn/user，完整约束见0.4；只在本站登录成功返回 |
| Provider | source:string、displayName:string；只列配置完整且启用的平台，不含应用凭据 |
| OAuthAuthorize | 请求仅operation:LOGIN或BIND；响应authorizationUrl:string、expiresIn:number（state有效秒数），平台/回跳地址由服务端配置 |
| OAuthExchange | 仅ticket:string必填，43位ASCII `[A-Za-z0-9_-]{43}`；结果使用下述OAuthResult联合响应 |

注册不收 email/avatar/roleIds/status，服务端固定启用并分配 member；注册仅返回 Me，不自动登录。邮箱在本站仅是可选联系资料，未验证，不能当登录、找回密码或自动合并依据。系统不得返回 passwordHash/password_hash/salt，包括管理员接口。

`OAuthResult` 为联合响应：LOGIN 只有 operation/session，BIND 只有 operation/binding。OAuth 票据和第三方 access_token 不得当成本站 Bearer。

<a id="model-12-2"></a>

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

<a id="model-12-3"></a>

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

<a id="model-12-4"></a>

### 12.4 文章模型与状态

**所有前台读取统一使用 PUBLIC 条件**：`status=1 AND visibility=1 AND deleted_at IS NULL AND publish_time IS NOT NULL AND publish_time<=服务端当前UTC时间`。详情、列表、搜索、分类/标签筛选、归档、评论列表、点赞和文件文章上下文均如此，持有管理员 Token 不放宽前台过滤。隐藏分类不改变文章发布状态；前台分类列表和按分类筛选不暴露隐藏分类，文章仍可通过公开列表/详情读到，category 返回 null。非公开文章通过后台详情预览。

| 模型/字段 | 类型/必填/约束 |
| --- | --- |
| ArticleWrite.title | S(200)，必填 |
| ArticleWrite.slug | S(200)，必填，同通用 slug；回收站仍占唯一键 |
| ArticleWrite.summary | S0(500)，POST可选默认空串；PUT必填，不能null |
| ArticleWrite.categoryId | ID?，POST默认null；PUT必填；非null必须存在（后台可选择隐藏分类） |
| ArticleWrite.coverUrl | URL?，POST默认null；PUT必填；本站文件按[文件与附件](API.md#group-11)公开资源约束 |
| ArticleWrite.articleType | 1原创/2转载/3翻译，POST默认1；PUT必填 |
| ArticleWrite.originalUrl | URL?，POST默认null；PUT必填；type=2/3必须非空，type=1可null |
| ArticleWrite.visibility | 1公开/2私密，POST默认1；PUT必填 |
| ArticleWrite.isTop | 0/1，POST默认0；PUT必填 |
| ArticleWrite.allowComment | 0/1，POST默认1；PUT必填 |
| ArticleWrite.markdownContent | string 必填，可空串用于草稿；设计决定 UTF-8 最多1MiB，发布必须非空白 |
| ArticleWrite.tagIds | ID[]，POST默认[]；PUT必填；最多20个、不重复、全部存在 |
| ArticleAdminSummary | articleId/authorId:ID、author:{userId:ID,nickname:string}、category:Category?、title/slug/summary、coverUrl:URL?、articleType、originalUrl:URL?、status:0/1/2、visibility:1/2、isTop/allowComment:0/1、viewCount/likeCount/commentCount:string、publishTime/deletedAt:Time?、createTime/updateTime:Time、tags:Tag[] |
| ArticleAdminDetail | ArticleAdminSummary + categoryId:ID?、tagIds:ID[]、markdownContent:string、htmlContent:string（每次按当前规则从Markdown重渲染清理，不信历史HTML缓存） |
| ArticlePublic | articleId:ID、author:{userId:ID,nickname:string,avatar:URL?}、category:CategoryPublic?、tags:TagPublic[]、title/slug/summary、coverUrl:URL?、articleType、originalUrl:URL?、isTop/allowComment:0/1、viewCount/likeCount/commentCount:string、publishTime/createTime/updateTime:Time |
| ArticlePublicDetail | ArticlePublic + htmlContent:string、wordCount:string、readingMinutes:integer、prevArticle/nextArticle:ArticleNavigation?、relatedArticles:ArticleNavigation[]；派生规则见12.15，不返回Markdown源、作者邮箱、后台状态或deletedAt |
| ArticleState | `{status:1或2}`，必填；不接收publishTime |
| Archive | month:string（UTC的YYYY-MM）、articleCount:string（该月PUBLIC文章数）；分页total为月份数，不是文章数 |

tags/tagIds 均按 tagId ASC；commentCount 只统计 status=1 且祖先均为1/3的节点，排除待审/驳回/占位及被隐藏的后代；likeCount 按明细 COUNT，不维护表中不存在的副本。所有资源关联名称为当前资料，非历史快照。ArticlePublic 的 status 隐含已发布，不额外重复字段。

正文仅接收 Markdown，拒绝客户端 htmlContent。CommonMark 渲染后由 jsoup 白名单清理，存可选 html_content 缓存；输出时保证符合当前清理规则，缓存不可信时重新清理。保留基本段落/标题/表格/代码/任务列表，禁止脚本、事件属性、iframe、任意 style 和危险 URL；外链加 noopener/noreferrer。评论为纯文本。本站资源引用规则见[文件与附件](API.md#group-11)；前端不可通过原始 Markdown/raw HTML 绕过此渲染链。

设计决定：POST 固定创建 `status=0` 草稿，作者由登录用户设置；PUT 不收 status/publishTime/deletedAt/计数/authorId。状态只经专用接口变更：草稿0→发布1、发布1→下架2、下架2→发布1；重复目标幂等，无“重新变草稿”。首次发布写当前UTC publishTime，后续发布/编辑不覆盖，禁止未来发布时间/客户端定时发布。发布校验正文、转载来源、关联和可公开资源。修改当前已发布文章的正文/标题/可见性等内容，还须 `blog:article:publish` 并满足其范围，防止利用编辑绕过发布控制。

回收只设 deletedAt，保留原状态/正文/标签/slug/互动；回收站禁止编辑和发布。恢复固定为 `status=2` 下架、deletedAt=null，保留首次publishTime（从未发布过仍null），不会因恢复自动公开；之后显式发布。只读后台详情可查看回收站。

<a id="model-12-5"></a>

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

<a id="comment-rules"></a>

<a id="model-12-6"></a>

### 12.6 评论与点赞模型

| 模型 | 字段与约束 |
| --- | --- |
| CommentCreate | content:string 必填，trim 后1..2000字符纯文本；parentId:ID? 可选默认null；articleId仅取路径，userId取会话 |
| CommentPublic | commentId/articleId:ID、parentId:ID?、author:{userId:ID,nickname:string,avatar:URL?}?、content:string、status:1或3、replyCount:string、createTime/updateTime:Time |
| CommentOwn | commentId/articleId/userId:ID、parentId:ID?、content:string、status:0/1/2/3、createTime/updateTime:Time；不含其他用户私人信息 |
| CommentAdmin | CommentOwn + author:{userId:ID,nickname:string,avatar:URL?}?、articleTitle:string；占位 author=null，用户不物理清除，作者关联仍存在 |
| CommentReview | `{status:1或2}` 必填；1通过，2驳回，无审核备注字段 |
| LikeState | `{liked:boolean,likeCount:string}` |

前台评论只读取 PUBLIC 文章。节点自身及所有祖先须为 status=1 或3；祖先0/2时整个分支不可见。status=3 时 content 固定为“该评论已删除”、author=null，不泄露原内容和作者，replyCount只算当前可见直接子节点。status=0/2 的内容仅本人列表和有相应范围的后台可见；前台公开列表即使带 Token 也不混入自己的待审评论。

设计决定：采用**逐层分页**，不一次返回无限回复树。GET comments 不传parentId时仅根节点；传parentId时仅其直接回复，返回相同 Page。parentId 必须是该文章的可见节点，其他情况404。前端逐层加载；回复任意已通过节点，保留真实parentId，不把深层回复扁平重挂。replyCount用于展开按钮。

创建须同时满足：账号/权限有效、文章 PUBLIC、站点comment.enabled=true、文章allowComment=1；关闭评论返回409/STATE_CONFLICT，非PUBLIC文章返回404。parentId非null时要求同篇文章、节点status=1且祖先可见；跨文章/不存在父节点404，已变成占位或不可回复状态409。占位节点可展示已有回复但不再接受新回复。已关闭评论仅禁止新增，不隐藏已有可见评论。待审或驳回评论无公开回复入口。parentId/articleId/userId创建后不可修改，故不能移动评论或构造环。

状态：创建由服务端读取 reviewRequired 决定0待审或1通过；管理员/作者回复同样走评论创建，不默认免审。后台可将0/1/2审核为1或2，同目标幂等；3为终态，不能审核复活。本人删除任意自身0/1/2状态或后台管理删除，均在事务内将content覆盖为固定占位并置3，不物理删除、不改回复链；重复删除200。审核驳回已通过父评论时隐藏整分支；恢复通过后重新计算可见回复。管理删除使用同一占位状态是本契约的决定，无额外“管理员删除”枚举。

<a id="model-12-7"></a>

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

<a id="model-12-8"></a>

### 12.8 文件模型与读取响应

blog_file没有公开标志或文章外键，不能仅凭fileId认为可匿名访问。第 11 章文件 HTTP 已接入文章真实引用与现有存储；当前默认启用OSS，新上传使用storageType=oss；本地目录仅用于临时校验及历史local对象。启用时必须完整提供OSS配置，缺失不回退本地；旧文件按各自元数据读取，不迁移历史对象，不提供多存储切换 API，也不把私有附件改成公开直链。

| 模型 | 字段与约束 |
| --- | --- |
| File | fileId/uploaderId:ID、originalName:string（1..255）、mimeType:string（≤100）、sizeBytes:string、storageType:"local"（默认）或"oss"（显式启用后新上传）、createTime:Time、previewUrl:string（后台内容接口，非公开直链） |
| Upload | multipart只有file，单个、非空；不接uploaderId、文件路径、bucket、objectKey、isPublic |

设计决定：首期只允许PNG/JPEG图片，单文件≤10MiB、单次multipart总大小≤11MiB；扩展名白名单`.png/.jpg/.jpeg`必须与服务端内容识别一致，不信Content-Type。使用JDK ImageIO先读尺寸再解码验证真实图片，拒绝不合法结构/异常尺寸（宽高各≤10000、像素总数≤2500万）。不支持WebP/PDF/SVG/HTML/脚本/压缩包，当前POM未配WebP编解码或PDF解析器，不预装额外依赖。图片不承诺压缩转码；以后有其他附件需求再明确类型及校验契约。

原文件名仅展示，移除路径分隔符/控制字符，结果空或>255报400；服务端生成ASCII随机object_key（≤512），normalize后的绝对路径必须仍位于上传根目录内，防目录穿越和符号链接越界。存储根目录不能被Nginx/静态资源映射公开。File模型不返回bucket/objectKey/磁盘路径。

成功内容响应为原始文件字节，非Result：`Content-Type`取服务端确认的image/png或image/jpeg、`Content-Length`取实际字节数、`X-Content-Type-Options:nosniff`、`Content-Disposition:inline; filename*=UTF-8''...`安全编码文件名。首期不支持Range/分片/断点上传，忽略Range返回完整200；文件响应`Cache-Control:no-store`，避免下架/私密后缓存绕过。失败仍返回对应HTTP的JSON Result。前端携带Bearer fetch后台内容并用临时object URL预览，不能把Token拼接进img URL。

<a id="model-12-9"></a>

### 12.9 主页与简历模型

实施状态：第15章21个入口已使用真实HomeProfile/Social/Experience/Skill服务及Mapper/XML；公开资料与账号资料分离，后台逐操作检查ALL。主页首次设置使用既有管理保护锁串行空资源版本，再升序锁操作者/所选主人、固定资料行。图标键沿用已有菜单键并增加文档示例github，其他图标使用外部HTTP(S)地址。

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

<a id="message-rules"></a>

<a id="model-12-10"></a>

### 12.10 留言模型、父链与悄悄话

实施状态：第16章11个入口与message配置行锁、完整祖先链、最早私密祖先接收者、本人归属SQL、编辑重新待审及占位删除已接通。通过提交后的提醒只取启用用户当前已确认激活的订阅邮箱，不含任何私密原文。公开逐层分页沿用轻量父链读取方式，数据量使扫描不可接受时再演进查询。

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

<a id="model-12-11"></a>

### 12.11 音乐模型

实施状态：第17章6个入口已接通播放列表与后台元数据维护；歌词组合、无符号32位整数秒及外部URL实际校验。现有文件业务仍仅支持PNG/JPEG，不抓取或删除外部媒体。

| 模型 | 完整字段与约束 |
| --- | --- |
| `MusicWrite` | `title`:S(200)（曲目标题）；`artist`:S(200)?（作者或歌手）；`durationSeconds`:integer&#124;null（0..4294967295秒，未知为null）；`coverUrl`:URL?（外部HTTP(S)封面）；`audioUrl`:URL（外部HTTP(S)音频URL；保存不触发服务端下载）；`lyricUrl`:URL?（歌词URL；无歌词时为null）；`lyricType`:string&#124;null（lrc/json/txt；必须与lyricUrl同时为空或同时非空）；`sortOrder`:int32（越小越靠前）；`status`:integer（0隐藏 1展示） |
| `Music` | `musicId`:ID（音乐ID）；`title`:S(200)（曲目标题）；`artist`:S(200)?（作者或歌手）；`durationSeconds`:integer&#124;null（0..4294967295秒，未知为null）；`coverUrl`:URL?（外部HTTP(S)封面）；`audioUrl`:URL（外部HTTP(S)音频URL；保存不触发服务端下载）；`lyricUrl`:URL?（歌词URL；无歌词时为null）；`lyricType`:string&#124;null（lrc/json/txt；必须与lyricUrl同时为空或同时非空）；`sortOrder`:int32（越小越靠前）；`status`:integer（0隐藏 1展示）；`createTime`:Time（创建时间，UTC）；`updateTime`:Time（最后更新时间，UTC） |
| `MusicPublic` | `musicId`:ID（音乐ID）；`title`:S(200)（曲目标题）；`artist`:S(200)?（作者或歌手）；`durationSeconds`:integer&#124;null（0..4294967295秒，未知为null）；`coverUrl`:URL?（外部HTTP(S)封面）；`audioUrl`:URL（外部HTTP(S)音频URL；保存不触发服务端下载）；`lyricUrl`:URL?（歌词URL；无歌词时为null）；`lyricType`:string&#124;null（lrc/json/txt；必须与lyricUrl同时为空或同时非空）；`sortOrder`:int32（越小越靠前） |

lyricUrl与lyricType必须同时为null或同时非null，类型只允许lrc/json/txt；是否有歌词由URL判断。播放列表只返回status=1，sortOrder ASC,musicId ASC。所有媒体URL为外部HTTP(S)资源，不接受本站附件API路径、磁盘路径或任意脚本；服务器不下载或代理探测URL。现有文件接口仍只上传PNG/JPEG，新增曲目不意味着支持音频/歌词本地上传。删除曲目只删除元数据。

<a id="model-12-12"></a>

### 12.12 邮件订阅与票据模型

实施状态：第18章11个入口已实现；订阅状态写事务、独立Redis申请/投递轮次、一次性确认与幂等退订、真实SMTP边界及PUBLIC XML均已接通。SMTP默认关闭，未配置或传输失败明确503，待验证数据可重发恢复。XML不依赖邮箱订阅。

| 模型 | 完整字段与约束 |
| --- | --- |
| `Subscription` | `subscriptionId`:ID（订阅ID）；`email`:string（订阅邮箱，≤254；服务端trim并转小写）；`status`:integer（0待验证 1已订阅 2已退订）；`verifiedAt`:Time&#124;null（当前邮箱验证成功时间）；`subscribeTime`:Time&#124;null（最近激活时间）；`unsubscribeTime`:Time&#124;null（最近退订时间）；`createTime`:Time（创建时间，UTC）；`updateTime`:Time（最后更新时间，UTC） |
| `SubscriptionAdmin` | `userId`:ID（所属本站用户）；`subscriptionId`:ID（订阅ID）；`email`:string（订阅邮箱，≤254；服务端trim并转小写）；`status`:integer（0待验证 1已订阅 2已退订）；`verifiedAt`:Time&#124;null（当前邮箱验证成功时间）；`subscribeTime`:Time&#124;null（最近激活时间）；`unsubscribeTime`:Time&#124;null（最近退订时间）；`createTime`:Time（创建时间，UTC）；`updateTime`:Time（最后更新时间，UTC） |

status为0待验证、1已订阅、2已退订。每用户一条、每规范化邮箱一条，昵称读取用户公开资料，不复制旧nickname列。待验证或换邮箱时verifiedAt/subscribeTime/unsubscribeTime清空；激活必须有当前邮箱验证时间和订阅时间，unsubscribeTime=null；退订写时间但可保留原验证记录。任何请求都不能写userId/verifiedAt/审计时间，后台状态接口也不能更换邮箱或跳过验证。

确认链接使用固定前端完成页 `#confirmTicket=<opaque>`，前端读fragment后立即清除，携带本站Bearer向确认接口提交JSON票据。票据24小时有效，Redis中只绑定本次申请的用户、订阅ID、邮箱与申请版本。申请版本是Redis生成的随机标识，按用户保存当前有效值，重发时原子替换，不能仅靠毫秒updateTime区分申请；过期、复用、版本或身份不符统一400/SUBSCRIPTION_TICKET_INVALID，不泄露具体原因。锁用户及订阅后原子消费票据，再提交数据库；数据库失败时需要重新申请，不用额外表伪造跨Redis/MySQL事务。申请/重发按账号与可信IP限流，SMTP或Redis失败503，待验证数据可保留，不能回复已激活。申请/重发共用每用户+可信IP每60秒5次限流，创建留言为每用户+可信IP每60秒10次。相同已激活邮箱的PUT不再发确认邮件。

邮件退订票据30天有效，Redis绑定订阅ID、邮箱、verifiedAt、本轮subscribeTime，只允许退订该记录；有效票据可重复调用保持幂等。邮件链接由固定前端页面读取fragment并让用户确认，再POST退订；GET不修改订阅。重新申请/换邮箱/再激活或删除记录后，旧轮次票据失效。服务端不向本人状态接口或后台列表返回票据，不在访问日志中记录票据。实名登录只用于本站身份，邮箱确认不创建会话、不改变登录邮箱或角色。XML Feed独立于邮箱订阅，公开阅读不需要票据。

订阅列表和详情暴露邮箱，仅ALL后台管理或本人可读；不保留旧无分页全量订阅邮箱接口。订阅写操作锁当前用户与对应订阅，唯一冲突整笔409，不把相同邮箱自动转移到另一账号。X-Revision覆盖userId/email/status/verifiedAt/subscribeTime/unsubscribeTime；重复相同状态不覆盖时间。

<a id="analytics-rules"></a>

<a id="model-12-13"></a>

### 12.13 访问统计、报表与时间口径

实施状态：第19章14个入口已接通可信Origin/最长站点路径前缀核验、PUBLIC规范slug核验、访客与明细同事务、限制/到期/清理及UTC报表。原IP仅在内存查离线地域，访问表持久化IPv4 /24或IPv6 /48网段；来源URL和页面路径剔除全部query/fragment。

| 模型 | 完整字段与约束 |
| --- | --- |
| `TrackingResult` | `recorded`:boolean（数据库成功记录本次浏览后为true，不返回访客会话） |
| `Visitor` | `visitorId`:ID（统计访客ID，不是登录身份）；`fingerprint`:string（ASCII统计标识，不可作为鉴权凭据）；`ipAddress`:string&#124;null（最近脱敏IP网段，仅后台可见）；`userAgent`:string&#124;null（≤1024，最近User-Agent）；`country`:string&#124;null（国家）；`province`:string&#124;null（省份）；`city`:string&#124;null（城市）；`isBlocked`:integer（数据库封禁标记0/1；实际生效还需检查blockedUntil）；`blockedUntil`:Time&#124;null（封禁结束，已封禁且null表示永久）；`firstVisitTime`:Time（首次访问时间）；`lastVisitTime`:Time（最近访问时间）；`createTime`:Time（创建时间，UTC）；`updateTime`:Time（最后更新时间，UTC） |
| `PageView` | `viewId`:ID（浏览记录ID）；`visitorId`:ID&#124;null（统计访客，清理后可空）；`userId`:ID&#124;null（采集时服务端确认的本站用户，匿名或清理后为空）；`articleId`:ID&#124;null（通过公开文章与规范路径核验的文章ID）；`siteCode`:string（home/blog/cv）；`pagePath`:S(2048)（不含敏感查询参数的规范路径）；`pageTitle`:S(200)?（页面标题）；`referer`:URL?（已脱敏的来源URL）；`ipAddress`:string&#124;null（脱敏IP网段，仅后台可见）；`userAgent`:string&#124;null（≤1024）；`viewTime`:Time（采集时间） |
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

<a id="model-12-14"></a>

### 12.14 操作日志模型

| 模型 | 完整字段与约束 |
| --- | --- |
| `OperationLog` | `logId`:ID（操作日志ID）；`userId`:ID&#124;null（操作人；物理清理后为空）；`operationType`:string（≤32，ASCII操作类型）；`operationTarget`:string（≤100，ASCII模块或资源）；`targetId`:ID&#124;null（目标数字ID，其他情况为空）；`permissionCode`:string&#124;null（本次检查的权限码快照）；`result`:integer（0失败 1成功）；`errorMessage`:string&#124;null（≤500，脱敏错误摘要）；`ipAddress`:string&#124;null（操作IP）；`operationTime`:Time（操作时间） |
| `OperationLogDetail` | `logId`:ID（操作日志ID）；`userId`:ID&#124;null（操作人；物理清理后为空）；`operationType`:string（≤32，ASCII操作类型）；`operationTarget`:string（≤100，ASCII模块或资源）；`targetId`:ID&#124;null（目标数字ID，其他情况为空）；`permissionCode`:string&#124;null（本次检查的权限码快照）；`result`:integer（0失败 1成功）；`errorMessage`:string&#124;null（≤500，脱敏错误摘要）；`ipAddress`:string&#124;null（操作IP）；`operationTime`:Time（操作时间）；`operationData`:object&#124;null（白名单脱敏摘要；UTF-8≤16KiB，不是完整请求体） |

仅服务端写日志，记录实际会话操作人、模块、动作、目标、权限码快照和结果；不接受客户端创建、修改日志。列表不返回大段摘要，详情operationData为白名单脱敏JSON对象或null。不得保存密码、授权头、OAuth code/state/ticket、RSS票据、密钥、原始SQL或异常堆栈。user_id外键SET NULL保证账号物理清理后保留日志；日志不承担授权凭据作用。

日志为不可编辑记录，查询和删除不需要X-Revision；清理需sys:log:delete+ALL，删除行为追加一条新的审计，不递归记录写日志本身。业务成功日志随数据库变更同事务写入；业务失败回滚后另行写脱敏失败日志，不能用已回滚事务保存失败记录。当前后台55个写方法已实际写入审计，包含原32个写方法及主页/社交/简历/音乐、留言治理、订阅治理、访客/PV清理和日志清理：成功日志写入失败使业务回滚，回滚后由独立虚拟线程事务记录失败；operationData固定为null，错误只记固定白名单码。尚未进入业务写方法的参数绑定/校验/HTTP权限拒绝不产生该业务审计。失败投递为进程内异步任务，数据库或进程故障不提供跨宕机可靠投递保证，写入失败输出固定错误；第20章查询/详情/清理已实现，使用独立响应模型；列表不展开operationData，详情当前返回null。清理写入新的审计记录，与删除一起提交；不开放客户端写日志。

<a id="model-12-15"></a>

### 12.15 文章导航、推荐与热门榜

| 模型 | 完整字段与约束 |
| --- | --- |
| `ArticleNavigation` | `articleId`:ID、`title`:string、`slug`:string；仅PUBLIC文章，供上下篇与相关文章共用 |
| `HotArticle` | `articleId`:ID（PUBLIC文章ID）；`title`:string（标题）；`slug`:string（URL标识）；`summary`:string（摘要）；`coverUrl`:URL?（外部公开封面）；`viewCount`:string（文章累计阅读数）；`likeCount`:string（当前点赞明细数）；`hotValue`:string（按metric选择的累计阅读数或点赞数）；`publishTime`:Time（首次发布时间） |

ArticlePublicDetail在原有字段上增加wordCount:string、readingMinutes:integer（最少1）、prevArticle/nextArticle:ArticleNavigation|null、relatedArticles:ArticleNavigation[]。ArticleNavigation仅含articleId:ID、title:string、slug:string；不返回私密或后台信息。字数按Markdown解析后的可见正文统计：每个汉字计1、每个连续英文字母/数字词计1，忽略标记、URL目标和代码块；readingMinutes=max(1,ceil(wordCount/300))，都是读取时派生，不新增SQL列或可写计数。

上一篇按(publishTime,articleId)早于当前文章的最近PUBLIC文章，下一篇取晚于当前文章的最近PUBLIC文章；时间相同用articleId稳定排序，无结果null。相关文章取相同且可展示category的PUBLIC文章，排除自身，publishTime DESC,articleId DESC，最多6篇；未分类或分类隐藏时[]。这些派生字段不加入文章X-Revision，不增加候选文章浏览计数。

第7.11节用period=site/month、metric=view/like表达旧站4榜。month是本月首次发布的候选文章按累计数排序，保留旧SQL的真实含义；不是时间段内新增热度。时间段访问排行另见第19章。搜索扩展为标题、摘要和Markdown原文的字面包含，不强制引入中文全文索引或旧异步计数缓存。

---

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
| 注册/第三方首次注册 | 用户+member，第三方还含oauth绑定；唯一键冲突回滚整笔；平台身份冲突按[认证与第三方授权](API.md#group-1)重新查赢家 |
| 密码/绑定/解绑 | 所有方式变更先锁sys_user再读密码和绑定；清除/解绑保证至少一种方式；不写占位哈希/默认密码；需要撤销的操作先完成Redis失效再提交，失败处理同[会话生命周期](#auth-rules) |
| 用户/角色/权限/授权 | 先固定管理员保护锁，再按ID升序锁所有受影响用户、目标角色/权限，检查最后管理员；关联集合替换同事务，先撤销会话/清缓存再提交；部分成功不允许 |
| 菜单结构 | 同固定保护锁后检查整棵影响树，保存结构/父节点与约束；角色菜单+权限经grants共同保存 |
| 文章保存 | 文件引用锁→文章锁→X-Revision/范围→分类标签校验→元数据+正文+标签；发布检查publish权限和首次时间；任一关联不存在整笔400，不部分保存 |
| 分类/标签删除 | 单事务执行外键约定的SET NULL/CASCADE；并发文章写入遇到失效关联返回400，不产生正文孤儿；引用变化令聚合X-Revision失效 |
| 回收/恢复/发布/评论/点赞 | 同一文章锁定后才检查状态和写入，避免“检查公开后同时被下架”仍写入；评论先锁配置再锁文章及评论/父节点，文件引用检查/变更先锁文件再锁文章；多目标按ID升序 |
| 评论写入与开关 | 先锁comment配置行，再锁文章和目标/父评论；以锁内最新enabled/reviewRequired/allowComment决策；review/moderate按相同锁顺序。因此配置关闭与并发新增有确定提交顺序 |
| 点赞 | 文章锁+复合主键插入/删除；COUNT读已提交明细，无Redis点赞副本；并发其他用户变化时响应计数仅代表本次读取时刻 |
| 上传/删除 | 按[文件与附件](API.md#group-11)顺序与补偿；存储非数据库事务资源，不使用虚构的分布式事务 |
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
| 旧全文索引、计数同步、邮件系统与部署配置 | 搜索契约定义字面包含；不移植异步计数批次，不把SMTP/OAuth/存储凭据放进配置表；本轮使用Boot BOM管理的mail starter实现SMTP边界，并接入现有数据库/Redis及地域设施 |

新增66个入口已落实Controller→Service接口/实现→Mapper/XML及对应实体/DTO/VO，邮件与采集已接通真实消费者。沿用现有28表，不复制老项目鉴权、访客Token、凭据或计数缓存。公开白名单逐方法/路径增加；隔离验证、真实外部平台和部署验收分别记录在运行说明，不将实现标记等同上线。

---

<a id="permanent-delete-rules"></a>

## 22.7 永久删除开发约定

必须先按现有接口移入回收站；仅 `deletedAt != null` 可永久删除，正常文章返回 `409/STATE_CONFLICT`，不允许用参数绕过。需要有效 admin:access，delete 与 purge 的 ALL/SELF 分别检查；任何一个权限仅 SELF 都要求当前用户为文章作者，不把一个权限的 ALL 扩大到另一个。新 purge 权限须实施时显式初始化并授权，不自动赋予 author/member，也不因角色名为 admin 而绕过授权。查看详情另外遵循既有 query 权限范围。

按现有事务及锁后复核规则检查原 Token、权限、范围、版本与回收状态；先权限/范围，再校验版本和状态。缺版本 428、过期 412、无权限 403、不存在或超出任一数据范围 404。重复删除已不存在目标返回 404；前端超时后先重新查询确认，不承诺永久删除响应幂等。拒绝请求体及所有查询参数。

| 数据 | 永久删除的处理规则 |
| --- | --- |
| 文章元数据、正文、安全 HTML、标签关联、点赞明细 | 同一数据库事务内删除；正文/标签关联/点赞可利用现有 ON DELETE CASCADE，不删除分类或标签字典 |
| 该文章全部评论及回复 | 包括待审、通过、驳回与占位，均不可恢复；现有评论外键为 RESTRICT，须在持有文章及相关评论锁后按子节点到父节点顺序删除，再删文章，不能直接假设级联或关闭外键校验 |
| 文件元数据与 OSS/本地对象 | 保留，不把引用文件当作文章私有对象自动删除；仍由既有文件删除入口检查全部引用与自身权限 |
| 浏览明细 | 保留已有历史采集，articleId 按现有外键 SET NULL；pagePath/pageTitle 历史快照保留，按文章 ID 的排行不再关联该文章，站点 PV 趋势仍含原浏览 |
| 操作日志 | 保留历史审计，新增 purge 成功审计与数据库清理同事务提交；失败回滚，沿用既有失败审计，不记录已删正文 |
| slug、公开入口与恢复 | 删除后释放文章 slug；文章详情、关联文件的该文章访问上下文返回 404；不再进入 Feed/站点地图/公开推荐，恢复接口返回 404 |

前端操作放在回收站并明确提示“永久删除文章及全部评论、点赞，无法恢复”，与普通删除分开。后端已实现；验证只在隔离数据库执行删除，不修改现有 SQL 外键或实际存储对象。前端页面尚未建设。

---

<a id="planned-acceptance"></a>

### 22.8 实施范围与验收条件

本章后端已复用当前 Controller→Service 接口/实现→Mapper/XML、DTO/VO、ArticleFilter、公开响应转换、权限范围、分页与审计机制；没有新增数据库表或改变既有公开列表字段。权限及菜单统一由[初始化脚本](../sql/drifting_leaves_v2.sql)创建，显式授予内置管理员角色，不授予 author/member。项目尚未上线，不再维护单独的权限增量脚本。

| 改动位置 | 必须完成的业务及验证 |
| --- | --- |
| 前台文章查询 DTO、查询白名单、Service、Mapper 条件 | 新增 authorId 校验与传递，公开 SQL 分支真正应用作者条件；验证合法/非法/不存在作者，及作者+分类+标签+关键词+月份 AND 组合、total 一致性和公开隔离；不能只复用目前仅后台生效的 authorId 分支 |
| 公开作者、分类/标签详情入口及公开准入白名单 | 按方法/路径逐项增加 4 个公开路由；验证分页排序、公开计数、空集合、隐藏分类、slug 大小写及作者资料白名单，管理员 Token 不扩大公开结果 |
| 本人点赞列表入口与本人路由校验 | 身份来自会话，验证账号 A 不能查 B、拒绝 userId、过期会话 401；取消/重复/再次点赞的时间顺序，以及文章下架后 records/total 同时排除 |
| 永久删除业务、权限初始化/固定码保护、事务审计 | 复用 delete 与新增 purge 的独立范围；验证正常文章拒删、SELF 越权、缺失/过期版本、锁等待后的会话/权限/状态复核，多层评论 RESTRICT 顺序清理、成功审计及失败整笔回滚；确认字典/文件保留、浏览关联置 null、删除后不能恢复或公开读取 |
| 文档与客户端 | 后端已实现并通过隔离集成测试，路由统计为 159；前端尚无工程，作者主页/筛选页、分类/标签页、个人点赞页与回收站后续分别接入对应接口，永久删除必须明确说明不可恢复 |

错误沿用 [第 14 章](API.md#errors)：严格输入 400/VALIDATION_ERROR，未认证 401/UNAUTHENTICATED，权限不足 403/FORBIDDEN，隐藏/不存在/范围外 404/NOT_FOUND，状态冲突 409/STATE_CONFLICT，旧版本 412/REVISION_CONFLICT，缺版本 428/PRECONDITION_REQUIRED，业务存储不可用按既有规则返回 503。本章没有新增错误码，不能将存储故障转换为成功空页或删除成功。


## 23. 在线连接与统计身份

完整 HTTP/消息定义见 [API 第23章](API.md#group-23)与[在线说明](实时在线人数接口规划.md)。公开白名单只增加 `POST /analytics/online/tickets`（可选 Bearer，一旦携带必须有效）；精确 `GET /ws/online` 进入专用握手校验，普通GET、查询参数、非约定来源和Header/子协议身份均拒绝，不开放整个路径段。

在线服务只签发用途隔离的30秒随机票据，键保存摘要，载荷只保存服务端确认的userId、原Token摘要与到期时间；浏览器不能自报userId。join按Origin、原会话、账号状态重查后Lua比较完整载荷并删除。长连接通过 `SessionManager.requireOriginalActive` 只查绑定用户的原会话，不扫描Redis全键；现有敏感操作的 `originalToken/requireRecent` 仍要求最近300秒认证。心跳不续期Token、不写PV。

单实例内连接登记、快照生成和广播保持同一顺序，发送有Spring会话保护。关闭/错误/超时/发送失败统一幂等移除；join与close交错不能残留成员。登录用户按账号去重，访客按站点/Origin/fingerprint去重，有有效登录连接的浏览器键排除匿名重复人数。故障无法确认身份时关闭相应连接，不能伪造匿名身份或0人数。

只有Blog的站点统计卡片展示三项全站合计，Home/Cv保留匿名登记，Admin不参与；布局而非卡片维护连接。会话变更重连和旧异步结果隔离涵盖密码/OAuth共用登录入口、退出、401和账号安全变更。实际验证和未验证范围见[运行说明](后端运行说明.md#online-runtime)。
