-- DriftingLeavesV2 通用演示数据，适用于 MySQL 8.0.16+ 和当前完整 V2 表结构。
-- 先在客户端选择目标 V2 开发库，再执行本脚本；不创建数据库，不改已有站点配置或管理员。
-- 所有姓名、正文、邮箱、IP、地域及日志均为虚构示例。账号无密码、无 OAuth 绑定，不能用于登录。
-- 重复导入会跳过仍匹配演示标识的记录，不重置已编辑的状态；导入期间暂停其他演示脚本。
-- 客户端必须遇错停止并执行 ROLLBACK，不要忽略错误继续执行 COMMIT。
-- 直接 SQL 不触发应用缓存失效，导入完成后重启后端，再刷新页面。
SET NAMES utf8mb4;
SET @dl_demo_previous_time_zone = @@SESSION.time_zone;
SET SESSION time_zone = '+00:00';

-- 提前检查最近新增的列；旧库先执行 sql/migrations 下缺少的升级脚本。
SELECT location, user_agent_os, user_agent_browser FROM blog_comment LIMIT 0;
SELECT location, user_agent_os, user_agent_browser FROM blog_message LIMIT 0;
SELECT last_login_ip, last_login_location FROM sys_user LIMIT 0;

-- 固定本批数据的时间锚点，重复导入时不因时间变化重复生成浏览明细。
SET @dl_demo_anchor = COALESCE(
  (SELECT DATE_ADD(first_visit_time, INTERVAL 30 DAY) FROM blog_visitor
   WHERE fingerprint = 'v2-demo-visitor-01'), UTC_TIMESTAMP(3)
);
DROP TEMPORARY TABLE IF EXISTS dl_demo_user_seed, dl_demo_article_seed, dl_demo_discussion_seed,
  dl_demo_comment_ids, dl_demo_message_ids, dl_demo_visitor_seed, dl_demo_numbers;
START TRANSACTION;

-- 一、展示账号：只关联既有 author/member 角色，不授予管理员或修改角色权限。
CREATE TEMPORARY TABLE dl_demo_user_seed (
  seed_key INT PRIMARY KEY, username VARCHAR(64), nickname VARCHAR(64), role_code VARCHAR(64),
  status TINYINT, location VARCHAR(192), ip VARCHAR(45), user_id BIGINT
);

INSERT INTO dl_demo_user_seed (seed_key, username, nickname, role_code, status, location, ip, user_id) VALUES
  (1, 'v2_demo_author_1', '落叶手记（演示）', 'author', 1, '湖南省 长沙市', '192.0.2.11', NULL),
  (2, 'v2_demo_author_2', '代码与远方（演示）', 'author', 1, '浙江省 杭州市', '192.0.2.12', NULL),
  (3, 'v2_demo_reader_01', '秋日来信（演示）', 'member', 1, NULL, '198.51.100.3', NULL),
  (4, 'v2_demo_reader_02', '山间清风（演示）', 'member', 1, '广东省 深圳市', '198.51.100.4', NULL),
  (5, 'v2_demo_reader_03', '星河旅人（演示）', 'member', 1, '江苏省 南京市', '198.51.100.5', NULL),
  (6, 'v2_demo_reader_04', '午后咖啡（演示）', 'member', 1, NULL, '198.51.100.6', NULL),
  (7, 'v2_demo_reader_05', '海边拾光（演示）', 'member', 1, '广东省 深圳市', '198.51.100.7', NULL),
  (8, 'v2_demo_reader_06', '停用读者（演示）', 'member', 0, '江苏省 南京市', '198.51.100.8', NULL);

INSERT INTO sys_user (username, password_hash, nickname, email, intro, status,
  last_login_time, last_login_ip, last_login_location, create_time, update_time)
SELECT s.username, NULL, s.nickname, CONCAT(s.username, '@example.test'),
  '[V2 演示] 虚构展示账号，无密码，不可登录。', s.status,
  DATE_SUB(@dl_demo_anchor, INTERVAL s.seed_key HOUR), s.ip, s.location,
  DATE_SUB(@dl_demo_anchor, INTERVAL 60 DAY), DATE_SUB(@dl_demo_anchor, INTERVAL s.seed_key HOUR)
FROM dl_demo_user_seed s
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.username = s.username);
UPDATE dl_demo_user_seed s JOIN sys_user u ON u.username = s.username SET s.user_id = u.user_id;
INSERT INTO sys_user_role (user_id, role_id)
SELECT s.user_id, r.role_id FROM dl_demo_user_seed s JOIN sys_role r ON r.role_code = s.role_code
WHERE NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = s.user_id AND ur.role_id = r.role_id);

-- 二、分类和标签：使用独立演示名称与 slug，不修改已有字典。
INSERT INTO blog_category (category_name, slug, description, sort_order, status)
SELECT s.name, s.slug, '[V2 演示] 用于列表、筛选和隐藏分类的展示。', s.sort_order, s.status
FROM (
  SELECT '[演示] 后端开发' AS name, 'v2-demo-backend' AS slug, 901 AS sort_order, 1 AS status
  UNION ALL SELECT '[演示] 前端实践', 'v2-demo-frontend', 902, 1
  UNION ALL SELECT '[演示] 生活随笔', 'v2-demo-life', 903, 1
  UNION ALL SELECT '[演示] 隐藏分类', 'v2-demo-hidden', 904, 0
) s
WHERE NOT EXISTS (SELECT 1 FROM blog_category c WHERE c.slug = s.slug OR c.category_name = s.name);
INSERT INTO blog_tag (tag_name, slug)
SELECT s.name, s.slug FROM (
  SELECT '[演示] Java' AS name, 'v2-demo-java' AS slug
  UNION ALL SELECT '[演示] Vue', 'v2-demo-vue'
  UNION ALL SELECT '[演示] MySQL', 'v2-demo-mysql'
  UNION ALL SELECT '[演示] Redis', 'v2-demo-redis'
  UNION ALL SELECT '[演示] 日常', 'v2-demo-daily'
  UNION ALL SELECT '[演示] 工程实践', 'v2-demo-engineering'
) s
WHERE NOT EXISTS (SELECT 1 FROM blog_tag t WHERE t.slug = s.slug OR t.tag_name = s.name);

-- 三、24 篇文章：公开、草稿、下架、私密、回收站和未来发布时间均有样例。
CREATE TEMPORARY TABLE dl_demo_article_seed (
  seed_key INT PRIMARY KEY, title VARCHAR(200), category_slug VARCHAR(100), author_seed INT,
  article_type TINYINT, status TINYINT, visibility TINYINT, is_top TINYINT, allow_comment TINYINT,
  publish_days INT, in_trash TINYINT, article_id BIGINT
);

INSERT INTO dl_demo_article_seed (seed_key, title, category_slug, author_seed, article_type, status, visibility, is_top, allow_comment, publish_days, in_trash, article_id) VALUES
  (1, '[演示] 从零梳理一次 Spring Boot 请求', 'v2-demo-backend', 2, 1, 1, 1, 1, 1, 2, 0, NULL),
  (2, '[演示] Vue 组件里的状态应该放在哪里', 'v2-demo-frontend', 1, 1, 1, 1, 1, 1, 4, 0, NULL),
  (3, '[演示] 一杯咖啡和一个安静的周末', 'v2-demo-life', 2, 1, 1, 1, 0, 1, 6, 0, NULL),
  (4, '[演示] MySQL 索引为什么没有生效', 'v2-demo-backend', 1, 1, 1, 1, 0, 1, 8, 0, NULL),
  (5, '[演示] 让评论与留言更容易阅读', 'v2-demo-frontend', 2, 1, 1, 1, 0, 1, 10, 0, NULL),
  (6, '[演示] 把日常写成一封长信', 'v2-demo-life', 1, 1, 1, 1, 0, 1, 12, 0, NULL),
  (7, '[演示] Redis 缓存更新的几个细节', 'v2-demo-backend', 2, 1, 1, 1, 0, 1, 14, 0, NULL),
  (8, '[演示] 一次界面适配的记录', 'v2-demo-frontend', 1, 1, 1, 1, 0, 1, 16, 0, NULL),
  (9, '[演示] 在城市里寻找一条散步路线', 'v2-demo-life', 2, 1, 1, 1, 0, 1, 18, 0, NULL),
  (10, '[演示] 接口错误信息也需要认真设计', 'v2-demo-backend', 1, 1, 1, 1, 0, 1, 20, 0, NULL),
  (11, '[演示] 这篇文章关闭了评论', 'v2-demo-frontend', 2, 1, 1, 1, 0, 0, 22, 0, NULL),
  (12, '[演示] 雨天整理书桌', 'v2-demo-life', 1, 1, 1, 1, 0, 1, 24, 0, NULL),
  (13, '[演示] 转载示例：保持代码可读性', 'v2-demo-backend', 2, 2, 1, 1, 0, 1, 26, 0, NULL),
  (14, '[演示] 翻译示例：关注真实的用户反馈', 'v2-demo-frontend', 1, 3, 1, 1, 0, 1, 28, 0, NULL),
  (15, '[演示] 没有分类也可以好好记录', NULL, 2, 1, 1, 1, 0, 1, 30, 0, NULL),
  (16, '[演示] 隐藏分类中的公开文章', 'v2-demo-hidden', 1, 1, 1, 1, 0, 1, 32, 0, NULL),
  (17, '[演示] 还没有完成的后端草稿', 'v2-demo-frontend', 2, 1, 0, 1, 0, 1, NULL, 0, NULL),
  (18, '[演示] 等待配图的旅行草稿', 'v2-demo-life', 1, 1, 0, 1, 0, 1, NULL, 0, NULL),
  (19, '[演示] 暂时下架的开发笔记', 'v2-demo-backend', 2, 1, 2, 1, 0, 1, 38, 0, NULL),
  (20, '[演示] 需要重新核对的资料', 'v2-demo-frontend', 1, 1, 2, 1, 0, 1, 40, 0, NULL),
  (21, '[演示] 只给自己看的备忘录', 'v2-demo-life', 2, 1, 1, 2, 0, 1, 42, 0, NULL),
  (22, '[演示] 项目计划中的私人想法', 'v2-demo-backend', 1, 1, 1, 2, 0, 1, 44, 0, NULL),
  (23, '[演示] 回收站中的历史文章', 'v2-demo-frontend', 2, 1, 1, 1, 0, 1, 46, 1, NULL),
  (24, '[演示] 明天才可公开的文章', 'v2-demo-life', 1, 1, 1, 1, 0, 1, -1, 0, NULL);

INSERT INTO blog_article (author_id, category_id, title, slug, summary, article_type, original_url,
  status, visibility, is_top, allow_comment, view_count, publish_time, deleted_at, create_time, update_time)
SELECT u.user_id, c.category_id, s.title, CONCAT('v2-demo-article-', LPAD(s.seed_key, 2, '0')),
  CONCAT('[V2 演示] ', s.title, '。用于检查文章卡片、搜索、归档、状态筛选和详情排版。'),
  s.article_type, IF(s.article_type = 1, NULL, 'https://example.com/v2-demo/source'),
  s.status, s.visibility, s.is_top, s.allow_comment, 80 + s.seed_key * 37,
  IF(s.publish_days IS NULL, NULL, DATE_SUB(@dl_demo_anchor, INTERVAL s.publish_days DAY)),
  IF(s.in_trash = 1, DATE_SUB(@dl_demo_anchor, INTERVAL 1 DAY), NULL),
  DATE_SUB(@dl_demo_anchor, INTERVAL 90 DAY), DATE_SUB(@dl_demo_anchor, INTERVAL s.seed_key HOUR)
FROM dl_demo_article_seed s JOIN dl_demo_user_seed u ON u.seed_key = s.author_seed
LEFT JOIN blog_category c ON c.slug = s.category_slug
WHERE NOT EXISTS (SELECT 1 FROM blog_article a WHERE a.slug = CONCAT('v2-demo-article-', LPAD(s.seed_key, 2, '0')));
UPDATE dl_demo_article_seed s JOIN blog_article a
  ON a.slug = CONCAT('v2-demo-article-', LPAD(s.seed_key, 2, '0')) SET s.article_id = a.article_id;
INSERT INTO blog_article_content (article_id, markdown_content, html_content)
SELECT s.article_id, CONCAT('# ', s.title, CHAR(10), CHAR(10),
  '**这是一篇虚构的 V2 演示文章。**', CHAR(10), CHAR(10),
  '## 为什么记录', CHAR(10), '把复杂的问题拆成清楚的步骤，让下一次回顾更轻松。', CHAR(10), CHAR(10),
  '## 实践清单', CHAR(10), '- 先确认输入和预期结果', CHAR(10), '- 再检查失败路径和边界', CHAR(10),
  '- 最后保留必要的验证记录', CHAR(10), CHAR(10),
  '```java', CHAR(10), 'String message = "Hello, DriftingLeaves V2";', CHAR(10), '```', CHAR(10), CHAR(10),
  '| 检查项 | 结果 |', CHAR(10), '| --- | --- |', CHAR(10), '| 中文与 Emoji | 🍂 正常展示 |', CHAR(10),
  '| 长段落 | 自动换行 |', CHAR(10), CHAR(10), '> 本文仅用于演示，请替换为自己的内容。'), NULL
FROM dl_demo_article_seed s
WHERE NOT EXISTS (SELECT 1 FROM blog_article_content c WHERE c.article_id = s.article_id);
INSERT INTO blog_article_tag (article_id, tag_id)
SELECT a.article_id, t.tag_id FROM dl_demo_article_seed a JOIN blog_tag t
  ON t.slug = CASE MOD(a.seed_key, 3) WHEN 0 THEN 'v2-demo-daily' WHEN 1 THEN 'v2-demo-java' ELSE 'v2-demo-vue' END
     OR t.slug = 'v2-demo-engineering'
WHERE NOT EXISTS (SELECT 1 FROM blog_article_tag atg WHERE atg.article_id = a.article_id AND atg.tag_id = t.tag_id);
INSERT INTO blog_article_like (article_id, user_id, create_time)
SELECT a.article_id, u.user_id, DATE_SUB(@dl_demo_anchor, INTERVAL a.seed_key DAY)
FROM dl_demo_article_seed a CROSS JOIN dl_demo_user_seed u
WHERE a.seed_key <= 12 AND u.seed_key BETWEEN 3 AND 6
  AND NOT EXISTS (SELECT 1 FROM blog_article_like l WHERE l.article_id = a.article_id AND l.user_id = u.user_id);

-- 四、36 条评论和 32 条留言，包含根节点、两层回复、审核状态、私密继承和历史空标识。
CREATE TEMPORARY TABLE dl_demo_discussion_seed (
  kind CHAR(1), seed_key INT, parent_key INT, depth INT, user_seed INT, article_seed INT,
  content_format TINYINT, is_secret TINYINT, notify_reply TINYINT, status TINYINT,
  content TEXT, location VARCHAR(192), agent_os VARCHAR(64), agent_browser VARCHAR(64),
  created_days INT, edited_days INT, PRIMARY KEY (kind, seed_key)
);

INSERT INTO dl_demo_discussion_seed (kind, seed_key, parent_key, depth, user_seed, article_seed, content_format, is_secret, notify_reply, status, content, location, agent_os, agent_browser, created_days, edited_days) VALUES
  ('C', 1, NULL, 0, 4, 1, 0, 0, 0, 1, '[V2 演示 C01] 写得很清楚，特别是失败路径的处理，感谢分享。', '湖南省 长沙市', 'Windows', 'Edge', 2, NULL),
  ('C', 2, NULL, 0, 5, 2, 0, 0, 0, 1, '[V2 演示 C02] 读完之后把自己的实现又整理了一遍，确实更容易维护了。', '浙江省 杭州市', 'macOS', 'Safari', 3, NULL),
  ('C', 3, NULL, 0, 6, 3, 0, 0, 0, 0, '[V2 演示 C03] 这是一条等待审核的评论，用来检查审核筛选。', '广东省 深圳市', 'Android', 'Chrome', 4, NULL),
  ('C', 4, NULL, 0, 7, 4, 0, 0, 0, 2, '[V2 演示 C04] 这条评论已被驳回，用于检查后台详情和状态。', '江苏省 南京市', 'iOS', 'Safari', 5, NULL),
  ('C', 5, NULL, 0, 8, 5, 0, 0, 0, 3, '[V2 演示 C05] 该评论已删除', '本地', 'Linux', 'Firefox', 6, NULL),
  ('C', 6, NULL, 0, 3, 6, 0, 0, 0, 1, '[V2 演示 C06] 中文、标点与 Emoji 都能正常显示：你好，世界！🍂', NULL, NULL, NULL, 7, NULL),
  ('C', 7, NULL, 0, 4, 1, 0, 0, 0, 1, '[V2 演示 C07] 长评论换行示例：
第一段记录想法。
希望每一个后来阅读代码的人，都能顺着步骤理解这里的设计。希望每一个后来阅读代码的人，都能顺着步骤理解这里的设计。希望每一个后来阅读代码的人，都能顺着步骤理解这里的设计。希望每一个后来阅读代码的人，都能顺着步骤理解这里的设计。希望每一个后来阅读代码的人，都能顺着步骤理解这里的设计。希望每一个后来阅读代码的人，都能顺着步骤理解这里的设计。希望每一个后来阅读代码的人，都能顺着步骤理解这里的设计。希望每一个后来阅读代码的人，都能顺着步骤理解这里的设计。希望每一个后来阅读代码的人，都能顺着步骤理解这里的设计。希望每一个后来阅读代码的人，都能顺着步骤理解这里的设计。希望每一个后来阅读代码的人，都能顺着步骤理解这里的设计。希望每一个后来阅读代码的人，都能顺着步骤理解这里的设计。', '湖南省 长沙市', 'Windows', 'Edge', 8, NULL),
  ('C', 8, NULL, 0, 5, 2, 0, 0, 0, 1, '[V2 演示 C08] 搜索字符示例：O''Reilly、100%、下划线 _，以及 <b>按文字展示</b>。', '浙江省 杭州市', 'macOS', 'Safari', 9, NULL),
  ('C', 9, NULL, 0, 6, 3, 0, 0, 0, 0, '[V2 演示 C09] 这是一条等待审核的评论，用来检查审核筛选。', '广东省 深圳市', 'Android', 'Chrome', 10, NULL),
  ('C', 10, NULL, 0, 7, 4, 0, 0, 0, 2, '[V2 演示 C10] 这条评论已被驳回，用于检查后台详情和状态。', '江苏省 南京市', 'iOS', 'Safari', 11, NULL),
  ('C', 11, NULL, 0, 8, 5, 0, 0, 0, 3, '[V2 演示 C11] 该评论已删除', '本地', 'Linux', 'Firefox', 12, NULL),
  ('C', 12, NULL, 0, 3, 6, 0, 0, 0, 1, '[V2 演示 C12] 中文、标点与 Emoji 都能正常显示：你好，世界！🍂', NULL, NULL, NULL, 13, NULL),
  ('C', 13, NULL, 0, 4, 1, 0, 0, 0, 1, '[V2 演示 C13] 写得很清楚，特别是失败路径的处理，感谢分享。', '湖南省 长沙市', 'Windows', 'Edge', 14, NULL),
  ('C', 14, NULL, 0, 5, 2, 0, 0, 0, 1, '[V2 演示 C14] 读完之后把自己的实现又整理了一遍，确实更容易维护了。', '浙江省 杭州市', 'macOS', 'Safari', 15, NULL),
  ('C', 15, NULL, 0, 6, 3, 0, 0, 0, 0, '[V2 演示 C15] 这是一条等待审核的评论，用来检查审核筛选。', '广东省 深圳市', 'Android', 'Chrome', 16, NULL),
  ('C', 16, NULL, 0, 7, 4, 0, 0, 0, 2, '[V2 演示 C16] 这条评论已被驳回，用于检查后台详情和状态。', '江苏省 南京市', 'iOS', 'Safari', 17, NULL),
  ('C', 17, NULL, 0, 8, 5, 0, 0, 0, 3, '[V2 演示 C17] 该评论已删除', '本地', 'Linux', 'Firefox', 18, NULL),
  ('C', 18, NULL, 0, 3, 6, 0, 0, 0, 1, '[V2 演示 C18] 中文、标点与 Emoji 都能正常显示：你好，世界！🍂', NULL, NULL, NULL, 19, NULL),
  ('C', 19, NULL, 0, 4, 1, 0, 0, 0, 1, '[V2 演示 C19] 写得很清楚，特别是失败路径的处理，感谢分享。', '湖南省 长沙市', 'Windows', 'Edge', 20, NULL),
  ('C', 20, NULL, 0, 5, 2, 0, 0, 0, 1, '[V2 演示 C20] 读完之后把自己的实现又整理了一遍，确实更容易维护了。', '浙江省 杭州市', 'macOS', 'Safari', 1, NULL),
  ('C', 21, NULL, 0, 6, 3, 0, 0, 0, 0, '[V2 演示 C21] 这是一条等待审核的评论，用来检查审核筛选。', '广东省 深圳市', 'Android', 'Chrome', 2, NULL),
  ('C', 22, NULL, 0, 7, 4, 0, 0, 0, 2, '[V2 演示 C22] 这条评论已被驳回，用于检查后台详情和状态。', '江苏省 南京市', 'iOS', 'Safari', 3, NULL),
  ('C', 23, NULL, 0, 8, 5, 0, 0, 0, 3, '[V2 演示 C23] 该评论已删除', '本地', 'Linux', 'Firefox', 4, NULL),
  ('C', 24, NULL, 0, 3, 6, 0, 0, 0, 1, '[V2 演示 C24] 中文、标点与 Emoji 都能正常显示：你好，世界！🍂', NULL, NULL, NULL, 5, NULL),
  ('C', 25, 1, 1, 5, 1, 0, 0, 0, 1, '[V2 演示 C25] 回复上一条评论：补充一个实际使用中的细节，欢迎继续交流。', '浙江省 杭州市', 'macOS', 'Safari', 0, NULL),
  ('C', 26, 25, 2, 6, 1, 0, 0, 0, 1, '[V2 演示 C26] 回复上一条评论：补充一个实际使用中的细节，欢迎继续交流。', '广东省 深圳市', 'Android', 'Chrome', 0, NULL),
  ('C', 27, 3, 1, 7, 3, 0, 0, 0, 1, '[V2 演示 C27] 回复上一条评论：补充一个实际使用中的细节，欢迎继续交流。', '江苏省 南京市', 'iOS', 'Safari', 0, NULL),
  ('C', 28, 4, 1, 1, 4, 0, 0, 0, 1, '[V2 演示 C28] 回复上一条评论：补充一个实际使用中的细节，欢迎继续交流。', '本地', 'Linux', 'Firefox', 0, NULL),
  ('C', 29, 5, 1, 2, 5, 0, 0, 0, 1, '[V2 演示 C29] 回复上一条评论：补充一个实际使用中的细节，欢迎继续交流。', NULL, NULL, NULL, 0, NULL),
  ('C', 30, 6, 1, 3, 6, 0, 0, 0, 1, '[V2 演示 C30] 回复上一条评论：补充一个实际使用中的细节，欢迎继续交流。', '湖南省 长沙市', 'Windows', 'Edge', 0, NULL),
  ('C', 31, 1, 1, 4, 1, 0, 0, 0, 1, '[V2 演示 C31] 回复上一条评论：补充一个实际使用中的细节，欢迎继续交流。', '浙江省 杭州市', 'macOS', 'Safari', 0, NULL),
  ('C', 32, 2, 1, 5, 2, 0, 0, 0, 1, '[V2 演示 C32] 回复上一条评论：补充一个实际使用中的细节，欢迎继续交流。', '广东省 深圳市', 'Android', 'Chrome', 0, NULL),
  ('C', 33, 3, 1, 6, 3, 0, 0, 0, 1, '[V2 演示 C33] 回复上一条评论：补充一个实际使用中的细节，欢迎继续交流。', '江苏省 南京市', 'iOS', 'Safari', 0, NULL),
  ('C', 34, 4, 1, 7, 4, 0, 0, 0, 1, '[V2 演示 C34] 回复上一条评论：补充一个实际使用中的细节，欢迎继续交流。', '本地', 'Linux', 'Firefox', 0, NULL),
  ('C', 35, 5, 1, 1, 5, 0, 0, 0, 1, '[V2 演示 C35] 回复上一条评论：补充一个实际使用中的细节，欢迎继续交流。', NULL, NULL, NULL, 0, NULL),
  ('C', 36, 6, 1, 2, 6, 0, 0, 0, 1, '[V2 演示 C36] 回复上一条评论：补充一个实际使用中的细节，欢迎继续交流。', '湖南省 长沙市', 'Windows', 'Edge', 0, NULL),
  ('M', 1, NULL, 0, 4, NULL, 1, 0, 1, 1, '[V2 演示 M01] **留言的重点**

感谢持续记录，愿每一天都有新的收获。

- 记录日常
- 分享技术

> 一条虚构的 Markdown 留言。', '湖南省 长沙市', 'Windows', 'Edge', 2, NULL),
  ('M', 2, NULL, 0, 5, NULL, 0, 1, 0, 1, '[V2 演示 M02] 欢迎来小站坐坐。
这一条用于检查纯文本换行和留言详情。', '浙江省 杭州市', 'macOS', 'Safari', 3, NULL),
  ('M', 3, NULL, 0, 6, NULL, 1, 0, 1, 0, '[V2 演示 M03] **留言的重点**

感谢持续记录，愿每一天都有新的收获。

- 记录日常
- 分享技术

> 一条虚构的 Markdown 留言。', '广东省 深圳市', 'Android', 'Chrome', 4, NULL),
  ('M', 4, NULL, 0, 7, NULL, 0, 0, 0, 2, '[V2 演示 M04] 欢迎来小站坐坐。
这一条用于检查纯文本换行和留言详情。', '江苏省 南京市', 'iOS', 'Safari', 5, NULL),
  ('M', 5, NULL, 0, 8, NULL, 1, 0, 1, 3, '[V2 演示 M05] 该留言已删除', '本地', 'Linux', 'Firefox', 6, NULL),
  ('M', 6, NULL, 0, 3, NULL, 0, 0, 0, 1, '[V2 演示 M06] 欢迎来小站坐坐。
这一条用于检查纯文本换行和留言详情。', NULL, NULL, NULL, 7, 1),
  ('M', 7, NULL, 0, 4, NULL, 1, 1, 1, 1, '[V2 演示 M07] **留言的重点**

感谢持续记录，愿每一天都有新的收获。

- 记录日常
- 分享技术

> 一条虚构的 Markdown 留言。', '湖南省 长沙市', 'Windows', 'Edge', 8, NULL),
  ('M', 8, NULL, 0, 5, NULL, 0, 0, 0, 0, '[V2 演示 M08] 欢迎来小站坐坐。
这一条用于检查纯文本换行和留言详情。', '浙江省 杭州市', 'macOS', 'Safari', 9, NULL),
  ('M', 9, NULL, 0, 6, NULL, 1, 0, 1, 2, '[V2 演示 M09] 长留言示例：
把普通的日子写下来，回头看时就有了新的意义。把普通的日子写下来，回头看时就有了新的意义。把普通的日子写下来，回头看时就有了新的意义。把普通的日子写下来，回头看时就有了新的意义。把普通的日子写下来，回头看时就有了新的意义。把普通的日子写下来，回头看时就有了新的意义。把普通的日子写下来，回头看时就有了新的意义。把普通的日子写下来，回头看时就有了新的意义。把普通的日子写下来，回头看时就有了新的意义。把普通的日子写下来，回头看时就有了新的意义。把普通的日子写下来，回头看时就有了新的意义。把普通的日子写下来，回头看时就有了新的意义。把普通的日子写下来，回头看时就有了新的意义。把普通的日子写下来，回头看时就有了新的意义。把普通的日子写下来，回头看时就有了新的意义。把普通的日子写下来，回头看时就有了新的意义。把普通的日子写下来，回头看时就有了新的意义。把普通的日子写下来，回头看时就有了新的意义。把普通的日子写下来，回头看时就有了新的意义。把普通的日子写下来，回头看时就有了新的意义。', '广东省 深圳市', 'Android', 'Chrome', 10, NULL),
  ('M', 10, NULL, 0, 7, NULL, 0, 0, 0, 3, '[V2 演示 M10] 欢迎来小站坐坐。
这一条用于检查纯文本换行和留言详情。', '江苏省 南京市', 'iOS', 'Safari', 11, NULL),
  ('M', 11, NULL, 0, 8, NULL, 1, 0, 1, 1, '[V2 演示 M11] **留言的重点**

感谢持续记录，愿每一天都有新的收获。

- 记录日常
- 分享技术

> 一条虚构的 Markdown 留言。', '本地', 'Linux', 'Firefox', 12, 1),
  ('M', 12, NULL, 0, 3, NULL, 0, 1, 0, 1, '[V2 演示 M12] 欢迎来小站坐坐。
这一条用于检查纯文本换行和留言详情。', NULL, NULL, NULL, 13, NULL),
  ('M', 13, NULL, 0, 4, NULL, 1, 0, 1, 0, '[V2 演示 M13] **留言的重点**

感谢持续记录，愿每一天都有新的收获。

- 记录日常
- 分享技术

> 一条虚构的 Markdown 留言。', '湖南省 长沙市', 'Windows', 'Edge', 14, NULL),
  ('M', 14, NULL, 0, 5, NULL, 0, 0, 0, 2, '[V2 演示 M14] 欢迎来小站坐坐。
这一条用于检查纯文本换行和留言详情。', '浙江省 杭州市', 'macOS', 'Safari', 15, NULL),
  ('M', 15, NULL, 0, 6, NULL, 1, 0, 1, 3, '[V2 演示 M15] **留言的重点**

感谢持续记录，愿每一天都有新的收获。

- 记录日常
- 分享技术

> 一条虚构的 Markdown 留言。', '广东省 深圳市', 'Android', 'Chrome', 16, NULL),
  ('M', 16, NULL, 0, 7, NULL, 0, 0, 0, 1, '[V2 演示 M16] 欢迎来小站坐坐。
这一条用于检查纯文本换行和留言详情。', '江苏省 南京市', 'iOS', 'Safari', 17, NULL),
  ('M', 17, NULL, 0, 8, NULL, 1, 1, 1, 1, '[V2 演示 M17] **留言的重点**

感谢持续记录，愿每一天都有新的收获。

- 记录日常
- 分享技术

> 一条虚构的 Markdown 留言。', '本地', 'Linux', 'Firefox', 18, NULL),
  ('M', 18, NULL, 0, 3, NULL, 0, 0, 0, 0, '[V2 演示 M18] 欢迎来小站坐坐。
这一条用于检查纯文本换行和留言详情。', NULL, NULL, NULL, 1, NULL),
  ('M', 19, NULL, 0, 4, NULL, 1, 0, 1, 2, '[V2 演示 M19] **留言的重点**

感谢持续记录，愿每一天都有新的收获。

- 记录日常
- 分享技术

> 一条虚构的 Markdown 留言。', '湖南省 长沙市', 'Windows', 'Edge', 2, NULL),
  ('M', 20, NULL, 0, 5, NULL, 0, 0, 0, 3, '[V2 演示 M20] 欢迎来小站坐坐。
这一条用于检查纯文本换行和留言详情。', '浙江省 杭州市', 'macOS', 'Safari', 3, NULL),
  ('M', 21, 1, 1, 1, NULL, 1, 0, 1, 1, '[V2 演示 M21] 收到你的留言了，愿这段交流带来一点新的想法。', '江苏省 南京市', 'iOS', 'Safari', 0, NULL),
  ('M', 22, 21, 2, 2, NULL, 0, 0, 0, 1, '[V2 演示 M22] 收到你的留言了，愿这段交流带来一点新的想法。', '本地', 'Linux', 'Firefox', 0, NULL),
  ('M', 23, 3, 1, 3, NULL, 1, 0, 1, 1, '[V2 演示 M23] 收到你的留言了，愿这段交流带来一点新的想法。', NULL, NULL, NULL, 0, NULL),
  ('M', 24, 4, 1, 4, NULL, 0, 0, 0, 1, '[V2 演示 M24] 收到你的留言了，愿这段交流带来一点新的想法。', '湖南省 长沙市', 'Windows', 'Edge', 0, NULL),
  ('M', 25, 5, 1, 5, NULL, 1, 0, 1, 1, '[V2 演示 M25] 收到你的留言了，愿这段交流带来一点新的想法。', '浙江省 杭州市', 'macOS', 'Safari', 0, NULL),
  ('M', 26, 6, 1, 6, NULL, 0, 0, 0, 1, '[V2 演示 M26] 收到你的留言了，愿这段交流带来一点新的想法。', '广东省 深圳市', 'Android', 'Chrome', 0, NULL),
  ('M', 27, 1, 1, 7, NULL, 1, 0, 1, 1, '[V2 演示 M27] 收到你的留言了，愿这段交流带来一点新的想法。', '江苏省 南京市', 'iOS', 'Safari', 0, NULL),
  ('M', 28, 2, 1, 1, NULL, 0, 1, 0, 1, '[V2 演示 M28] 收到你的留言了，愿这段交流带来一点新的想法。', '本地', 'Linux', 'Firefox', 0, NULL),
  ('M', 29, 3, 1, 2, NULL, 1, 0, 1, 1, '[V2 演示 M29] 收到你的留言了，愿这段交流带来一点新的想法。', NULL, NULL, NULL, 0, NULL),
  ('M', 30, 4, 1, 3, NULL, 0, 0, 0, 1, '[V2 演示 M30] 收到你的留言了，愿这段交流带来一点新的想法。', '湖南省 长沙市', 'Windows', 'Edge', 0, NULL),
  ('M', 31, 5, 1, 4, NULL, 1, 0, 1, 1, '[V2 演示 M31] 收到你的留言了，愿这段交流带来一点新的想法。', '浙江省 杭州市', 'macOS', 'Safari', 0, NULL),
  ('M', 32, 6, 1, 5, NULL, 0, 0, 0, 1, '[V2 演示 M32] 收到你的留言了，愿这段交流带来一点新的想法。', '广东省 深圳市', 'Android', 'Chrome', 0, NULL);

CREATE TEMPORARY TABLE dl_demo_comment_ids (seed_key INT PRIMARY KEY, comment_id BIGINT);
CREATE TEMPORARY TABLE dl_demo_message_ids (seed_key INT PRIMARY KEY, message_id BIGINT);

-- 先插入深度 0 的评论，再保存实际自增 ID，子节点不会占用固定业务主键。
INSERT INTO blog_comment (article_id, user_id, parent_id, content, location, user_agent_os, user_agent_browser, status, create_time, update_time)
SELECT a.article_id, u.user_id, p.comment_id, s.content, s.location, s.agent_os, s.agent_browser,
  s.status, DATE_SUB(@dl_demo_anchor, INTERVAL s.created_days DAY), DATE_SUB(@dl_demo_anchor, INTERVAL s.created_days DAY)
FROM dl_demo_discussion_seed s JOIN dl_demo_user_seed u ON u.seed_key = s.user_seed
JOIN dl_demo_article_seed a ON a.seed_key = s.article_seed
LEFT JOIN dl_demo_comment_ids p ON p.seed_key = s.parent_key
WHERE s.kind = 'C' AND s.depth = 0 AND (s.parent_key IS NULL OR p.comment_id IS NOT NULL)
  AND NOT EXISTS (SELECT 1 FROM blog_comment c WHERE c.article_id = a.article_id AND c.user_id = u.user_id AND c.content = s.content);
INSERT INTO dl_demo_comment_ids (seed_key, comment_id)
SELECT s.seed_key, c.comment_id FROM dl_demo_discussion_seed s JOIN dl_demo_user_seed u ON u.seed_key = s.user_seed
JOIN dl_demo_article_seed a ON a.seed_key = s.article_seed
JOIN blog_comment c ON c.article_id = a.article_id AND c.user_id = u.user_id AND c.content = s.content
WHERE s.kind = 'C' AND s.depth = 0;

-- 私密回复使用与父链相同的标记，展示时仍由应用检查完整父链。
INSERT INTO blog_message (user_id, parent_id, content, location, user_agent_os, user_agent_browser,
  content_format, is_secret, notify_reply, status, edited_at, create_time, update_time)
SELECT u.user_id, p.message_id, s.content, s.location, s.agent_os, s.agent_browser,
  s.content_format, s.is_secret, s.notify_reply, s.status,
  IF(s.edited_days IS NULL, NULL, DATE_SUB(@dl_demo_anchor, INTERVAL s.edited_days DAY)),
  DATE_SUB(@dl_demo_anchor, INTERVAL s.created_days DAY),
  DATE_SUB(@dl_demo_anchor, INTERVAL COALESCE(s.edited_days, s.created_days) DAY)
FROM dl_demo_discussion_seed s JOIN dl_demo_user_seed u ON u.seed_key = s.user_seed
LEFT JOIN dl_demo_message_ids p ON p.seed_key = s.parent_key
WHERE s.kind = 'M' AND s.depth = 0 AND (s.parent_key IS NULL OR p.message_id IS NOT NULL)
  AND NOT EXISTS (SELECT 1 FROM blog_message m WHERE m.user_id = u.user_id AND m.content = s.content);
INSERT INTO dl_demo_message_ids (seed_key, message_id)
SELECT s.seed_key, m.message_id FROM dl_demo_discussion_seed s JOIN dl_demo_user_seed u ON u.seed_key = s.user_seed
JOIN blog_message m ON m.user_id = u.user_id AND m.content = s.content
WHERE s.kind = 'M' AND s.depth = 0;

-- 先插入深度 1 的评论，再保存实际自增 ID，子节点不会占用固定业务主键。
INSERT INTO blog_comment (article_id, user_id, parent_id, content, location, user_agent_os, user_agent_browser, status, create_time, update_time)
SELECT a.article_id, u.user_id, p.comment_id, s.content, s.location, s.agent_os, s.agent_browser,
  s.status, DATE_SUB(@dl_demo_anchor, INTERVAL s.created_days DAY), DATE_SUB(@dl_demo_anchor, INTERVAL s.created_days DAY)
FROM dl_demo_discussion_seed s JOIN dl_demo_user_seed u ON u.seed_key = s.user_seed
JOIN dl_demo_article_seed a ON a.seed_key = s.article_seed
LEFT JOIN dl_demo_comment_ids p ON p.seed_key = s.parent_key
WHERE s.kind = 'C' AND s.depth = 1 AND (s.parent_key IS NULL OR p.comment_id IS NOT NULL)
  AND NOT EXISTS (SELECT 1 FROM blog_comment c WHERE c.article_id = a.article_id AND c.user_id = u.user_id AND c.content = s.content);
INSERT INTO dl_demo_comment_ids (seed_key, comment_id)
SELECT s.seed_key, c.comment_id FROM dl_demo_discussion_seed s JOIN dl_demo_user_seed u ON u.seed_key = s.user_seed
JOIN dl_demo_article_seed a ON a.seed_key = s.article_seed
JOIN blog_comment c ON c.article_id = a.article_id AND c.user_id = u.user_id AND c.content = s.content
WHERE s.kind = 'C' AND s.depth = 1;

-- 私密回复使用与父链相同的标记，展示时仍由应用检查完整父链。
INSERT INTO blog_message (user_id, parent_id, content, location, user_agent_os, user_agent_browser,
  content_format, is_secret, notify_reply, status, edited_at, create_time, update_time)
SELECT u.user_id, p.message_id, s.content, s.location, s.agent_os, s.agent_browser,
  s.content_format, s.is_secret, s.notify_reply, s.status,
  IF(s.edited_days IS NULL, NULL, DATE_SUB(@dl_demo_anchor, INTERVAL s.edited_days DAY)),
  DATE_SUB(@dl_demo_anchor, INTERVAL s.created_days DAY),
  DATE_SUB(@dl_demo_anchor, INTERVAL COALESCE(s.edited_days, s.created_days) DAY)
FROM dl_demo_discussion_seed s JOIN dl_demo_user_seed u ON u.seed_key = s.user_seed
LEFT JOIN dl_demo_message_ids p ON p.seed_key = s.parent_key
WHERE s.kind = 'M' AND s.depth = 1 AND (s.parent_key IS NULL OR p.message_id IS NOT NULL)
  AND NOT EXISTS (SELECT 1 FROM blog_message m WHERE m.user_id = u.user_id AND m.content = s.content);
INSERT INTO dl_demo_message_ids (seed_key, message_id)
SELECT s.seed_key, m.message_id FROM dl_demo_discussion_seed s JOIN dl_demo_user_seed u ON u.seed_key = s.user_seed
JOIN blog_message m ON m.user_id = u.user_id AND m.content = s.content
WHERE s.kind = 'M' AND s.depth = 1;

-- 先插入深度 2 的评论，再保存实际自增 ID，子节点不会占用固定业务主键。
INSERT INTO blog_comment (article_id, user_id, parent_id, content, location, user_agent_os, user_agent_browser, status, create_time, update_time)
SELECT a.article_id, u.user_id, p.comment_id, s.content, s.location, s.agent_os, s.agent_browser,
  s.status, DATE_SUB(@dl_demo_anchor, INTERVAL s.created_days DAY), DATE_SUB(@dl_demo_anchor, INTERVAL s.created_days DAY)
FROM dl_demo_discussion_seed s JOIN dl_demo_user_seed u ON u.seed_key = s.user_seed
JOIN dl_demo_article_seed a ON a.seed_key = s.article_seed
LEFT JOIN dl_demo_comment_ids p ON p.seed_key = s.parent_key
WHERE s.kind = 'C' AND s.depth = 2 AND (s.parent_key IS NULL OR p.comment_id IS NOT NULL)
  AND NOT EXISTS (SELECT 1 FROM blog_comment c WHERE c.article_id = a.article_id AND c.user_id = u.user_id AND c.content = s.content);
INSERT INTO dl_demo_comment_ids (seed_key, comment_id)
SELECT s.seed_key, c.comment_id FROM dl_demo_discussion_seed s JOIN dl_demo_user_seed u ON u.seed_key = s.user_seed
JOIN dl_demo_article_seed a ON a.seed_key = s.article_seed
JOIN blog_comment c ON c.article_id = a.article_id AND c.user_id = u.user_id AND c.content = s.content
WHERE s.kind = 'C' AND s.depth = 2;

-- 私密回复使用与父链相同的标记，展示时仍由应用检查完整父链。
INSERT INTO blog_message (user_id, parent_id, content, location, user_agent_os, user_agent_browser,
  content_format, is_secret, notify_reply, status, edited_at, create_time, update_time)
SELECT u.user_id, p.message_id, s.content, s.location, s.agent_os, s.agent_browser,
  s.content_format, s.is_secret, s.notify_reply, s.status,
  IF(s.edited_days IS NULL, NULL, DATE_SUB(@dl_demo_anchor, INTERVAL s.edited_days DAY)),
  DATE_SUB(@dl_demo_anchor, INTERVAL s.created_days DAY),
  DATE_SUB(@dl_demo_anchor, INTERVAL COALESCE(s.edited_days, s.created_days) DAY)
FROM dl_demo_discussion_seed s JOIN dl_demo_user_seed u ON u.seed_key = s.user_seed
LEFT JOIN dl_demo_message_ids p ON p.seed_key = s.parent_key
WHERE s.kind = 'M' AND s.depth = 2 AND (s.parent_key IS NULL OR p.message_id IS NOT NULL)
  AND NOT EXISTS (SELECT 1 FROM blog_message m WHERE m.user_id = u.user_id AND m.content = s.content);
INSERT INTO dl_demo_message_ids (seed_key, message_id)
SELECT s.seed_key, m.message_id FROM dl_demo_discussion_seed s JOIN dl_demo_user_seed u ON u.seed_key = s.user_seed
JOIN blog_message m ON m.user_id = u.user_id AND m.content = s.content
WHERE s.kind = 'M' AND s.depth = 2;

-- 五、友链和音乐。URL 使用保留的测试域名；音乐默认隐藏，替换为真实音频后再展示。
INSERT INTO blog_friend_link (link_name, url, description, sort_order, status)
SELECT CONCAT('[演示] ', s.name), CONCAT('https://', s.slug, '.example.test/'),
  '[V2 演示] 虚构友链，用于检查审核、排序和站点卡片。', 900 + s.n, s.status
FROM (
  SELECT 1 AS n, '山间书屋' AS name, 'mountain' AS slug, 1 AS status
  UNION ALL SELECT 2, '代码小站', 'code', 1
  UNION ALL SELECT 3, '慢生活笔记', 'slow-life', 1
  UNION ALL SELECT 4, '等待审核的朋友', 'pending', 0
  UNION ALL SELECT 5, '暂时下架的小站', 'offline', 2
  UNION ALL SELECT 6, '远方来信', 'letters', 0
) s
WHERE NOT EXISTS (SELECT 1 FROM blog_friend_link f WHERE f.url = CONCAT('https://', s.slug, '.example.test/'));
INSERT INTO blog_music (title, artist, duration_seconds, audio_url, sort_order, status)
SELECT CONCAT('[演示] ', s.title), '虚构音乐人', s.seconds,
  CONCAT('https://media.example.test/v2-demo/track-', s.n, '.mp3'), 900 + s.n, 0
FROM (
  SELECT 1 AS n, '林间清晨' AS title, 186 AS seconds
  UNION ALL SELECT 2, '雨后的街道', 213
  UNION ALL SELECT 3, '给晚风的信', 204
) s
WHERE NOT EXISTS (SELECT 1 FROM blog_music m WHERE m.title = CONCAT('[演示] ', s.title));

-- 待验证和已退订样例不创建激活订阅；真实确认、邮件及 Redis 票据继续走应用流程。
INSERT INTO blog_rss_subscription (user_id, email, status, verified_at, subscribe_time, unsubscribe_time, create_time, update_time)
SELECT u.user_id, CONCAT(u.username, '@example.test'), IF(u.seed_key <= 6, 0, 2),
  IF(u.seed_key <= 6, NULL, DATE_SUB(@dl_demo_anchor, INTERVAL 20 DAY)),
  IF(u.seed_key <= 6, NULL, DATE_SUB(@dl_demo_anchor, INTERVAL 20 DAY)),
  IF(u.seed_key <= 6, NULL, DATE_SUB(@dl_demo_anchor, INTERVAL 2 DAY)),
  DATE_SUB(@dl_demo_anchor, INTERVAL 25 DAY), DATE_SUB(@dl_demo_anchor, INTERVAL 2 DAY)
FROM dl_demo_user_seed u WHERE u.seed_key >= 3
  AND NOT EXISTS (SELECT 1 FROM blog_rss_subscription r WHERE r.user_id = u.user_id OR r.email = CONCAT(u.username, '@example.test'));

-- 六、18 位统计访客和最近 30 天的 240 条浏览记录。IP 为文档专用地址，地域是展示用虚构值。
CREATE TEMPORARY TABLE dl_demo_visitor_seed (
  seed_key INT PRIMARY KEY, fingerprint VARCHAR(150), ip VARCHAR(45), agent VARCHAR(1024),
  country VARCHAR(64), province VARCHAR(64), city VARCHAR(64), is_blocked TINYINT, block_days INT
);

INSERT INTO dl_demo_visitor_seed (seed_key, fingerprint, ip, agent, country, province, city, is_blocked, block_days) VALUES
  (1, 'v2-demo-visitor-01', '203.0.113.1', 'Mozilla/5.0 Windows NT 10.0 Chrome/130.0 Edg/130.0', '中国', '湖南省', '长沙市', 0, NULL),
  (2, 'v2-demo-visitor-02', '203.0.113.2', 'Mozilla/5.0 Android 14 Chrome/130.0', '中国', '广东省', '深圳市', 0, NULL),
  (3, 'v2-demo-visitor-03', '203.0.113.3', 'Mozilla/5.0 iPhone Safari/604.1', '中国', '浙江省', '杭州市', 0, NULL),
  (4, 'v2-demo-visitor-04', '2001:db8::4', 'Mozilla/5.0 Windows NT 10.0 Chrome/130.0 Edg/130.0', '中国', '江苏省', '南京市', 0, NULL),
  (5, 'v2-demo-visitor-05', '203.0.113.5', 'Mozilla/5.0 Android 14 Chrome/130.0', NULL, NULL, NULL, 0, NULL),
  (6, 'v2-demo-visitor-06', '203.0.113.6', 'Mozilla/5.0 iPhone Safari/604.1', '中国', '湖南省', '长沙市', 0, NULL),
  (7, 'v2-demo-visitor-07', '203.0.113.7', 'Mozilla/5.0 Windows NT 10.0 Chrome/130.0 Edg/130.0', '中国', '广东省', '深圳市', 0, NULL),
  (8, 'v2-demo-visitor-08', '2001:db8::8', 'Mozilla/5.0 Android 14 Chrome/130.0', '中国', '浙江省', '杭州市', 0, NULL),
  (9, 'v2-demo-visitor-09', '203.0.113.9', 'Mozilla/5.0 iPhone Safari/604.1', '中国', '江苏省', '南京市', 0, NULL),
  (10, 'v2-demo-visitor-10', '203.0.113.10', 'Mozilla/5.0 Windows NT 10.0 Chrome/130.0 Edg/130.0', NULL, NULL, NULL, 0, NULL),
  (11, 'v2-demo-visitor-11', '203.0.113.11', 'Mozilla/5.0 Android 14 Chrome/130.0', '中国', '湖南省', '长沙市', 0, NULL),
  (12, 'v2-demo-visitor-12', '2001:db8::12', 'Mozilla/5.0 iPhone Safari/604.1', '中国', '广东省', '深圳市', 0, NULL),
  (13, 'v2-demo-visitor-13', '203.0.113.13', 'Mozilla/5.0 Windows NT 10.0 Chrome/130.0 Edg/130.0', '中国', '浙江省', '杭州市', 0, NULL),
  (14, 'v2-demo-visitor-14', '203.0.113.14', 'Mozilla/5.0 Android 14 Chrome/130.0', '中国', '江苏省', '南京市', 0, NULL),
  (15, 'v2-demo-visitor-15', '203.0.113.15', 'Mozilla/5.0 iPhone Safari/604.1', NULL, NULL, NULL, 0, NULL),
  (16, 'v2-demo-visitor-16', '2001:db8::16', 'Mozilla/5.0 Windows NT 10.0 Chrome/130.0 Edg/130.0', '中国', '湖南省', '长沙市', 1, -1),
  (17, 'v2-demo-visitor-17', '203.0.113.17', 'Mozilla/5.0 Android 14 Chrome/130.0', '中国', '广东省', '深圳市', 1, 7),
  (18, 'v2-demo-visitor-18', '203.0.113.18', 'Mozilla/5.0 iPhone Safari/604.1', '中国', '浙江省', '杭州市', 1, NULL);

INSERT INTO blog_visitor (fingerprint, ip_address, user_agent, country, province, city, is_blocked, blocked_until,
  first_visit_time, last_visit_time, create_time, update_time)
SELECT s.fingerprint, s.ip, s.agent, s.country, s.province, s.city, s.is_blocked,
  IF(s.block_days IS NULL, NULL, DATE_ADD(@dl_demo_anchor, INTERVAL s.block_days DAY)),
  DATE_SUB(@dl_demo_anchor, INTERVAL 30 DAY), @dl_demo_anchor,
  DATE_SUB(@dl_demo_anchor, INTERVAL 30 DAY), @dl_demo_anchor
FROM dl_demo_visitor_seed s
WHERE NOT EXISTS (SELECT 1 FROM blog_visitor v WHERE v.fingerprint = s.fingerprint);
CREATE TEMPORARY TABLE dl_demo_numbers (n INT PRIMARY KEY);

-- MySQL 8 的递归查询生成 0..239，避免手写大量纯数字样例。
INSERT INTO dl_demo_numbers (n)
WITH RECURSIVE sequence_numbers (n) AS (
  SELECT 0
  UNION ALL
  SELECT n + 1 FROM sequence_numbers WHERE n < 239
)
SELECT n FROM sequence_numbers;

INSERT INTO blog_page_view (visitor_id, user_id, article_id, site_code, page_path, page_title, referer, ip_address, user_agent, view_time)
SELECT v.visitor_id, IF(MOD(n.n, 4) = 0, u.user_id, NULL), IF(MOD(n.n, 3) = 1, a.article_id, NULL),
  CASE MOD(n.n, 3) WHEN 0 THEN 'home' WHEN 1 THEN 'blog' ELSE 'cv' END,
  IF(MOD(n.n, 3) = 1, CONCAT('/articles/v2-demo-article-', LPAD(a.seed_key, 2, '0')), '/'),
  CASE MOD(n.n, 3) WHEN 0 THEN '[演示] 主页' WHEN 1 THEN a.title ELSE '[演示] 简历' END,
  IF(MOD(n.n, 2) = 0, 'https://search.example.test/', NULL), s.ip, s.agent,
  DATE_SUB(DATE_SUB(@dl_demo_anchor, INTERVAL MOD(n.n, 30) DAY), INTERVAL FLOOR(n.n / 30) MINUTE)
FROM dl_demo_numbers n JOIN dl_demo_visitor_seed s ON s.seed_key = MOD(n.n, 18) + 1
JOIN blog_visitor v ON v.fingerprint = s.fingerprint
JOIN dl_demo_user_seed u ON u.seed_key = MOD(n.n, 6) + 1
JOIN dl_demo_article_seed a ON a.seed_key = MOD(n.n, 12) + 1
WHERE NOT EXISTS (SELECT 1 FROM blog_page_view p WHERE p.visitor_id = v.visitor_id
  AND p.site_code = CASE MOD(n.n, 3) WHEN 0 THEN 'home' WHEN 1 THEN 'blog' ELSE 'cv' END
  AND p.view_time = DATE_SUB(DATE_SUB(@dl_demo_anchor, INTERVAL MOD(n.n, 30) DAY), INTERVAL FLOOR(n.n / 30) MINUTE));

-- 七、12 条明确标注为演示的操作日志，不表示真实调用过后台接口。
INSERT INTO sys_operation_log (user_id, operation_type, operation_target, target_id, permission_code,
  operation_data, result, error_message, ip_address, operation_time)
SELECT u.user_id, CASE MOD(n.n, 4) WHEN 0 THEN 'create' WHEN 1 THEN 'update' WHEN 2 THEN 'delete' ELSE 'publish' END,
  'blog:article', a.article_id,
  CONCAT('blog:article:', CASE MOD(n.n, 4) WHEN 0 THEN 'create' WHEN 1 THEN 'update' WHEN 2 THEN 'delete' ELSE 'publish' END),
  JSON_OBJECT('demoKey', CONCAT('v2-demo-log-', LPAD(n.n + 1, 2, '0')), 'title', a.title,
    'note', '虚构操作快照，仅用于后台日志详情展示'),
  IF(MOD(n.n, 4) = 2, 0, 1), IF(MOD(n.n, 4) = 2, 'RESOURCE_IN_USE', NULL),
  '192.0.2.11', DATE_SUB(@dl_demo_anchor, INTERVAL n.n HOUR)
FROM dl_demo_numbers n JOIN dl_demo_article_seed a ON a.seed_key = n.n + 1
JOIN dl_demo_user_seed u ON u.seed_key = a.author_seed
WHERE n.n < 12 AND NOT EXISTS (SELECT 1 FROM sys_operation_log l
  WHERE JSON_UNQUOTE(JSON_EXTRACT(l.operation_data, '$.demoKey')) = CONCAT('v2-demo-log-', LPAD(n.n + 1, 2, '0')));

DROP TEMPORARY TABLE dl_demo_user_seed, dl_demo_article_seed, dl_demo_discussion_seed,
  dl_demo_comment_ids, dl_demo_message_ids, dl_demo_visitor_seed, dl_demo_numbers;
COMMIT;
SET SESSION time_zone = @dl_demo_previous_time_zone;

-- 导入后核对本批演示数据；实际数据库中的其他记录不会计入这些数量。
SELECT '展示账号' AS item, COUNT(*) AS total FROM sys_user WHERE username REGEXP '^v2_demo_(author|reader)_'
UNION ALL SELECT '文章', COUNT(*) FROM blog_article WHERE LEFT(slug, 16) = 'v2-demo-article-'
UNION ALL SELECT '评论', COUNT(*) FROM blog_comment WHERE content LIKE '[V2 演示 C%'
UNION ALL SELECT '留言', COUNT(*) FROM blog_message WHERE content LIKE '[V2 演示 M%'
UNION ALL SELECT '访客', COUNT(*) FROM blog_visitor WHERE fingerprint LIKE 'v2-demo-visitor-%'
UNION ALL SELECT '浏览明细', COUNT(*) FROM blog_page_view p JOIN blog_visitor v ON v.visitor_id = p.visitor_id
  WHERE v.fingerprint LIKE 'v2-demo-visitor-%';
