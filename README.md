# DriftingLeavesV2

项目包含 Spring Boot 后端和 Home、Blog、Cv、Admin 四个独立的 Vue 前端。

新建空库使用 [初始化 SQL](sql/drifting_leaves_v2.sql) 导入表结构、权限和种子数据。已有 V2 数据库按 [数据库说明](sql/README.md) 执行适用的迁移。应用启动时不会自动建库或导入 SQL。

## 开发入口

| 模块 | 默认开发端口 | 说明 |
| --- | --- | --- |
| 后端 | 5922 | [父 POM](Backend/pom.xml)（构建：`mvn -B -ntp -f Backend/pom.xml verify`） |
| Home | 5173 | [个人主页](Front/Front-Home/README.md) |
| Blog | 5174 | [博客](Front/Front-Blog/README.md) |
| Cv | 5175 | [简历](Front/Front-Cv/README.md) |
| Admin | 5176 | [管理端](Front/Front-Admin/README.md) |

后端需要 JDK 21、Maven、MySQL 和 Redis。前端需要 Node.js `^22.18.0 || >=24.12.0`、pnpm `11.9.0`；在各前端目录运行 `pnpm install --frozen-lockfile`、`pnpm dev` 或 `pnpm build`。四端分别输出到各自的 `dist/`，开发代理配置不会打进部署产物。

Home、Blog、Cv、Admin 四端各自维护源码、资源、配置和依赖，可分别作为独立项目复制、安装、构建和部署。跨端不共享源码目录，也不引用兄弟工程。

各端 `pnpm build` 执行 Vite 生产构建，保留默认 500 kB 分块告警。组件按需导入和路由拆包继续保留，构建后应检查输出体积。

## 文档

- [接口索引](docs/API_INDEX.md)：按端和业务查找接口。
- [接口文档](docs/API.md)：四端文档入口、通用协议与错误码。
- [接口开发约定](docs/API_CONTRACT.md)：鉴权、事务、并发与资源访问规则。
- [数据库说明](sql/README.md)与[演示数据](sql/DEMO_DATA.md)。
- [Docker 部署](DockerForDriftingLeaves/README.md)。
- [后端依赖与模块](Backend/pom.xml)。
- [实时在线人数共用规则](docs/API.md#group-23)与[Markdown 解析与安全约定](docs/API_CONTRACT.md#models)。

文档只描述当前实现及使用方法。构建成功不代表外部平台联调、正式部署或上线验收完成。
