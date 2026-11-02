# Admin 管理端

独立的 Vue 3 + Vite + Element Plus 工程，UI 复用 `DriftingLeaves-Website/Frontend-Admin`：公共主题、系统字体栈、iconfont 字体、侧栏与顶栏、统计卡片，以及菜单悬停、侧栏折叠和页面切换动画。登录页保留当前设计。迁移后的样式为原生 CSS，不新增 Sass 依赖。接口依据仓库 `docs/API.md` 及实际后端实现。

## 四模块边界

`Front-Home`、`Front-Blog`、`Front-Cv`、`Front-Admin` 分别维护自己的源码、依赖、锁文件、环境配置和构建产物。Admin 不导入其他模块的文件，不需要先启动或构建其他模块；模块之间通过后端 HTTP API 交换业务数据。

本次检查 Home、CV 的源码导入、包依赖和 Vite 配置，未发现跨模块源码引用、`file:` / `link:` / `workspace:` 依赖或指向其他模块的构建配置，因此无需修改。未修改 Blog。

## 启动

需要 Node.js `^22.18.0 || >=24.12.0`、pnpm `11.9.0`。在本目录执行：

```powershell
pnpm install --frozen-lockfile
pnpm dev
```

开发地址：`http://127.0.0.1:5176`，也可使用 `http://localhost:5176`。端口占用时直接报错，不自动换端口。复制 `.env.example` 为 `.env.local`，通过 `API_PROXY_TARGET` 指向后端；默认 `http://localhost:5922`。登录账号必须具有 `admin:access`；当前初始化脚本已包含用户指定的管理员，见[数据库说明](../../sql/README.md)。

后端使用 `dev` 环境时默认放行上述两个开发 Origin。Vite 的 `changeOrigin` 只修改转发的 Host，仍保留浏览器 Origin，因此使用开发代理也需要后端放行来源。设置 `FRONTEND_ORIGINS` 会替换全部默认来源；自定义域名或端口需写入该变量，修改后重启后端。不要通过删除 Origin 或放行通配符绕过来源检查。

```powershell
pnpm test
pnpm build
pnpm preview
```

构建输出为本模块 `dist/`。生产环境独立部署该目录，并将站内路由回退到 `index.html`；将 `/api/v1` 同源反向代理到后端。跨域部署时设置 `VITE_API_ORIGIN` 为后端源站，不包含 `/api/v1`，同时将 Admin 的精确 Origin 加入后端 `FRONTEND_ORIGINS`。

## 已实现内容

| 模块           | 功能                                                                                                                              |
| -------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| 认证与个人账号 | 账密、绑定邮箱验证码和图形验证码组合登录、个人资料、设置/修改/清除密码、第三方绑定与解绑、退出 |
| 文章           | 分页、多条件筛选、Markdown 编辑、保存草稿、发布/下架、置顶、可见性、分类/标签、图片附件、服务端安全 HTML 预览、回收/恢复/永久删除 |
| 内容管理       | 分类、标签、友链、音乐的查询与增删改；文件上传、授权预览与删除                                                                    |
| 互动           | 评论与留言查询、详情、审核、删除占位；后台回复留言；订阅详情、退订、已验证订阅重新激活与删除                                      |
| 系统           | 用户创建、资料与状态、角色分配；角色与 ALL/SELF、菜单/权限授权；权限字典；目录/页面/按钮菜单；站点及评论/留言配置；操作日志与清理 |
| 展示管理       | 主页公开资料、社交链接、教育/工作/项目经历、技能                                                                                  |
| 访问统计       | 区间概览、每日浏览与新访客趋势、地域分布、文章排行、访客详情及采集限制、浏览记录                                                  |

菜单组件键与初始化 SQL 一致，集中登记在 `src/navigation.js`，只能加载本地白名单组件。按钮不生成路由；隐藏页面仍保留已授予的访问能力。前端按有效权限和独立 ALL/SELF 范围展示操作，后端执行实际授权。字典选择器读取全部分页；授权表单保留原有 ID，停用项需主动取消后再保存。

## 请求与安全行为

- API 前缀集中为 `/api/v1`。后台完整认证固定有效 7 天（604800 秒），禁止第三方直接登录；localStorage 的 `dl-admin-session` 仅保存 Token 和固定到期时间，通过 Bearer 发送，不写 Cookie、URL 或日志。刷新或重新打开页面后重读本人资料、权限和菜单，确认 `admin:access` 后先注册动态路由再导航，不续期。浏览器禁用存储时仅本页有效。前台及历史会话均不能代替后台完整认证。
- ID、ID 数组、统计与字节数保持十进制字符串。分页页码使用数字，时间筛选转为 UTC 毫秒，报表使用 UTC 自然日。
- PUT 只提交完整的允许字段，PATCH 明确区分缺失和 null；不会把响应整行、作者、审计时间或 HTML 提交给后端。
- 管理端删除、审核、发布/下架、恢复、封禁/解封等 26 个动作使用统一的单条/批量入口，18个支持批量动作的管理列表均提供首列复选框、表头全选当前页、已选数量、取消选择和权限控制的批量操作栏；单条按钮继续提交一项数组。翻页、修改筛选、刷新或更换每页条数时清空选择，不跨页累计ID。原单条管理写路径已移除，前后端需同步部署；需要版本的动作把详情版本放入 `items[].revision`，不发送 `If-Revision`；完整编辑仍沿用原版本头。文件清理检查每项结果，外层 HTTP 200 不代表文件全部删除成功。
- 编辑先取详情，再原样携带带引号的 `X-Revision`。412 保留表单输入并提示重新获取后合并，428 提示补充版本；失败写入不自动重试。400 展示字段错误，429 使用 `Retry-After`。
- 文章编辑复用旧后台的 Markdown 编辑器、实时预览、表情、封面上传及发布设置布局。实时预览经 HTML 清理且仅用于编辑，已保存内容预览仍使用后端清理的 HTML。附件需先创建草稿；后台图片携带 Bearer 读取为临时对象 URL，关闭预览或离开时释放。
- 保存只记录本次提交快照，请求期间的新输入保持未保存；草稿创建后的 URL 同步不会重建编辑器。发布按保存、携最新版本发布、返回动态菜单文章列表的顺序执行；发布失败保留草稿和 ID，重试使用更新接口。发布期间继续输入时留在编辑页，避免丢失修改；列表启用 keepAlive 时返回也会刷新。离页可选择保存并离开、不保存或关闭弹窗继续编辑。
- 修改/清除密码、解绑成功或会话失效会清除内存身份与本端持久凭据；密码错误不会无条件注销已有会话。角色/菜单/账号变更造成的会话撤销遵从后端返回。

## OAuth 部署

后台登录必须先填写用户名、密码、账号当前绑定邮箱和图形验证码，再发送邮箱验证码；发送成功后填写邮件数字及刷新后的图形验证码登录。邮件验证码五分钟有效，每邮箱一分钟一次；邮箱必须已配置到账号，发送失败明确报错。首次部署或旧管理员缺少邮箱时，可先从前台账密登录维护账号邮箱。第三方仅用于个人设置中的绑定，不再作为后台登录方式。

完成页为 `/oauth/complete`。后端只有一个全局 `OAUTH_COMPLETE_URL`，使用此 Admin 完成页时，应将它配置为 **Admin 自己的 HTTPS Origin + `/oauth/complete`**，并将该 Origin 加入 `FRONTEND_ORIGINS`；平台回调仍配置到后端 `/api/v1/admin/auth/oauth/{source}/callback`。不能指向另一个前端模块来隐式共享登录身份。若未来要求多个不同 Origin 同时各自完成 OAuth，需要先扩展后端已校验的完成页配置，本次没有修改此后端契约。

完成页读取 ticket 后立即清除 fragment。绑定弹窗通过精确同源 `postMessage` 将票据交回保留 Token 的原页面，原页面校验 origin、source 和当前绑定流程后兑换。300 秒敏感认证窗口、Cookie、平台可用性与一次性票据均由后端控制。

## 验证

`pnpm test` 使用 Node 内置测试运行器，覆盖真实请求与会话模块的版本头、字符串 ID、分页、multipart、错误分类、限流、私密图片地址、菜单白名单与权限范围、密码字节限制、列表取消和错误状态。

浏览器检查文件为 `tests/browser-smoke.mjs`，用 Playwright CLI 在 HTTP 边界提供明确的虚构数据，检查全部 22 个管理页面、10 类编辑表单提交、文章保存请求、版本冲突保留输入、OAuth 绑定弹窗携带原会话兑换、移动端、刷新后恢复登录及固定到期时间。在已运行 `pnpm dev` 且本机存在可用浏览器时执行：

```powershell
npx --yes --package @playwright/cli playwright-cli -s=drifting-admin open http://127.0.0.1:5176/login
npx --yes --package @playwright/cli playwright-cli -s=drifting-admin run-code --filename tests/browser-smoke.mjs
npx --yes --package @playwright/cli playwright-cli -s=drifting-admin close

# 文章编辑回归使用独立会话，避免前一轮测试的会话初始化脚本干扰。
npx --yes --package @playwright/cli playwright-cli -s=article-editor open http://127.0.0.1:5176/login
npx --yes --package @playwright/cli playwright-cli -s=article-editor run-code --filename tests/article-editor-smoke.mjs
npx --yes --package @playwright/cli playwright-cli -s=article-editor close
```

`tests/article-editor-smoke.mjs` 检查连续新建独立文章、发布后返回列表、草稿续存、发布失败重试、请求期间输入与重复快捷键、最新版本头、缓存列表刷新、安全实时预览、移动端及详情失败禁用保存。

列表分页沿用老项目：每页可选 10/15/20/50 条，支持数字页码与跳页；切换条数回到第一页并保留筛选。两端动态分页回归在仓库根目录执行 `npx --package @playwright/cli playwright-cli -s=pagination run-code --filename Front/shared/pagination-browser-smoke.mjs`，运行前启动后台 5176 和博客 5174 的 Vite 服务，并用同一会话执行 `open about:blank`。该脚本挂载真实分页组件，在 HTTP 请求处使用虚构数据。

浏览器截图保存在忽略的 `output/playwright/` 下。测试替身与生产构建不代表真实后端、数据库、OSS、SMTP 或第三方平台联调已完成。本次不启动后端、不操作业务数据库。

## 服务器异常与限流

网络连接失败、HTTP 5xx、无法解析的成功响应及 HTTP 429 显示参考 FeiTwnd-Website 对应端的全屏 404 样式；429 保留 Retry-After 秒数提示。请求仍失败且不自动重放，普通校验、鉴权与版本冲突沿用原逻辑。点击“返回首页”重新进入站点；如有未提交编辑，应重新加载后再操作。统计采集失败保持静默，不切换异常页。

公共异常边界验证：在仓库根目录执行 `node --test Front/shared/requestFailure.test.mjs`。

按本站要求，已有动画和过渡始终启用，不随系统“减少动态效果”设置关闭。

UI 资源位于 `src/assets/`；管理区保留持久登录与动态权限菜单；登录页保持当前 UI、OAuth 和表单校验。服务器异常与限流继续使用 FeiTwnd 的 404 展示，保留本站 HTTP 错误分类及 Retry-After。参考工程只读，不复制凭据、部署地址或业务数据。

侧栏图标在 `src/navigation.js` 的页面白名单中按用途指定：22 个业务入口及个人资料入口各不相同。老项目已有图标使用本地 iconfont，标签、主页、社交、经历、技能和访客使用已安装的 Element Plus 图标补齐，无需修改后端菜单数据。

后台主体、导航、常规按钮和普通标签保持克制的黑白灰风格。仅在关键语义处使用颜色：绿色表示发布、通过、启用与恢复，橙色表示草稿、待审核及下架等提醒操作，红色表示驳回、禁用、限制和删除，紫色突出置顶与私密留言。统计卡片仅用小面积图标区分指标，待审数量用橙色提醒，图表保留系列配色以便阅读。单条与批量操作保持一致，状态文字继续保留，不仅依赖颜色传达含义。

下拉框的焦点边框由 Element Plus 外层统一显示，全局键盘焦点规则排除其内部搜索输入框，避免出现截断的重复黑框。`tests/form-focus-smoke.mjs` 可通过 Playwright CLI 的 `run-code --filename` 执行，检查选择、搜索、多选、文本、密码、数字、多行、日期、日期时间、日期区间及真实分页组件，并确认无匹配结果和键盘焦点正常。

统计报表使用与老管理端相同的 ECharts 5.6.0，复用折线渐变、柱体和环形图的入场与更新动画，以及日期提示、柱体阴影、扇区高亮和图例切换。原有每日数据、文章排行和省份明细保留在可展开区域，提示和明细保留 BIGINT 原始计数。图表按容器尺寸自动重绘，离开页面时释放实例。`pnpm test` 包含真实图表渲染、精确计数提示、标题转义、零值和空数据验证。

`tests/charts-smoke.mjs` 挂载真实统计页面，以虚构 HTTP 数据检查四张图表的实际入场动画、鼠标悬停、图例点击、键盘展开明细、375px 布局、零值与空结果刷新及卸载清理。启动 `pnpm dev` 后执行：

```powershell
npx --yes --package @playwright/cli playwright-cli -s=charts open http://127.0.0.1:5176/login
npx --yes --package @playwright/cli playwright-cli -s=charts run-code --filename tests/charts-smoke.mjs
npx --yes --package @playwright/cli playwright-cli -s=charts close
```

浏览器检查还覆盖登录页设计保留、侧栏图标唯一性、折叠与展开、keepAlive 状态，以及未知路由、429、503、网络异常的全屏 404 与返回入口。

图形验证码复用 `Front/shared/CaptchaField.vue`：通过本端 `/auth/captcha` 获取六位数字圆圈验证码，120 秒有效。登录与 OAuth LOGIN 提交 `X-Captcha-Id`、`X-Captcha-Code`；博客注册、评论、留言及本人留言编辑同样校验；后台留言回复不要求图形验证码，仍执行后台登录、权限和限流。提交后刷新图片，失败保留正文。跨域白名单须允许两个验证码头；OAuth BIND、回调、兑换仍沿用原有 Cookie/Origin/Bearer 流程，详见[接口文档](../../docs/API.md#captcha)。


## 批量选择交互

复用 `D:/CodingFiles/Blog/fet/FeiTwnd-Website/Frontend-Admin` 的 Element Plus 48px 选择列、当前页全选、空选择禁用与按数量确认方式，保留本站布局、权限与接口协议。文章的发布/下架、回收/恢复/永久删除，评论/留言的审核/分支删除，访客的封禁/解封和其他现有批量清理动作均已接入。用户、单例资料和配置没有对应批量接口，不新增无效多选入口。

需要版本的批次先顺序读取所选详情，再确认并提交一个写请求，最多100项；取消或读取失败不执行写入，版本冲突保留选择并提示重新读取，不自动降级逐条提交。评论/留言删除确认说明全部后代影响；菜单只选择叶子，内置角色不可选；访客封禁需填写结束时间或明确勾选永久。文件清理显示成功/失败数量，并逐项列出失败ID、原因及错误码。

共用实现为 `src/components/BatchActions.vue` 和 `src/batch.js`。单元测试使用 `pnpm test`；浏览器夹具 `tests/batch-selection-smoke.mjs` 使用虚构HTTP数据驱动18个真实Vue页面，不连接业务服务，可经Playwright CLI的 `run-code --filename` 执行。

2026-11-01 批量选择验收：`pnpm test` 24项通过，`pnpm build`成功；Playwright虚构HTTP夹具验证18个真实页面、47项检查通过，覆盖当前页全选/取消、翻页清空、取消确认不写入、多项单次请求、版本冲突不重试、文件失败明细、永久限制明确选择和权限隐藏。桌面/手机截图位于仓库`output/playwright/batch-selection.png`及`batch-selection-mobile.png`；静态核对18页覆盖全部26个统一入口。本次未修改后端或连接业务库。

## 评论与留言的列表和详情

列表按内容、昵称、回复目标 ID、所属文章（评论）或可见性（留言）、状态、发表时间和操作分列，正文只预览一行，点击内容或详情查看全文。回复目标使用现有 V2 的 parentId，不额外请求或推测被回复者资料。

两类详情共用 DiscussionDetail：桌面双列、手机单列，展示当前接口已有的账号、记录 ID、状态、属地、系统、浏览器和时间；留言另含可见性、内容格式及通知意愿。正文置于带边框的内容区，纯文本保留换行，Markdown 沿用后端安全 HTML 与私密图片读取。SafeHtml 等待容器挂载后填入内容，修复首次打开详情时 Markdown 正文为空的问题。接口和数据库不变，后台回复继续无需验证码。

新增浏览器检查可通过 Playwright CLI 的 `run-code --filename Front/Front-Admin/tests/discussion-layout-smoke.mjs` 执行，覆盖 1440/1280/390 像素下的两类页面、长正文、纯文本转义、Markdown、历史未知字段、大整数 ID 及打开期间调整窗口。HTTP 边界使用虚构数据，截图位于仓库的 `output/playwright/comments-detail-*.png` 和 `messages-detail-*.png`。
