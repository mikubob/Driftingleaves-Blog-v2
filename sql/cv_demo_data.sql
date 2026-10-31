-- 仅用于本地展示的虚构简历；先完成 V2 表结构初始化，再执行本脚本。
-- 只补充缺失条目，不修改已有资料，不创建密码或授予任何角色。
-- SQL 导入不触发应用缓存失效，完成后重启后端再刷新简历页。
USE driftingleaves;
SET NAMES utf8mb4;
START TRANSACTION;

-- 公开资料需要关联账号，该账号没有密码、OAuth 绑定或角色，不能用于登录。
INSERT INTO sys_user (username, password_hash, nickname, intro, status)
SELECT 'cv_demo_owner', NULL, '简历演示账号', '仅用于关联虚构简历展示资料。', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'cv_demo_owner');

INSERT INTO home_profile (
  profile_id, user_id, nickname, tagline, description,
  avatar, website, public_email, github_url, location
)
SELECT 1, user_id, '落叶 · 演示简历', '全栈开发工程师 · Java / Vue',
  '这是一份用于页面展示的虚构简历。专注于后端服务与前端交互，喜欢把复杂问题整理成清晰、可靠的产品体验。请将以下示例替换为自己的真实资料。',
  NULL, 'https://example.com', 'demo@example.com', 'https://github.com', '中国 · 杭州（演示）'
FROM sys_user
WHERE username = 'cv_demo_owner'
  AND NOT EXISTS (SELECT 1 FROM home_profile WHERE profile_id = 1);

INSERT INTO home_social_link (name, icon, url, sort_order, status)
SELECT demo.name, demo.icon, demo.url, demo.sort_order, 1
FROM (
  SELECT 'GitHub' AS name, 'github' AS icon, 'https://github.com' AS url, 10 AS sort_order
  UNION ALL
  SELECT '联系我（演示）', 'mail', 'mailto:demo@example.com', 20
) AS demo
WHERE NOT EXISTS (SELECT 1 FROM home_social_link WHERE url = demo.url);

INSERT INTO cv_experience (
  experience_type, title, subtitle, content, start_date, end_date, sort_order, status
)
SELECT demo.experience_type, demo.title, demo.subtitle, demo.content,
  demo.start_date, demo.end_date, demo.sort_order, 1
FROM (
  SELECT 0 AS experience_type, '示例大学（演示）' AS title,
    '计算机科学与技术 · 本科' AS subtitle,
    '- 系统学习数据结构、操作系统、计算机网络与数据库原理。
- 参与课程设计与团队项目，完成从需求分析到部署的完整实践。
- 本条为虚构教育经历，仅用于展示。' AS content,
    '2020-09-01' AS start_date, '2024-06-30' AS end_date, 10 AS sort_order
  UNION ALL
  SELECT 1, '示例科技团队（演示）', 'Java 开发工程师',
    '- 使用 **Spring Boot** 设计与维护业务接口，关注数据一致性和接口可读性。
- 结合 MySQL 与 Redis 优化数据访问，完善自动化测试与运行文档。
- 与前端协作完成需求交付和线上问题排查。',
    '2024-07-01', NULL, 20
  UNION ALL
  SELECT 2, '个人内容与简历平台（演示）', '独立开发 · Java / Vue / MySQL / Redis',
    '### 项目介绍
一个集内容创作、个人主页与在线简历于一体的示例网站。

- 设计文章、分类、标签、经历和技能模块，建立清晰的数据关系。
- 实现基于角色的权限控制、内容分页与公开展示接口。
- 使用 Vue 3 构建响应式页面，支持深浅主题、移动端与简历打印。',
    '2025-03-01', NULL, 30
  UNION ALL
  SELECT 2, '团队任务协作工具（演示）', '后端开发 · 接口设计与工程实践',
    '### 项目职责
- 围绕任务、成员和进度设计 REST API 与数据库模型。
- 实现参数校验、分页检索与统一错误反馈。
- 编写关键业务测试，整理部署说明和接口文档。',
    '2024-09-01', '2025-02-28', 40
) AS demo
WHERE NOT EXISTS (
  SELECT 1 FROM cv_experience
  WHERE experience_type = demo.experience_type AND title = demo.title
);

INSERT INTO cv_skill (name, description, icon, sort_order, status)
SELECT demo.name, demo.description, demo.icon, demo.sort_order, 1
FROM (
  SELECT 'Java' AS name, '熟悉集合、异常处理与并发基础，重视清晰命名、职责划分和代码可读性。' AS description,
    'document' AS icon, 10 AS sort_order
  UNION ALL
  SELECT 'Spring Boot', '能够构建 REST API，理解依赖注入、参数校验、事务与统一异常处理。', 'setting', 20
  UNION ALL
  SELECT 'MySQL', '熟悉关系型数据建模、索引与事务，能够分析常见查询并优化访问路径。', 'chart', 30
  UNION ALL
  SELECT 'Redis', '了解缓存、过期策略与限流场景，关注缓存更新和数据库数据的一致性。', 'folder', 40
  UNION ALL
  SELECT 'Vue 3', '使用组合式 API 构建响应式界面，关注组件复用、移动端适配与可访问性。', 'picture', 50
  UNION ALL
  SELECT 'Git 与工程协作', '使用 Git 管理代码，结合自动化测试、构建和文档保持交付质量。', 'github', 60
) AS demo
WHERE NOT EXISTS (SELECT 1 FROM cv_skill WHERE name = demo.name);

COMMIT;
