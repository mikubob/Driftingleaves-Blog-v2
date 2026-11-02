# 博客端 Front-Blog

文章通过 MdPreview 展示公开 Markdown，保留代码高亮、复制、折叠与目录。网页与 RSS 的解析职责及语法差异见 [Markdown 阅读说明](../../docs/Markdown阅读说明.md)。

直接迁移并复用 `D:/CodingFiles/Blog/DriftingLeavesOfFront/Frontend-Blog/` 的 UI 源码，适配本仓库 [API.md](../../docs/API.md) 和后端实现。原项目保持只读；没有迁移旧访客凭据、业务数据、真实部署配置或构建产物。

## 音乐与歌词播放

音乐播放器支持外部地址及 `/api/v1/blog/music/files/{fileId}/content` 受管地址，API 层统一补上已配置的 API origin。原生 audio 使用后端 Range 响应；打开播放列表面板后加载歌词，LRC/JSON同步展示当前行，TXT保留全文。切歌、关闭面板或组件卸载时取消歌词请求；失败可重试。歌词必须为UTF-8且≤1 MiB，JSON为 `[{"time":0,"text":"歌词"}]`（秒），外站歌词需允许浏览器CORS。隐藏音乐不能通过公开文件路径读取。

## 原组件复用

| 原文件 | 本项目位置与适配 |
| --- | --- |
| `view/Layout/index.vue` | 同路径；保留布局、页面过渡、返回顶部；改为 V2 加载与浏览采集 |
| `components/BlogHeader.vue` | 保留透明导航、响应式菜单、搜索、主题和音乐播放器；链接与曲目改接 V2 |
| `components/HeroBanner.vue` | 保留原横幅和背景资源 |
| `components/ArticleCard.vue` | 保留原卡片模板和完整样式；通过 `shared/presentation.js` 映射 V2 属性 |
| `components/HomeTimeWidgets.vue` | 直接复用时钟、日历、黄历、时间进度组件及原样式 |
| `components/SidebarCard.vue` | 保留资料、统计、分类/标签弹窗和 RSS 弹窗；改为账号订阅 |
| `components/BlogFooter.vue` | 保留页脚布局；公开配置、版权展示名、RSS 和 Sitemap 地址来自 V2 |
| `components/TableOfContents.vue` | 保留桌面/移动目录、阅读进度与锚点交互 |
| `view/Home, Category, Tag, Archive, Hot, Links, About` | 分类与标签文章页合并至本项目 `view/Taxonomy/index.vue`，路由和样式保留；其余页面替换数据读取，归档按 V2 月份分页 |
| `view/Article, Message` | 保留原正文、点赞动画、表单和布局；评论/留言行提取到 `DiscussionList.vue`，支持 V2 逐层回复分页 |
| `Frontend-Shared/EmojiPicker.vue`、`emoji.json` | 迁入 `src/shared/`，继续用于原表单 |
| `assets/styles`、`assets/font`、`assets/images/bgc.webp` | 直接复用原站样式、字体和背景 |

上一版仿写的主布局、侧栏、播放器、文章/留言页和全局样式已移除。替换前的源码备份保留在忽略目录 `output/before-legacy-reuse/`。

V2 新增的登录、个人中心、邮件确认和退订流程继续保留，样式限定在 `.v2-page` 内，使用原站主题变量。没有用这些页面替换原站已有的博客 UI。

## 本地运行

需要 Node.js `^22.18.0 || >=24.12.0` 和 pnpm `11.9.0`。

```powershell
cd D:\CodingFiles\Blog\DriftingLeavesV2\Front\Front-Blog
pnpm install --frozen-lockfile
pnpm dev
```

地址为 `http://127.0.0.1:5174`，端口占用时报错。默认将 `/api/v1` 原样代理到 `http://localhost:5922`。复制 `.env.example` 为 `.env.local`，可设置 `API_PROXY_TARGET`。

后端 dev 默认允许博客 `http://127.0.0.1:5174` 和 `http://localhost:5174`。博客公开基址仍需按实际访问地址设置；若显式配置过 `FRONTEND_ORIGINS`，还需在该白名单中加入博客来源。启动后端前按实际环境设置：

```powershell
$env:FRONTEND_ORIGINS = 'http://127.0.0.1:5173,http://localhost:5173,http://127.0.0.1:5174,http://localhost:5174,http://127.0.0.1:5175,http://localhost:5175,http://127.0.0.1:5176,http://localhost:5176'
$env:BLOG_BASE_URL = 'http://127.0.0.1:5174'
```

`FRONTEND_ORIGINS` 替换整份白名单，应保留其他正在使用的真实 Origin，修改后重启后端。代理保留浏览器 Origin，不能替代后端来源校验。使用 `localhost` 时同步替换博客基址。

```powershell
pnpm build
pnpm preview
```

生产产物为 `dist/`，采用根路径部署。服务器需将 `/api/v1` 代理到后端，并将前端页面路径回退到 `index.html`。跨域部署时在构建前设置 `VITE_API_ORIGIN` 为后端公开源站，不含 `/api/v1`，后端同时配置精确 CORS 和暴露响应头。当前未实现子路径部署。

## V2 适配边界

- 文章正式地址为 `/articles/:slug`，与后端 RSS、站点地图和统计一致。搜索入口为 `/?search=`。
- 文章、分类、标签、作者、归档、友链和回复均使用 V2 分页。字典和播放列表继续读取全部页。ID、外键、BIGINT 计数及分页总数保持字符串，不用浮点数转换；原 Element Plus 数字总数分页改为保留字符串的 `ListPager`。
- 卡片字段通过 `articleView` 映射：`articleId → id`、`coverUrl → coverImage`、分类/标签对象转为原组件属性，正文优先使用 `markdownContent`，缺少原文时回退 `htmlContent`。不伪造缺失的地理位置、设备信息、分类计数或在线人数。
- 原站访客会话、匿名昵称/邮箱输入和验证码不适用于 V2，已移除。密码与 OAuth 登录均固定有效 3 天（259200 秒）；localStorage 的 `dl-blog-session` 仅保存 Token 和固定到期时间，经 Bearer 发送给配置的 API。刷新或重新打开页面后先核验 `/blog/me`、`/blog/me/capabilities`，再恢复身份和权限，不续期；退出或核验失败清除持久凭据。浏览器禁用存储时仅本页有效。
- 点赞、评论、留言和邮件订阅按 V2 权限操作。评论为纯文本；V2 没有评论 Markdown、评论悄悄话或评论编辑接口，这些旧入口已移除。留言保留 Markdown、悄悄话、回复通知意愿及本人编辑/删除。私密范围和审核状态始终由服务端决定。
- 文章使用 MdPreview 展示公开详情的 Markdown，缺少原文字段时回退安全 HTML，仍保留正文上方的阅读排版选择并应用六种主题；留言继续使用后端清理后的 HTML。保留目录和锚点。公开封面/附件支持文章上下文路径，跨域时指向配置的 API，不读取后台私密文件。
- 原在线访客 WebSocket 在 V2 没有对应接口，已移除该统计项和连接。今日浏览、总浏览、访客总量等使用真实公开统计。不把接口失败展示成空内容；读取提供加载、错误与重试。
- 原 RSS 弹窗改为登录账号订阅，待验证邮箱不会标成已订阅。邮件确认页读取 `#confirmTicket` 后清除地址片段，登录同一账号后提交；退订页读取 `#unsubscribeTicket` 后，由用户点击确认才提交。刷新后需重新打开完整邮件链接。
- 资料和留言编辑原样传递详情 `X-Revision` 至 `If-Revision`。412 保留输入并提示重新获取后合并，不自动覆盖。密码不 trim、不截断，遵守 8–64 字符和 UTF-8 ≤72 字节；修改成功后重新登录。个人中心提供第三方绑定/解绑，绑定弹窗核对来源、窗口和原 Token，解绑成功后重新登录；未新增清除密码入口。
- 浏览统计只持久化随机 `dl-blog-visitor`，不是身份。路径与来源剔除 query/fragment；文章公开详情成功后带字符串 articleId 采集，不重放写请求。

OAuth 需在后端配置真实平台、允许的前端 Origin 及固定 `OAUTH_COMPLETE_URL=https://<博客域名>/oauth/complete`。完整第三方登录使用 HTTPS 和 Secure Cookie；authorize/exchange 带 `credentials: include`。邮箱确认页使用登录弹窗保留原票据，仅接受同源且匹配已打开窗口的消息。部署策略需保留此同源弹窗通信。

注册须填写邮箱及邮件中的六位验证码；前台可选账密、邮箱验证码或已绑定第三方登录，三种方式都填写图形验证码。发送邮件会消费图形验证码并刷新图片，注册/登录提交时填写新图片。邮件验证码五分钟有效，发送失败明确报错，每邮箱一分钟最多发送一次。验证码只由邮件接收，不出现在 HTTP 响应；注册与登录用途不能混用。第三方未绑定时先完成邮箱注册，再到个人中心绑定，后续第三方登录不重复验证邮箱。

## 构建检查

项目已按要求移除自动化测试源码、浏览器检查脚本和测试执行入口；可使用 `pnpm build` 检查生产构建。

## 服务器异常与限流

网络连接失败、HTTP 5xx、无法解析的成功响应及 HTTP 429 显示参考 FeiTwnd-Website 对应端的全屏 404 样式；429 保留 Retry-After 秒数提示。请求仍失败且不自动重放，普通校验、鉴权与版本冲突沿用原逻辑。点击“返回首页”重新进入站点；如有未提交编辑，应重新加载后再操作。统计采集失败保持静默，不切换异常页。

公共异常边界验证：在仓库根目录执行 `node --test Front/shared/requestFailure.test.mjs`。

旧 Blog 的页面过渡、卡片悬停、主题切换、音乐旋转、点赞、时钟、目录和回顶样式继续复用；布局过渡启用 `appear`，使首次进入也播放旧版入场效果。按本站要求，动画、过渡与平滑滚动始终启用，不随系统“减少动态效果”设置关闭。浏览器 smoke 可分别传入 `no-preference`、`reduce`，通过真实 transitionrun / animationstart 验证两种系统偏好下的主要动效。

图形验证码复用 `Front/shared/CaptchaField.vue`：通过本端 `/auth/captcha` 获取六位数字圆圈验证码，120 秒有效。登录与 OAuth LOGIN 提交 `X-Captcha-Id`、`X-Captcha-Code`；博客注册、评论、留言及本人留言编辑同样校验；后台留言回复不要求图形验证码，仍执行后台登录、权限和限流。提交后刷新图片，失败保留正文。跨域白名单须允许两个验证码头；OAuth BIND、回调、兑换仍沿用原有 Cookie/Origin/Bearer 流程，详见[接口文档](../../docs/API.md#captcha)。

文章排版支持 default、github、vuepress、mk-cute、smart-blue、cyanosis。作者编辑/已保存预览与读者阅读偏好使用不同浏览器键；博客明暗仅跟随网站已有黑白天开关及系统主题，不另设正文按钮，管理端编辑和预览固定亮色，不提供明暗按钮。偏好不写入文章，保存仍只提交原文和版本。受管附件继续走 API；后台私密图片通过 Bearer 读取并在切换/卸载时释放临时 URL。详见 [Markdown 阅读说明](../../docs/Markdown阅读说明.md)。

阅读主题选择复用 Element Plus 并跟随网站明暗配色；正文背景透明，夜间不再嵌套纯黑内容块。Markdown 与旧 HTML 文章都使用相同的代码工具栏和本地高亮配色，保留行号、复制和折叠。
