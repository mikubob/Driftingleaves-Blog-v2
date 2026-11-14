# DriftingLeaves V2 Docker 部署

## 准备产物

| 构建产物 | 放到部署目录 |
| --- | --- |
| `Backend/server/target/server-2.0.0-SNAPSHOT.jar` | `backend/DriftingLeaves.jar` |
| `Front/Front-Home/dist/` | `html/Home/dist/` |
| `Front/Front-Blog/dist/` | `html/Blog/dist/` |
| `Front/Front-Admin/dist/` | `html/Admin/dist/` |
| `Front/Front-Cv/dist/` | `html/CV/dist/` |

后端构建见[运行说明](../docs/后端运行说明.md)。四个前端分别执行 `pnpm install --frozen-lockfile` 和 `pnpm build`。Home、CV 使用同源反代时，构建环境里的 `VITE_API_ORIGIN` 留空。上传部署目录时带上有效的 `SSL/` 证书和私钥。JAR 如果包含其他本地生产凭据，与 TLS 私钥一样仅通过私有渠道传输。

## 首次启动

现有 `driftingleaves` 网络应包含 `mysql_8` 和 `redis_7`。后端以 `prd` 环境运行，连接的 V2 数据库须事先准备。用 [V2 SQL 基线](../sql/drifting_leaves_v2.sql) 初始化时，先确认目标为空库，库名和生产配置的 `DB_URL` 一致。

在服务器的部署目录首次配置时，将 `.env.example` 复制为 `.env`，填写当前有效的 OSS AccessKey 和 Nginx 地址。已有 `.env` 时只更新相关项，保留其他配置。Compose 将这些变量明确传入后端；仅在宿主机放置 `.env` 不会自动注入容器。启用 OSS 时，凭据为空会由应用启动校验报错，不再使用 JAR 内的历史凭据；显式关闭 OSS 的行为保持不变。

在服务器的部署目录执行：

```bash
docker-compose config --quiet
docker-compose pull nginx
docker-compose up -d --build
docker-compose exec nginx nginx -t
docker-compose ps
```

后端的健康检查调用 `/api/v1/home/profile`，Nginx 的健康检查访问首页静态产物。挂载的 `uploads` 卷用于临时文件和本地附件，后端重建时保留。

## 配置可信代理

Nginx 启动后，查询它在同一个 Docker 网络上的地址：

```bash
docker inspect --format '{{with index .NetworkSettings.Networks "driftingleaves"}}{{.IPAddress}}{{end}}' driftingleaves-nginx
```

当前部署在变量未设置或为空时使用 `172.19.0.5`。如果查到的地址不同，在部署目录的 `.env` 设置 `TRUSTED_PROXIES=<查到的 Nginx IP>`，执行 `docker-compose up -d backend` 让后端加载。用 `docker exec driftingleaves-backend printenv TRUSTED_PROXIES` 核对容器实际收到的值，使限流和封禁按访客 IP 生效。以后若重建 Nginx，再查一次并按实际地址更新。

配置更新后用 `docker-compose up -d --no-deps backend` 重建后端容器，单独 `restart` 不会加载新的环境变量。通过公开域名产生一次新访问，检查访客 IP 和地域；已有的 Docker 内网访问记录无法还原当时的真实 IP，保留原记录。

OSS 验收需执行一次上传、读取和删除，确认私有对象经本站接口可读，并清理本次测试文件。`InvalidAccessKeyId` 表示云端不认可当前 AccessKey，需在阿里云确认凭据有效，不能通过更改 Bucket ACL 或切换本地存储解决。

首次部署后，从浏览器验证四端的静态页面和各自的 `/api/v1/` 请求，再检查 Blog 登录和在线人数连接。已有的 `bt.driftingleaves.xyz` 仍反代到原宝塔入口。
