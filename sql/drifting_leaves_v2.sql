-- DriftingLeavesV2 全新数据库设计，MySQL >= 8.0.16（CHECK 约束必须生效）。
-- 2026-10-29；参考来源、权限规则及导入方法见同目录 README.md。
-- 在空库中执行；不迁移旧数据，不包含 DROP DATABASE/TABLE，不创建默认密码账号。
CREATE DATABASE IF NOT EXISTS drifting_leaves_v2
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE drifting_leaves_v2;
SET NAMES utf8mb4;

-- 一、身份与 RBAC：菜单控制展示，权限控制后端操作，两者独立授权。
CREATE TABLE sys_user (
  user_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID，Java Long；API返回字符串',
  username VARCHAR(64) NOT NULL COMMENT '登录名，不区分大小写',
  password_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '自适应密码哈希；NULL表示未设置本站密码，不可密码登录',
  nickname VARCHAR(64) NOT NULL COMMENT '用户昵称',
  email VARCHAR(254) DEFAULT NULL COMMENT '未绑定为NULL，空字符串由应用归一化为NULL',
  avatar VARCHAR(2048) DEFAULT NULL COMMENT '头像URL',
  intro VARCHAR(500) DEFAULT NULL COMMENT '个人简介',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  last_login_time DATETIME(3) DEFAULT NULL COMMENT '最近一次登录时间，未登录为NULL',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间，记录修改时自动更新',
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_user_username (username),
  UNIQUE KEY uk_user_email (email),
  CONSTRAINT ck_user_status CHECK (status IN (0, 1)),
  CONSTRAINT ck_user_username CHECK (CHAR_LENGTH(TRIM(username)) > 0),
  CONSTRAINT ck_user_hash CHECK (password_hash IS NULL OR CHAR_LENGTH(password_hash) >= 60)
) ENGINE=InnoDB COMMENT='账号与个人资料；注销/停用使用status，避免删除作者记录';

-- shortcut: 每个平台仅接入一个应用；同一平台接入多个应用时增加应用标识并调整唯一键。
CREATE TABLE sys_user_oauth (
  oauth_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '第三方账号绑定ID',
  user_id BIGINT NOT NULL COMMENT '本站用户ID；Sa-Token使用此ID登录',
  source VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'JustAuth平台标识，服务端固定为QQ、GITHUB等大写值',
  uuid VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'JustAuth返回的稳定用户标识，区分大小写，不使用昵称或邮箱',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '绑定时间',
  PRIMARY KEY (oauth_id),
  UNIQUE KEY uk_oauth_identity (source, uuid),
  UNIQUE KEY uk_oauth_user_source (user_id, source),
  CONSTRAINT fk_oauth_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE CASCADE,
  CONSTRAINT ck_oauth_source CHECK (CHAR_LENGTH(TRIM(source)) > 0),
  CONSTRAINT ck_oauth_uuid CHECK (CHAR_LENGTH(TRIM(uuid)) > 0)
) ENGINE=InnoDB COMMENT='JustAuth第三方身份绑定；每用户每平台一个账号，不存第三方Token';

CREATE TABLE sys_role (
  role_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  role_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定角色码，不带ROLE_前缀',
  role_name VARCHAR(64) NOT NULL COMMENT '角色显示名称',
  description VARCHAR(255) DEFAULT NULL COMMENT '角色说明',
  data_scope VARCHAR(4) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'SELF' COMMENT 'ALL全部 SELF本人；按授予当前权限的角色计算',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间，记录修改时自动更新',
  PRIMARY KEY (role_id),
  UNIQUE KEY uk_role_code (role_code),
  CONSTRAINT ck_role_status CHECK (status IN (0, 1)),
  CONSTRAINT ck_role_scope CHECK (data_scope IN ('ALL', 'SELF'))
) ENGINE=InnoDB COMMENT='角色；角色之间不按权重隐式继承权限';

CREATE TABLE sys_user_role (
  user_id BIGINT NOT NULL COMMENT '用户ID',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '用户获授该角色的时间',
  PRIMARY KEY (user_id, role_id),
  KEY idx_user_role_role (role_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE CASCADE,
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (role_id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='用户多角色；复合主键防重复授权';

CREATE TABLE sys_permission (
  permission_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  permission_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '模块:资源:动作；后端注解/鉴权使用，多个接口可复用',
  permission_name VARCHAR(64) NOT NULL COMMENT '权限显示名称',
  module VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属业务模块，如admin、blog、sys',
  description VARCHAR(255) DEFAULT NULL COMMENT '权限用途及使用说明',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间，记录修改时自动更新',
  PRIMARY KEY (permission_id),
  UNIQUE KEY uk_permission_code (permission_code),
  CONSTRAINT ck_permission_status CHECK (status IN (0, 1))
) ENGINE=InnoDB COMMENT='接口/操作权限字典；不依赖前端菜单是否存在或可见';

CREATE TABLE sys_role_permission (
  role_id BIGINT NOT NULL COMMENT '角色ID',
  permission_id BIGINT NOT NULL COMMENT '权限ID',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '角色获授该权限的时间',
  PRIMARY KEY (role_id, permission_id),
  KEY idx_role_permission_permission (permission_id),
  CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (role_id) ON DELETE CASCADE,
  CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (permission_id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='角色接口权限；不从角色菜单表推导';

CREATE TABLE sys_menu (
  menu_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  parent_id BIGINT DEFAULT NULL COMMENT '根节点NULL；父节点必须为目录或页面',
  menu_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '唯一菜单编码，供前端及授权配置识别',
  menu_name VARCHAR(64) NOT NULL COMMENT '菜单显示名称',
  menu_type CHAR(1) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'D目录 M页面 B按钮',
  route_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'Vue路由名，目录/页面唯一',
  path VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '前端路由，非后端接口URL',
  component VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '前端组件注册键；仅允许前端白名单组件',
  icon VARCHAR(64) DEFAULT NULL COMMENT '菜单图标标识，由前端图标库解析',
  permission_id BIGINT DEFAULT NULL COMMENT '页面访问/按钮所需权限；目录可为空',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值，数值越小越靠前',
  is_visible TINYINT NOT NULL DEFAULT 1 COMMENT '0不在导航显示 1显示；隐藏不等于撤权',
  keep_alive TINYINT NOT NULL DEFAULT 0 COMMENT '是否缓存页面组件：0否 1是',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用 1启用；禁用目录时不返回整棵子树',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间，记录修改时自动更新',
  PRIMARY KEY (menu_id),
  UNIQUE KEY uk_menu_code (menu_code),
  UNIQUE KEY uk_menu_route_name (route_name),
  UNIQUE KEY uk_menu_path (path),
  KEY idx_menu_parent_sort (parent_id, sort_order, menu_id),
  KEY idx_menu_permission (permission_id),
  CONSTRAINT fk_menu_parent FOREIGN KEY (parent_id) REFERENCES sys_menu (menu_id) ON DELETE RESTRICT,
  CONSTRAINT fk_menu_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (permission_id) ON DELETE RESTRICT,
  CONSTRAINT ck_menu_type CHECK (menu_type IN ('D', 'M', 'B')),
  CONSTRAINT ck_menu_status CHECK (status IN (0, 1)),
  CONSTRAINT ck_menu_visible CHECK (is_visible IN (0, 1)),
  CONSTRAINT ck_menu_keep_alive CHECK (keep_alive IN (0, 1)),
  CONSTRAINT ck_menu_shape CHECK (
    (menu_type = 'D' AND route_name IS NOT NULL AND path IS NOT NULL AND component IS NOT NULL)
    OR (menu_type = 'M' AND route_name IS NOT NULL AND path IS NOT NULL AND component IS NOT NULL AND permission_id IS NOT NULL)
    OR (menu_type = 'B' AND parent_id IS NOT NULL AND route_name IS NULL AND path IS NULL AND component IS NULL AND permission_id IS NOT NULL AND is_visible = 0)
  )
) ENGINE=InnoDB COMMENT='后台目录/页面/按钮树；树环和跨层级规则由应用写入时校验';

CREATE TABLE sys_role_menu (
  role_id BIGINT NOT NULL COMMENT '角色ID',
  menu_id BIGINT NOT NULL COMMENT '菜单ID',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '角色获授该菜单的时间',
  PRIMARY KEY (role_id, menu_id),
  KEY idx_role_menu_menu (menu_id),
  CONSTRAINT fk_role_menu_role FOREIGN KEY (role_id) REFERENCES sys_role (role_id) ON DELETE CASCADE,
  CONSTRAINT fk_role_menu_menu FOREIGN KEY (menu_id) REFERENCES sys_menu (menu_id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='角色可访问的菜单/按钮；配置时同时选择祖先节点';

-- 二、博客核心业务。
CREATE TABLE blog_category (
  category_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  category_name VARCHAR(64) NOT NULL COMMENT '分类名称',
  slug VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '分类URL别名，唯一且区分大小写',
  description VARCHAR(255) DEFAULT NULL COMMENT '分类说明',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值，数值越小越靠前',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0隐藏 1展示；不改变文章发布状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间，记录修改时自动更新',
  PRIMARY KEY (category_id),
  UNIQUE KEY uk_category_name (category_name),
  UNIQUE KEY uk_category_slug (slug),
  CONSTRAINT ck_category_status CHECK (status IN (0, 1))
) ENGINE=InnoDB COMMENT='文章单分类；归档按文章发布时间查询，不另建归档表';

CREATE TABLE blog_tag (
  tag_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  tag_name VARCHAR(64) NOT NULL COMMENT '标签名称',
  slug VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '标签URL别名，唯一且区分大小写',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间，记录修改时自动更新',
  PRIMARY KEY (tag_id),
  UNIQUE KEY uk_tag_name (tag_name),
  UNIQUE KEY uk_tag_slug (slug)
) ENGINE=InnoDB COMMENT='标签字典';

CREATE TABLE blog_article (
  article_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '文章ID',
  author_id BIGINT NOT NULL COMMENT '创建时取登录用户，不信任客户端提交的作者',
  category_id BIGINT DEFAULT NULL COMMENT '所属分类ID，未分类或分类删除后为NULL',
  title VARCHAR(200) NOT NULL COMMENT '文章标题',
  slug VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '文章URL别名，唯一且区分大小写',
  summary VARCHAR(500) NOT NULL DEFAULT '' COMMENT '文章摘要，用于列表及搜索结果展示',
  cover_url VARCHAR(2048) DEFAULT NULL COMMENT '文章封面图URL',
  article_type TINYINT NOT NULL DEFAULT 1 COMMENT '1原创 2转载 3翻译',
  original_url VARCHAR(2048) DEFAULT NULL COMMENT '原文链接，转载或翻译时必填',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布 2下架',
  visibility TINYINT NOT NULL DEFAULT 1 COMMENT '1公开 2私密；私密仅作者或具备相应ALL权限者可读',
  is_top TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶：0否 1是',
  allow_comment TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许评论：0否 1是',
  view_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '浏览计数；点赞/评论数由明细聚合',
  publish_time DATETIME(3) DEFAULT NULL COMMENT '首次发布时写入，编辑不覆盖',
  deleted_at DATETIME(3) DEFAULT NULL COMMENT 'NULL正常，非NULL回收站；slug仍保留',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间，记录修改时自动更新',
  PRIMARY KEY (article_id),
  UNIQUE KEY uk_article_slug (slug),
  KEY idx_article_public (status, visibility, deleted_at, is_top DESC, publish_time DESC, article_id DESC),
  KEY idx_article_category (category_id, status, visibility, deleted_at, publish_time DESC, article_id DESC),
  KEY idx_article_author (author_id, deleted_at, status, create_time DESC, article_id DESC),
  CONSTRAINT fk_article_author FOREIGN KEY (author_id) REFERENCES sys_user (user_id) ON DELETE RESTRICT,
  CONSTRAINT fk_article_category FOREIGN KEY (category_id) REFERENCES blog_category (category_id) ON DELETE SET NULL,
  CONSTRAINT ck_article_type CHECK (article_type IN (1, 2, 3)),
  CONSTRAINT ck_article_source CHECK (article_type = 1 OR (original_url IS NOT NULL AND CHAR_LENGTH(TRIM(original_url)) > 0)),
  CONSTRAINT ck_article_status CHECK (status IN (0, 1, 2)),
  CONSTRAINT ck_article_visibility CHECK (visibility IN (1, 2)),
  CONSTRAINT ck_article_top CHECK (is_top IN (0, 1)),
  CONSTRAINT ck_article_comment CHECK (allow_comment IN (0, 1)),
  CONSTRAINT ck_article_publish CHECK (status <> 1 OR publish_time IS NOT NULL)
) ENGINE=InnoDB COMMENT='文章元数据；发布、可见范围与回收站状态独立';

CREATE TABLE blog_article_content (
  article_id BIGINT NOT NULL COMMENT '文章ID，同时作为主键及关联blog_article.article_id的外键',
  markdown_content LONGTEXT NOT NULL COMMENT 'Markdown格式的文章正文',
  html_content LONGTEXT DEFAULT NULL COMMENT '可选渲染缓存；输出前由应用过滤不安全HTML',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间，记录修改时自动更新',
  PRIMARY KEY (article_id),
  CONSTRAINT fk_content_article FOREIGN KEY (article_id) REFERENCES blog_article (article_id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='正文一对一拆分，列表查询无需读取大字段';

CREATE TABLE blog_article_tag (
  article_id BIGINT NOT NULL COMMENT '文章ID，关联blog_article.article_id',
  tag_id BIGINT NOT NULL COMMENT '标签ID，关联blog_tag.tag_id',
  PRIMARY KEY (article_id, tag_id),
  KEY idx_article_tag_tag (tag_id, article_id),
  CONSTRAINT fk_article_tag_article FOREIGN KEY (article_id) REFERENCES blog_article (article_id) ON DELETE CASCADE,
  CONSTRAINT fk_article_tag_tag FOREIGN KEY (tag_id) REFERENCES blog_tag (tag_id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='文章多标签；禁止逗号拼接标签ID';

CREATE TABLE blog_comment (
  comment_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  article_id BIGINT NOT NULL COMMENT '所属文章ID，关联blog_article.article_id',
  user_id BIGINT NOT NULL COMMENT '仅登录用户评论',
  parent_id BIGINT DEFAULT NULL COMMENT '被回复的评论ID，必须属于同一文章',
  content TEXT NOT NULL COMMENT '纯文本或经过白名单过滤的内容',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0待审核 1通过 2驳回 3作者删除占位',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间，记录修改时自动更新',
  PRIMARY KEY (comment_id),
  UNIQUE KEY uk_comment_article (comment_id, article_id),
  KEY idx_comment_article (article_id, status, create_time, comment_id),
  KEY idx_comment_parent (parent_id, article_id),
  KEY idx_comment_user (user_id, create_time DESC),
  KEY idx_comment_review (status, create_time, comment_id),
  CONSTRAINT fk_comment_article FOREIGN KEY (article_id) REFERENCES blog_article (article_id) ON DELETE RESTRICT,
  CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE RESTRICT,
  CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id, article_id) REFERENCES blog_comment (comment_id, article_id) ON DELETE RESTRICT,
  CONSTRAINT ck_comment_status CHECK (status IN (0, 1, 2, 3)),
  CONSTRAINT ck_comment_content CHECK (CHAR_LENGTH(TRIM(content)) BETWEEN 1 AND 2000)
) ENGINE=InnoDB COMMENT='文章评论与回复；删除时清空内容为占位文案并置status=3，保留回复链';

CREATE TABLE blog_article_like (
  article_id BIGINT NOT NULL COMMENT '被点赞的文章ID',
  user_id BIGINT NOT NULL COMMENT '点赞用户ID',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '点赞时间',
  PRIMARY KEY (article_id, user_id),
  KEY idx_article_like_user (user_id, create_time DESC),
  CONSTRAINT fk_like_article FOREIGN KEY (article_id) REFERENCES blog_article (article_id) ON DELETE CASCADE,
  CONSTRAINT fk_like_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='登录用户点赞，复合主键防重复；取消点赞物理删除';

CREATE TABLE blog_friend_link (
  link_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '友链ID',
  link_name VARCHAR(100) NOT NULL COMMENT '友情链接显示名称',
  url VARCHAR(2048) NOT NULL COMMENT '友情链接目标URL',
  avatar VARCHAR(2048) DEFAULT NULL COMMENT '友链站点头像或图标URL',
  description VARCHAR(255) DEFAULT NULL COMMENT '友链站点简介',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值，数值越小越靠前',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0待审核 1展示 2驳回/下架',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间，记录修改时自动更新',
  PRIMARY KEY (link_id),
  KEY idx_friend_link_status_sort (status, sort_order, link_id),
  CONSTRAINT ck_friend_link_status CHECK (status IN (0, 1, 2))
) ENGINE=InnoDB COMMENT='友情链接；URL协议及长度由应用校验';

CREATE TABLE blog_file (
  file_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  uploader_id BIGINT NOT NULL COMMENT '上传者用户ID，关联sys_user.user_id',
  original_name VARCHAR(255) NOT NULL COMMENT '上传时的原始文件名，仅用于展示',
  storage_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'local' COMMENT '存储类型，如local本地存储、oss对象存储',
  bucket VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '' COMMENT '对象存储桶名称，本地存储时为空字符串',
  object_key VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '服务端生成的存储键，不使用用户文件名作为路径',
  mime_type VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '文件MIME类型，如image/png',
  size_bytes BIGINT UNSIGNED NOT NULL COMMENT '文件大小，单位字节',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '文件上传记录创建时间',
  PRIMARY KEY (file_id),
  UNIQUE KEY uk_file_storage (storage_type, bucket, object_key),
  KEY idx_file_uploader (uploader_id, create_time DESC),
  CONSTRAINT fk_file_uploader FOREIGN KEY (uploader_id) REFERENCES sys_user (user_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='上传文件元数据；私有对象由服务端鉴权后签发地址';

CREATE TABLE blog_site_config (
  config_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '唯一配置键，如site、comment',
  config_value JSON NOT NULL COMMENT 'JSON格式的配置内容',
  description VARCHAR(255) DEFAULT NULL COMMENT '配置项用途说明',
  is_public TINYINT NOT NULL DEFAULT 0 COMMENT '仅显式标记1的配置可返回前台',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间，记录修改时自动更新',
  PRIMARY KEY (config_key),
  CONSTRAINT ck_config_public CHECK (is_public IN (0, 1))
) ENGINE=InnoDB COMMENT='站点展示与业务开关；不保存数据库/邮件/对象存储密码';

-- 三、基础角色、权限与菜单。无用户、文章、外部SQL业务数据。
START TRANSACTION;
INSERT INTO sys_role (role_id, role_code, role_name, description, data_scope) VALUES
  (1, 'admin', '管理员', '全部内容及系统授权管理；新增权限需要显式授权', 'ALL'),
  (2, 'author', '作者', '后台写作、管理本人文章与上传文件', 'SELF'),
  (3, 'member', '注册用户', '前台评论、点赞及删除本人评论，无后台菜单', 'SELF');

INSERT INTO sys_permission (permission_id, permission_code, permission_name, module) VALUES
  (1, 'admin:access', '进入管理后台', 'admin'),
  (10, 'blog:article:query', '查询管理文章', 'blog'),
  (11, 'blog:article:create', '创建文章', 'blog'),
  (12, 'blog:article:update', '编辑文章', 'blog'),
  (13, 'blog:article:delete', '回收文章', 'blog'),
  (14, 'blog:article:publish', '发布或下架文章', 'blog'),
  (15, 'blog:article:restore', '恢复回收站文章', 'blog'),
  (20, 'blog:category:query', '查询管理分类', 'blog'),
  (21, 'blog:category:create', '创建分类', 'blog'),
  (22, 'blog:category:update', '编辑分类', 'blog'),
  (23, 'blog:category:delete', '删除分类', 'blog'),
  (30, 'blog:tag:query', '查询管理标签', 'blog'),
  (31, 'blog:tag:create', '创建标签', 'blog'),
  (32, 'blog:tag:update', '编辑标签', 'blog'),
  (33, 'blog:tag:delete', '删除标签', 'blog'),
  (40, 'blog:comment:query', '查询管理评论', 'blog'),
  (41, 'blog:comment:review', '审核评论', 'blog'),
  (42, 'blog:comment:moderate', '管理删除评论', 'blog'),
  (43, 'blog:comment:create', '前台发表评论', 'blog'),
  (44, 'blog:comment:deleteOwn', '删除本人评论', 'blog'),
  (45, 'blog:article:like', '前台点赞或取消点赞', 'blog'),
  (50, 'blog:link:query', '查询管理友链', 'blog'),
  (51, 'blog:link:create', '创建友链', 'blog'),
  (52, 'blog:link:update', '编辑及审核友链', 'blog'),
  (53, 'blog:link:delete', '删除友链', 'blog'),
  (60, 'blog:file:query', '查询管理文件', 'blog'),
  (61, 'blog:file:upload', '上传文件', 'blog'),
  (62, 'blog:file:delete', '删除文件', 'blog'),
  (70, 'blog:config:query', '查询站点配置', 'blog'),
  (71, 'blog:config:update', '修改站点配置', 'blog'),
  (80, 'sys:user:query', '查询用户', 'sys'),
  (81, 'sys:user:create', '创建用户', 'sys'),
  (82, 'sys:user:update', '编辑及启停用户', 'sys'),
  (83, 'sys:user:assignRole', '分配用户角色', 'sys'),
  (90, 'sys:role:query', '查询角色', 'sys'),
  (91, 'sys:role:create', '创建角色', 'sys'),
  (92, 'sys:role:update', '编辑及启停角色', 'sys'),
  (93, 'sys:role:delete', '删除角色', 'sys'),
  (94, 'sys:role:grant', '分配角色菜单和权限', 'sys'),
  (100, 'sys:menu:query', '查询菜单', 'sys'),
  (101, 'sys:menu:create', '创建菜单', 'sys'),
  (102, 'sys:menu:update', '编辑及启停菜单', 'sys'),
  (103, 'sys:menu:delete', '删除菜单', 'sys'),
  (110, 'sys:permission:query', '查询权限字典', 'sys'),
  (111, 'sys:permission:create', '创建权限', 'sys'),
  (112, 'sys:permission:update', '编辑及启停权限', 'sys'),
  (113, 'sys:permission:delete', '删除权限', 'sys');

-- 管理员显式获得当前全部权限，作者及会员按白名单授权。
INSERT INTO sys_role_permission (role_id, permission_id)
  SELECT 1, permission_id FROM sys_permission;
INSERT INTO sys_role_permission (role_id, permission_id)
  SELECT 2, permission_id FROM sys_permission
  WHERE permission_id IN (1, 10, 11, 12, 13, 14, 15, 20, 30, 43, 44, 45, 60, 61, 62);
INSERT INTO sys_role_permission (role_id, permission_id)
  SELECT 3, permission_id FROM sys_permission WHERE permission_id IN (43, 44, 45);

-- component均为未来前端的约定键，当前工作区没有对应组件实现。
INSERT INTO sys_menu (menu_id, parent_id, menu_code, menu_name, menu_type, route_name, path, component, icon, permission_id, sort_order) VALUES
  (100, NULL, 'content', '内容管理', 'D', 'Content', '/content', 'Layout', 'document', NULL, 10),
  (200, NULL, 'system', '系统管理', 'D', 'System', '/system', 'Layout', 'setting', NULL, 20);
INSERT INTO sys_menu (menu_id, parent_id, menu_code, menu_name, menu_type, route_name, path, component, icon, permission_id, sort_order) VALUES
  (110, 100, 'article', '文章管理', 'M', 'Article', '/content/articles', 'content/article/index', 'document', 10, 10),
  (120, 100, 'category', '分类管理', 'M', 'Category', '/content/categories', 'content/category/index', 'folder', 20, 20),
  (130, 100, 'tag', '标签管理', 'M', 'Tag', '/content/tags', 'content/tag/index', 'tag', 30, 30),
  (140, 100, 'comment', '评论管理', 'M', 'Comment', '/content/comments', 'content/comment/index', 'chat', 40, 40),
  (150, 100, 'link', '友链管理', 'M', 'FriendLink', '/content/links', 'content/link/index', 'link', 50, 50),
  (160, 100, 'file', '文件管理', 'M', 'File', '/content/files', 'content/file/index', 'picture', 60, 60),
  (210, 200, 'user', '用户管理', 'M', 'User', '/system/users', 'system/user/index', 'user', 80, 10),
  (220, 200, 'role', '角色管理', 'M', 'Role', '/system/roles', 'system/role/index', 'lock', 90, 20),
  (230, 200, 'menu', '菜单管理', 'M', 'Menu', '/system/menus', 'system/menu/index', 'menu', 100, 30),
  (240, 200, 'permission', '权限管理', 'M', 'Permission', '/system/permissions', 'system/permission/index', 'key', 110, 40),
  (250, 200, 'config', '站点配置', 'M', 'SiteConfig', '/system/config', 'system/config/index', 'setting', 70, 50);

-- 每个后台操作生成对应按钮；前台评论/点赞无需后台按钮。
INSERT INTO sys_menu (menu_id, parent_id, menu_code, menu_name, menu_type, permission_id, sort_order, is_visible)
SELECT 1000 + p.permission_id,
  CASE
    WHEN p.permission_id BETWEEN 11 AND 15 THEN 110
    WHEN p.permission_id BETWEEN 21 AND 23 THEN 120
    WHEN p.permission_id BETWEEN 31 AND 33 THEN 130
    WHEN p.permission_id BETWEEN 41 AND 42 THEN 140
    WHEN p.permission_id BETWEEN 51 AND 53 THEN 150
    WHEN p.permission_id BETWEEN 61 AND 62 THEN 160
    WHEN p.permission_id = 71 THEN 250
    WHEN p.permission_id BETWEEN 81 AND 83 THEN 210
    WHEN p.permission_id BETWEEN 91 AND 94 THEN 220
    WHEN p.permission_id BETWEEN 101 AND 103 THEN 230
    WHEN p.permission_id BETWEEN 111 AND 113 THEN 240
  END,
  p.permission_code, p.permission_name, 'B', p.permission_id, p.permission_id, 0
FROM sys_permission p
WHERE p.permission_id IN (11,12,13,14,15,21,22,23,31,32,33,41,42,51,52,53,61,62,71,81,82,83,91,92,93,94,101,102,103,111,112,113);

INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, menu_id FROM sys_menu;
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, m.menu_id FROM sys_menu m
WHERE m.menu_id = 100 OR EXISTS (
  SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = 2 AND rp.permission_id = m.permission_id
);

INSERT INTO blog_site_config (config_key, config_value, description, is_public) VALUES
  ('site', JSON_OBJECT('title', 'DriftingLeaves', 'description', '记录与分享', 'icp', ''), '公开站点信息', 1),
  ('comment', JSON_OBJECT('enabled', TRUE, 'reviewRequired', TRUE), '评论默认开启并审核；前端不得用本配置绕过服务端校验', 0);
COMMIT;
