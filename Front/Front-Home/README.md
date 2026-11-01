# 主页端 Front-Home

依据 [API.md](../../docs/API.md) 实现个人主页，视觉参考 `FeiTwnd-Website/Frontend-Home`：黑白配色、居中圆角卡片、圆形头像、社交导航、备案与版权信息。支持系统深浅主题、移动端和键盘焦点。按本站要求，已有动画和过渡始终启用，不随系统“减少动态效果”设置关闭。

## 启动

需要 Node.js `^22.18.0 || >=24.12.0`、pnpm `11.9.0`，在本目录执行：

```powershell
pnpm install --frozen-lockfile
pnpm dev
```

默认地址为 `http://127.0.0.1:5173`。开发服务器将 `/api/v1` 原样代理到 `http://localhost:5922`。复制 `.env.example` 为 `.env.local` 可修改 `API_PROXY_TARGET`。

端口占用时直接报错，不自动换端口。后端 `dev` 默认放行 `http://127.0.0.1:5173` 与 `http://localhost:5173`；代理保留浏览器 Origin，仍需通过后端来源检查。主页采集的默认 `HOME_BASE_URL` 为 `http://127.0.0.1:5173`，使用 `localhost` 时将其改为 `http://localhost:5173`。显式设置 `FRONTEND_ORIGINS` 会替换默认白名单，自定义地址需同步配置，修改后重启后端。

```powershell
pnpm test
pnpm build
pnpm preview
```

生产产物位于 `dist/`。同源部署由反向代理转发 `/api/v1`；跨域部署在构建前设置 `VITE_API_ORIGIN`，值为后端公开源站，不含 `/api/v1`。开发代理不会随静态产物部署。

## 接口与展示

| 接口 | 用途 |
| --- | --- |
| `GET /api/v1/home/profile` | 昵称、标签、简介、头像、所在地与公开联系方式 |
| `GET /api/v1/home/social-links` | 后端排序的社交导航，保留字符串 ID |
| `GET /api/v1/home/configs` | 公开 site 标题、描述、备案和建站日期 |
| `POST /api/v1/home/analytics/page-views` | 一次主页浏览，`siteCode=home`、`articleId=null` |

资料、社交和配置独立处理加载、失败、重试与空数据，不用示例数据替代接口结果。未配置头像或图片加载失败时显示昵称首字。链接仅使用 HTTP(S) 或单一邮箱 mailto；图标 URL 使用 HTTP(S)，图标键仅匹配本地 SVG 白名单，未知键回落为链接图标。兼容参考工程的 `boke`、`jianli`、`youxiang`、`bilibili-line`、`csdn1` 键。博客、简历等入口由后台社交链接配置，不写死部署地址。

只读取 `publicEmail`，不读取账号邮箱。本端无需登录，不持有 Token。浏览器本地存储仅保存随机统计标识，不作为身份；存储不可用时当前页面仍可生成标识。采集移除页面和来源的全部查询参数与 fragment，不自动重试 POST。采集失败只输出不含票据或标识的诊断，不影响公开阅读。后端须配置允许的 Home 来源，与实际开发或部署 Origin 对应。

建站日期存在且早于当前年时展示版权年份区间；未设置时只展示当前年。备案字段为空时不占位，不填入虚构备案。

## 验证

`pnpm test` 使用 Node 内置测试运行器，验证实际请求适配、数据与 URL 边界、限流错误、字符串 ID、采集字段和脱敏。仅在 HTTP 与浏览器存储边界使用替身，不代表真实后端、数据库或统计服务联调。

## 服务器异常与限流

网络连接失败、HTTP 5xx、无法解析的成功响应及 HTTP 429 显示参考 FeiTwnd-Website 对应端的全屏 404 样式；429 保留 Retry-After 秒数提示。请求仍失败且不自动重放，普通校验、鉴权与版本冲突沿用原逻辑。点击“返回首页”重新进入站点；如有未提交编辑，应重新加载后再操作。统计采集失败保持静默，不切换异常页。

公共异常边界验证：在仓库根目录执行 `node --test Front/shared/requestFailure.test.mjs`。
