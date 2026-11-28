# DriftingLeaves V2 Docker 部署

## 准备产物

| 构建产物 | 放到部署目录 |
| --- | --- |
| `Backend/server/target/server-2.0.0-SNAPSHOT.jar` | `backend/DriftingLeaves.jar` |
| `Front/Front-Home/dist/` | `html/Home/dist/` |
| `Front/Front-Blog/dist/` | `html/Blog/dist/` |
| `Front/Front-Admin/dist/` | `html/Admin/dist/` |
| `Front/Front-Cv/dist/` | `html/CV/dist/` |

后端在仓库根目录执行 `mvn -B -ntp -f Backend/pom.xml verify` 构建；打包时已自动排除本地 `application-*.yml`，JAR 不包含生产凭据。四个前端分别执行 `pnpm install --frozen-lockfile` 和 `pnpm build`。Home、CV 使用同源反代时，构建环境里的 `VITE_API_ORIGIN` 留空。上传部署目录时带上有效的 `SSL/` 证书和私钥。

## 首次启动

现有 `driftingleaves` 网络应包含 `mysql_8` 和 `redis_7`。后端容器以非 root 用户 `app` 和 `prd` 环境运行，连接的 V2 数据库须事先准备。用 [V2 SQL 基线](../sql/drifting_leaves_v2.sql) 初始化时，先确认目标为空库，库名和生产配置的 `DB_URL` 一致。

在服务器的部署目录首次配置时：

1. 将 `config/application-prd.yml.example` 复制为 `config/application-prd.yml`（挂载到容器 `/app/config/application-prd.yml`，由 Spring Boot 自动加载，不纳入 Git）。
2. 将 `.env.example` 复制为 `.env`，填写当前有效的数据库、Redis、SMTP、OSS AccessKey、OAuth 凭据和 `TRUSTED_PROXIES`。已有 `.env` 时只更新相关项，保留其他配置。Compose 将这些变量明确传入后端；仅在宿主机放置 `.env` 不会自动注入容器。启用 OSS 时，凭据为空会由应用启动校验报错；显式关闭 OSS 的行为保持不变。

在服务器的部署目录执行：

```bash
docker-compose config --quiet
docker-compose pull nginx
docker-compose up -d --build
docker-compose exec nginx nginx -t
docker-compose ps
```

后端的健康检查调用 `/api/v1/home/profile`，Nginx 在后端健康检查通过后启动（`condition: service_healthy`），其健康检查访问首页静态产物。挂载的 `uploads` 卷用于临时文件和本地附件，后端重建时保留。

## 配置可信代理

Nginx 启动后，查询它在同一个 Docker 网络上的地址：

```bash
docker inspect --format '{{with index .NetworkSettings.Networks "driftingleaves"}}{{.IPAddress}}{{end}}' driftingleaves-nginx
```

`TRUSTED_PROXIES` 不再内置固定单 IP 回退值，未配置时后端安全忽略转发头。请在部署目录的 `.env` 设置 `TRUSTED_PROXIES=<查到的 Nginx IP>`，执行 `docker-compose up -d --no-deps backend` 让后端加载。用 `docker exec driftingleaves-backend printenv TRUSTED_PROXIES` 核对容器实际收到的值，使限流和封禁按访客 IP 生效。以后若重建 Nginx，再查一次并按实际地址更新。

配置更新后用 `docker-compose up -d --no-deps backend` 重建后端容器，单独 `restart` 不会加载新的环境变量。通过公开域名产生一次新访问，检查访客 IP 和地域；已有的 Docker 内网访问记录无法还原当时的真实 IP，保留原记录。

## 可选运维入口与验收

主 `nginx/nginx.conf` 仅包含四端业务站点，并统一开启 `server_tokens off`、安全响应头（`nginx/security-headers.conf`）及四端 `index.html` 的 `no-cache`。如需反代独立运维面板，请在服务器将 `nginx/conf.d/ops.conf.example` 复制为 `nginx/conf.d/ops.conf`（已被 Git 忽略），填写实际域名、上游地址与受信 CA 证书，并保持 `proxy_ssl_verify on` 和 `Connection $connection_upgrade`。

OSS 验收需执行一次上传、读取和删除，确认私有对象经本站接口可读，并清理本次测试文件。`InvalidAccessKeyId` 表示云端不认可当前 AccessKey，需在阿里云确认凭据有效，不能通过更改 Bucket ACL 或切换本地存储解决。

首次部署后，从浏览器验证四端的静态页面和各自的 `/api/v1/` 请求，再检查 Blog 登录和在线人数连接。
