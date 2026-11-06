# 后端依赖与模块

项目尚未上线。本文说明当前依赖和模块职责；具体版本以四份 POM 为准，不再保留旧项目迁移计划和分章验收记录。

## 技术基线

| 能力 | 当前配置 | 用途 |
| --- | --- | --- |
| Java / Spring Boot | 21 / 4.1.2 | Spring MVC、虚拟线程及应用装配 |
| MyBatis-Plus | 3.5.17 | 实体映射、分页和事务内数据访问 |
| Druid | Boot 4 starter 1.2.28 | 数据库连接池；排除 MyBatis-Plus 传入的 HikariCP |
| MySQL / Redis | MySQL 8.0.16+；Redis 使用 Boot 管理的 Lettuce | 业务持久化、会话、票据、限流和公开查询缓存 |
| Sa-Token | 1.46.0 | 本站身份、会话及权限 |
| JustAuth | 1.16.7 | 第三方授权及绑定；不代替本站会话 |
| Fastjson 兼容包 | `com.alibaba:fastjson:2.0.66` | JustAuth 使用的 1.x API；不代替 MVC 的 Jackson 3 |
| CommonMark / jsoup | 0.31.0 / 1.23.2 | Markdown 解析及 HTML 清理 |
| Hutool | 5.8.48 | 字符串、摘要、图形验证码等实际使用的模块 |
| IP2Region | 3.3.7 | 离线 IPv4/IPv6 地域查询 |
| Aliyun OSS SDK | 3.18.5 | 文件对象存储 |
| Spring Mail / Security crypto | Boot 管理 | 邮件发送及 BCrypt；没有引入另一套登录鉴权 |

CommonMark 同版使用核心、表格、自动链接和任务列表扩展。Lombok 仅参与编译，不进入可执行 jar。Spring WebSocket 使用原生 JSON 文本消息，没有 STOMP、SockJS 或消息代理。

## 模块边界

| 模块 | 职责 | 依赖边界 |
| --- | --- | --- |
| [common](../Backend/common/pom.xml) | 共享结果、配置属性、工具、平台客户端、Redis 存储、会话和限流 | 不依赖 entity/server；声明实际使用的框架 API，应用 starter 留在 server |
| [entity](../Backend/entity/pom.xml) | 表实体、DTO、VO 和内部业务传输模型 | 持久化注解、校验 API、Lombok 和模型中实际使用的 Hutool core |
| [server](../Backend/server/pom.xml) | Controller、Service、Mapper、鉴权、审计、缓存、通知和应用装配 | 依赖 common/entity；唯一 Spring Boot 可执行应用 |

[父 POM](../Backend/pom.xml) 管理 Boot BOM 未覆盖的版本、内部模块版本和编译配置，不向所有模块统一塞入运行依赖。同一家族组件保持同版，不为单个工具引入完整 starter。

## 配置约束

- 数据源使用 Druid，连接初始化为 UTC，事务默认 READ COMMITTED；不启用 Druid Web 监控页。配置示例见[运行说明](后端运行说明.md#database)。
- Web JSON 使用 Boot 的 Jackson 3；主外键和 BIGINT 计数以字符串输出，时间固定为 UTC 毫秒。
- 公开缓存使用 Spring Cache 和现有 Redis 连接；会话、权限、票据和私密内容不进入公共缓存。
- OSS、邮箱、OAuth、数据库和 Redis 的凭据来自本地忽略配置或外部配置，不写入文档和 POM。
- 当前不维护测试源码或 main 自检类；编译和打包方式见[运行说明](后端运行说明.md#checks)。

后端没有额外的 JWT、SecurityFilterChain、消息队列、服务器监控、聊天或组织部门模块。新增依赖以实际消费者为依据，不按旧规划补装。
