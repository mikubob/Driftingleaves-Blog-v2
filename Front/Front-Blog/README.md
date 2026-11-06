# 博客端 Front-Blog

本工程独立维护，可单独复制、安装依赖、构建和部署。组件、工具和样式均位于本工程的 `src/`，修改只影响本端。

项目尚未上线，当前说明用于本地开发与构建。

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
| `view/Article, Message` | 保留正文、点赞动画、表单和布局；`DiscussionList.vue` 将整组回复平铺为一层，保留真实回复对象 |
| `Frontend-Shared/EmojiPicker.vue`、`emoji.json` | 由 [本端目录](src/shared) 提供，用于评论和留言表单 |
| `assets/styles`、`assets/font`、`assets/images/bgc.webp` | 直接复用原站样式、字体和背景 |

V2 新增的登录、个人中心、邮件确认和退订流程继续保留，样式限定在 `.v2-page` 内，使用原站主题变量。没有用这些页面替换原站已有的博客 UI。

## 本地运行

需要 Node.js `^22.18.0 || >=24.12.0` 和 pnpm `11.9.0`。

```powershell
cd D:\CodingFiles\Blog\DriftingLeavesV2\Front\Front-Blog
pnpm install --frozen-lockfile
pnpm dev
```

默认登录开发地址为 `http://127.0.0.1:5174`。Chrome 可在此回环地址使用 `Secure` 登录 Cookie。如需本地 HTTPS，在 `.env.local` 设置 `DEV_HTTPS_KEY`、`DEV_HTTPS_CERT` 为受信任的 PEM 私钥和证书路径，并配置后端 `BLOG_BASE_URL` 与实际 HTTPS 地址一致。Vite 将 `/api/v1/blog/` 原样代理到 `API_PROXY_TARGET`（默认 `http://localhost:5922`），保留浏览器 Origin 和 Referer。

后端 `dev` 默认匹配 `http://127.0.0.1:5174`。使用 `localhost`、其他端口或 HTTPS 时同步修改 `BLOG_BASE_URL`，再重启后端。

```powershell
pnpm build
pnpm preview
```

生产产物为 `dist/`，使用独立 HTTPS 主机名和根路径部署。将 `/api/v1/blog/` 原样代理到后端，保留请求 Cookie、Origin、Referer 和响应 Set-Cookie。`/api/v1/blog/ws/online` 支持 WebSocket Upgrade，转发握手时移除 Cookie。前端页面路径回退到 `index.html`，`BLOG_BASE_URL` 指向实际博客 HTTPS 地址。

## V2 适配边界

- 文章正式地址为 `/articles/:slug`，与后端 RSS、站点地图和统计一致。搜索入口为 `/?search=`。
- 文章、分类、标签、作者、归档、友链和回复均使用 V2 分页。字典和播放列表继续读取全部页。ID、外键、BIGINT 计数及分页总数保持字符串，不用浮点数转换；原 Element Plus 数字总数分页改为保留字符串的 `ListPager`。
- 卡片字段通过 `articleView` 映射：`articleId → id`、`coverUrl → coverImage`、分类/标签对象转为原组件属性，文章正文使用 `markdownContent`，评论和留言等内容继续使用各自的安全 HTML。不伪造缺失的地理位置、设备信息、分类计数或在线人数。
- 密码、邮箱验证码和 OAuth 登录固定有效 3 天（259200 秒），使用 `__Host-dl-blog-session`，属性为 HttpOnly、Secure、SameSite=Strict、Path=/、无 Domain。登录 JSON 只含 `expiresIn` 和 `user`。刷新后通过 `/blog/me`、`/blog/me/capabilities` 恢复身份和权限，访问不续期。退出先撤销服务端会话再清除 Cookie。启动时删除旧 `dl-blog-session`，切换后需要重新登录一次。
- 点赞、评论、留言和邮件订阅按 V2 权限操作。评论为纯文本，待审和驳回评论仅本人保留可见；发表后不可再次修改，可查询和删除本人评论。评论不支持 Markdown 或悄悄话。留言发表后同样不可再次修改，保留 Markdown、悄悄话、回复通知意愿及本人查询/删除。私密范围和审核状态始终由服务端决定。
- 文章使用 MdPreview 展示公开详情的 Markdown，缺少原文字段时回退安全 HTML，仍保留正文上方的阅读排版选择并应用六种主题；留言继续使用后端清理后的 HTML。保留目录和锚点。公开封面/附件支持文章上下文路径，跨域时指向配置的 API，不读取后台私密文件。
- 在线人数通过本端票据与 WebSocket 接入，站点统计展示 Home/Blog/Cv 合计；今日浏览、总浏览、访客总量使用公开统计接口。不把接口失败展示成空内容；读取提供加载、错误与重试。
- 原 RSS 弹窗改为登录账号订阅，待验证邮箱不会标成已订阅。邮件确认页读取 `#confirmTicket` 后清除地址片段，登录同一账号后提交；退订页读取 `#unsubscribeTicket` 后，由用户点击确认才提交。刷新后需重新打开完整邮件链接。
- 资料编辑原样传递详情 `X-Revision` 至 `If-Revision`。412 保留输入并提示重新获取后合并，不自动覆盖。密码不 trim、不截断，遵守 8–64 字符和 UTF-8 ≤72 字节；修改成功后重新登录。个人中心提供第三方绑定/解绑，绑定弹窗核对来源、窗口和原 Token，解绑成功后重新登录；未新增清除密码入口。
- 浏览统计只持久化随机 `dl-blog-visitor`，不是身份。路径与来源剔除 query/fragment；文章公开详情成功后带字符串 articleId 采集，不重放写请求。

选择 Blog 处理 OAuth 时，设置 `OAUTH_COMPLETE_URL=https://blog.example.com/oauth/complete`，并在平台及后端登记同一 HTTPS Origin 的回调，例如 `OAUTH_GITHUB_REDIRECT_URI=https://blog.example.com/api/v1/blog/auth/oauth/GITHUB/callback`。回调使用独立的 Lax 流程 Cookie，完成页清除 fragment。LOGIN 兑换才签发博客登录 Cookie。绑定及邮箱确认页的登录弹窗保留精确同源窗口和票据检查。

注册须填写邮箱及邮件中的六位验证码；前台可选账密、邮箱验证码或已绑定第三方登录，三种方式都填写图形验证码。邮箱登录验证码只发送到已绑定账号的邮箱，注册验证码可以发送到新邮箱。发送邮件会消费图形验证码并刷新图片，注册/登录提交时填写新图片。邮件验证码五分钟有效，发送失败明确报错，每邮箱一分钟最多发送一次。验证码只由邮件接收，不出现在 HTTP 响应；注册与登录用途不能混用。第三方未绑定时先完成邮箱注册，再到个人中心绑定，后续第三方登录不重复验证邮箱。

## 构建检查

使用 `pnpm build` 检查生产构建。

评论和留言点击“查看回复”后先预览 3 条，展开后可继续分页加载。点击回复对象的昵称可查看原文摘要。删除回复后重新查询整组，避免显示已一并删除的后代。

前后端需同步更新：公开列表的 `parentId` 查询和 `replyCount` 现在涵盖全部可见后代，响应新增 `replyTarget` 和 `replyable`；发表时的 `parentId` 仍是直接回复对象。后台逐层分页保持原样，无数据库迁移。

## 服务器异常与限流

网络连接失败、HTTP 5xx、无法解析的成功响应及 HTTP 429 进入全屏请求错误页，显示实际错误。认证接口遇到 429 时保留当前界面，显示 Retry-After 等待时间。请求失败后可手动重试。统计采集失败保持静默。


旧 Blog 的页面过渡、卡片悬停、主题切换、音乐旋转、点赞、时钟、目录和回顶样式继续复用；布局过渡启用 `appear`，使首次进入也播放旧版入场效果。按本站要求，动画、过渡与平滑滚动始终启用，不随系统“减少动态效果”设置关闭。

图形验证码复用 `src/shared/CaptchaField.vue`：通过本端 `/auth/captcha` 获取六位数字圆圈验证码，120 秒有效。登录与 OAuth LOGIN 提交 `X-Captcha-Id`、`X-Captcha-Code`；博客注册、评论、留言创建和回复同样校验；后台留言回复不要求图形验证码，仍执行后台登录、权限和限流。提交后刷新图片，失败保留正文。写请求按所属端校验 Origin，缺失时才解析 Referer；OAuth BIND、回调和兑换使用独立的流程 Cookie，详见[接口文档](../../docs/API.md#captcha)。

文章排版支持 default、github、vuepress、mk-cute、smart-blue、cyanosis。作者编辑/已保存预览与读者阅读偏好使用不同浏览器键；博客明暗仅跟随网站已有黑白天开关及系统主题，不另设正文按钮，管理端编辑和预览固定亮色，不提供明暗按钮。偏好不写入文章，保存仍只提交原文和版本。受管附件继续走 API；后台私密图片通过同源 API 读取并在切换/卸载时释放临时 URL。详见 [Markdown 阅读说明](../../docs/Markdown阅读说明.md)。

阅读主题选择复用 Element Plus 并跟随网站明暗配色；正文背景透明，夜间不再嵌套纯黑内容块。Markdown 与旧 HTML 文章都使用相同的代码工具栏和本地高亮配色，保留行号、复制和折叠。

## 生产包体积

Element Plus 组件、提示框和选择器在使用位置导入各自样式，入口只加载基础变量，避免完整组件库 CSS。阅读器随文章路由加载，黄历使用原有动态导入；保留全部阅读主题、动效和现有功能。
