# 简历端 Front-Cv

以 FeiTwnd-Website/Frontend-Cv 为视觉参考，采用 Vue 3 + Vite，展示公开个人资料、联系与社交链接、教育、工作/实习、项目经历和技能。支持系统深浅主题、手动切换、移动端与 A4 打印 / 导出 PDF。

## 启动与构建

需要 Node.js `^22.18.0 || >=24.12.0` 和 pnpm `11.9.0`，在本目录执行：

```powershell
pnpm install --frozen-lockfile
pnpm dev
```

默认开发地址为 `http://127.0.0.1:5175`。将 `.env.example` 复制为 `.env.local`，按实际环境设置 `API_PROXY_TARGET`；默认代理到 `http://localhost:5922`，不包含真实部署地址或凭据。

端口占用时直接报错，不自动换端口。后端 `dev` 默认放行 `http://127.0.0.1:5175` 与 `http://localhost:5175`；代理保留浏览器 Origin，仍需通过后端来源检查。开发 `CV_BASE_URL` 默认对应 `http://127.0.0.1:5175`，使用其他公开基址时显式覆盖。设置 `FRONTEND_ORIGINS` 会替换默认白名单，自定义域名或端口需同步配置，修改后重启后端。

```powershell
pnpm test
pnpm build
pnpm preview
```

生产静态产物位于 `dist/`。默认由同源反向代理把 `/api/v1` 转发给后端；跨域部署时，在构建前设置 `VITE_API_ORIGIN` 为后端公开源站（不含 `/api/v1`），并在后端放行简历站 Origin。开发代理不会随静态产物部署。

## 数据契约

依据 [API.md](../../docs/API.md) 第 0、12、15 章调用：

| 接口 | 用途 |
| --- | --- |
| `GET /api/v1/cv/profile` | 公开资料，允许 `data=null` |
| `GET /api/v1/cv/social-links` | 可见社交链接 |
| `GET /api/v1/cv/experiences?page=1&pageSize=100` | 全部公开教育、工作和项目经历 |
| `GET /api/v1/cv/skills?page=1&pageSize=100` | 全部公开技能 |

经历和技能按 `totalPages` 继续获取后续页，保留后端顺序；ID、分页统计保持十进制字符串。个人邮箱仅使用 `publicEmail`。经历正文仅展示后端清理的 `htmlContent`，不本地解析 Markdown。图标 URL 仅允许 HTTP(S)，图标键只解析本地白名单；未知图标键回落为链接图标。

各数据区独立呈现加载、错误与重试；没有公开数据时显示空状态，不填充示例履历。全部请求成功且有简历内容时启用打印，避免把失败的部分当成完整简历导出。本端只读取公开内容，不需要登录或保存 Token。

## 验证范围

`pnpm test` 使用 Node 内置测试运行器，在 HTTP 边界替换 `fetch`，验证真实请求适配、分页、字符串 ID、响应校验、公开联系字段和限流错误。替身测试不代表真实后端或数据库联调。

## 服务器异常与限流

网络连接失败、HTTP 5xx、无法解析的成功响应及 HTTP 429 显示参考 FeiTwnd-Website 对应端的全屏 404 样式；429 保留 Retry-After 秒数提示。请求仍失败且不自动重放，普通校验、鉴权与版本冲突沿用原逻辑。点击“返回首页”重新进入站点；如有未提交编辑，应重新加载后再操作。统计采集失败保持静默，不切换异常页。

公共异常边界验证：在仓库根目录执行 `node --test Front/shared/requestFailure.test.mjs`。

按本站要求，已有动画和过渡始终启用，不随系统“减少动态效果”设置关闭。
