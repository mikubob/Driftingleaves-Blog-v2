# 简历端 Front-Cv

本工程独立维护，可单独复制、安装依赖、构建和部署。组件、工具和样式均位于本工程的 `src/`，修改只影响本端。

项目尚未上线，当前说明用于本地开发与构建。

以 FeiTwnd-Website/Frontend-Cv 为视觉参考，采用 Vue 3 + Vite，展示公开个人资料、联系与社交链接、教育、工作/实习、项目经历和技能。支持系统深浅主题、手动切换、移动端与 A4 打印 / 导出 PDF。

## 启动与构建

需要 Node.js `^22.18.0 || >=24.12.0` 和 pnpm `11.9.0`，在本目录执行：

```powershell
pnpm install --frozen-lockfile
pnpm dev
```

默认开发地址为 `http://127.0.0.1:5175`。将 `.env.example` 复制为 `.env.local`，按实际环境设置 `API_PROXY_TARGET`；默认将 `/api/v1/cv/` 代理到 `http://localhost:5922`，不包含真实部署地址或凭据。

端口占用时直接报错。开发代理保留浏览器 Origin。使用 `localhost` 或更改端口时，同步修改后端 `CV_BASE_URL` 并重启。公开 HTTP 跨域与在线连接都按该站配置的来源校验。

```powershell
pnpm build
pnpm preview
```

生产静态产物位于 `dist/`。默认由同源反向代理把 `/api/v1/cv/` 转发给后端；跨域部署时，在构建前设置 `VITE_API_ORIGIN` 为后端公开源站（不含 `/api/v1`），并将后端 `CV_BASE_URL` 指向简历站实际地址。开发代理不会随静态产物部署。

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

## 构建检查

使用 `pnpm build` 检查生产构建。

## 服务器异常与限流

网络连接失败、HTTP 5xx、无法解析的成功响应及 HTTP 429 进入全屏请求错误页，显示实际错误。HTTP 429 提示 Retry-After 等待时间。请求失败后可手动重试。统计采集失败保持静默。


按本站要求，已有动画和过渡始终启用，不随系统“减少动态效果”设置关闭。
