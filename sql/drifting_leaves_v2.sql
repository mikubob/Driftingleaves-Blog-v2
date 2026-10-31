-- DriftingLeavesV2 全新数据库设计，MySQL >= 8.0.16（CHECK 约束必须生效）。
-- 2026-10-30；参考来源、权限规则及导入方法见同目录 README.md。
-- 在空库中执行；不迁移旧数据，不包含 DROP DATABASE/TABLE，包含指定的初始管理员账号。
CREATE DATABASE IF NOT EXISTS driftingleaves
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE driftingleaves;
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

-- 三、主页与简历。展示资料独立于登录资料，公开邮箱不直接读取sys_user.email。
CREATE TABLE home_profile (
  profile_id BIGINT NOT NULL DEFAULT 1 COMMENT '单站展示资料固定为1，不按登录用户切换',
  user_id BIGINT NOT NULL COMMENT '站点主人账号，不影响公开资料的独立编辑',
  nickname VARCHAR(64) NOT NULL COMMENT '公开展示昵称',
  tagline VARCHAR(100) NOT NULL DEFAULT '' COMMENT '个人标签或一句话介绍',
  description VARCHAR(500) DEFAULT NULL COMMENT '公开个人简介',
  avatar VARCHAR(2048) DEFAULT NULL COMMENT '公开头像URL',
  website VARCHAR(2048) DEFAULT NULL COMMENT '个人网站URL',
  public_email VARCHAR(254) DEFAULT NULL COMMENT '自愿公开的联系邮箱，不作为登录邮箱',
  github_url VARCHAR(2048) DEFAULT NULL COMMENT 'GitHub主页URL',
  location VARCHAR(100) DEFAULT NULL COMMENT '公开所在地',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  PRIMARY KEY (profile_id),
  CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE RESTRICT,
  CONSTRAINT ck_profile_singleton CHECK (profile_id = 1),
  CONSTRAINT ck_profile_nickname CHECK (CHAR_LENGTH(TRIM(nickname)) > 0)
) ENGINE=InnoDB COMMENT='主页、博客侧栏与简历共用的站点主人展示资料';

CREATE TABLE home_social_link (
  social_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '社交链接ID',
  name VARCHAR(64) NOT NULL COMMENT '平台或链接名称',
  icon VARCHAR(2048) DEFAULT NULL COMMENT '图标URL或前端白名单图标键，不存任意HTML',
  url VARCHAR(2048) NOT NULL COMMENT '社交链接；应用校验允许的协议',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值，数值越小越靠前',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0隐藏 1展示',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  PRIMARY KEY (social_id),
  KEY idx_social_status_sort (status, sort_order, social_id),
  CONSTRAINT ck_social_status CHECK (status IN (0, 1)),
  CONSTRAINT ck_social_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
  CONSTRAINT ck_social_url CHECK (CHAR_LENGTH(TRIM(url)) > 0)
) ENGINE=InnoDB COMMENT='站点社交媒体链接，与OAuth登录平台配置无关';

CREATE TABLE cv_experience (
  experience_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '经历ID',
  experience_type TINYINT NOT NULL COMMENT '0教育 1工作或实习 2项目',
  title VARCHAR(200) NOT NULL COMMENT '学校、公司或项目名称',
  subtitle VARCHAR(200) DEFAULT NULL COMMENT '专业、职位或项目角色',
  logo_url VARCHAR(2048) DEFAULT NULL COMMENT '校徽或公司、项目图标URL',
  content TEXT NOT NULL COMMENT 'Markdown内容；渲染后由应用过滤不安全HTML',
  start_date DATE NOT NULL COMMENT '开始日期',
  end_date DATE DEFAULT NULL COMMENT '结束日期；NULL表示至今',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值，数值越小越靠前',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0隐藏 1展示',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  PRIMARY KEY (experience_id),
  KEY idx_experience_public (status, experience_type, sort_order, start_date DESC, experience_id),
  CONSTRAINT ck_experience_type CHECK (experience_type IN (0, 1, 2)),
  CONSTRAINT ck_experience_status CHECK (status IN (0, 1)),
  CONSTRAINT ck_experience_dates CHECK (end_date IS NULL OR end_date >= start_date),
  CONSTRAINT ck_experience_title CHECK (CHAR_LENGTH(TRIM(title)) > 0)
) ENGINE=InnoDB COMMENT='站点主人教育、工作和项目经历';

CREATE TABLE cv_skill (
  skill_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '技能ID',
  name VARCHAR(64) NOT NULL COMMENT '技能名称',
  description VARCHAR(500) DEFAULT NULL COMMENT '技能描述',
  icon VARCHAR(2048) DEFAULT NULL COMMENT '图标URL或前端白名单图标键',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值，数值越小越靠前',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0隐藏 1展示',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  PRIMARY KEY (skill_id),
  KEY idx_skill_status_sort (status, sort_order, skill_id),
  CONSTRAINT ck_skill_status CHECK (status IN (0, 1)),
  CONSTRAINT ck_skill_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB COMMENT='简历技能展示';

-- 四、留言、音乐与订阅。互动统一使用本站用户，不复制旧访客Token或管理员账号。
CREATE TABLE blog_message (
  message_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '留言ID',
  user_id BIGINT NOT NULL COMMENT '留言或回复作者，从当前登录会话读取',
  parent_id BIGINT DEFAULT NULL COMMENT '被回复的留言ID；创建后不可修改，应用防止环',
  content TEXT NOT NULL COMMENT '纯文本或Markdown原文',
  content_format TINYINT NOT NULL DEFAULT 0 COMMENT '0纯文本 1Markdown；输出由应用安全渲染',
  is_secret TINYINT NOT NULL DEFAULT 0 COMMENT '0公开 1悄悄话；仅本人及获授ALL管理权限者可读，回复继承私密范围',
  notify_reply TINYINT NOT NULL DEFAULT 0 COMMENT '是否请求回复邮件通知；应用另行验证收件邮箱',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0待审核 1通过 2驳回 3作者删除占位',
  edited_at DATETIME(3) DEFAULT NULL COMMENT '作者最近编辑时间；编辑后重新审核',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  PRIMARY KEY (message_id),
  KEY idx_message_public (parent_id, status, is_secret, create_time, message_id),
  KEY idx_message_user (user_id, create_time DESC, message_id),
  KEY idx_message_review (status, create_time, message_id),
  CONSTRAINT fk_message_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE RESTRICT,
  CONSTRAINT fk_message_parent FOREIGN KEY (parent_id) REFERENCES blog_message (message_id) ON DELETE RESTRICT,
  CONSTRAINT ck_message_status CHECK (status IN (0, 1, 2, 3)),
  CONSTRAINT ck_message_format CHECK (content_format IN (0, 1)),
  CONSTRAINT ck_message_secret CHECK (is_secret IN (0, 1)),
  CONSTRAINT ck_message_notify CHECK (notify_reply IN (0, 1)),
  CONSTRAINT ck_message_content CHECK (CHAR_LENGTH(TRIM(content)) BETWEEN 1 AND 2000)
) ENGINE=InnoDB COMMENT='独立留言板与回复；删除采用占位文案保留回复链，管理员身份实时鉴权';

CREATE TABLE blog_music (
  music_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '音乐ID',
  title VARCHAR(200) NOT NULL COMMENT '音乐标题',
  artist VARCHAR(200) DEFAULT NULL COMMENT '歌手或作者',
  duration_seconds INT UNSIGNED DEFAULT NULL COMMENT '时长，单位秒；未知为NULL',
  cover_url VARCHAR(2048) DEFAULT NULL COMMENT '封面URL',
  audio_url VARCHAR(2048) NOT NULL COMMENT '音频URL；应用校验协议与文件访问权限',
  lyric_url VARCHAR(2048) DEFAULT NULL COMMENT '歌词URL；无歌词为NULL',
  lyric_type VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'lrc、json或txt；无歌词为NULL',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值，数值越小越靠前',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0隐藏 1展示',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  PRIMARY KEY (music_id),
  KEY idx_music_status_sort (status, sort_order, music_id),
  CONSTRAINT ck_music_status CHECK (status IN (0, 1)),
  CONSTRAINT ck_music_title CHECK (CHAR_LENGTH(TRIM(title)) > 0),
  CONSTRAINT ck_music_audio CHECK (CHAR_LENGTH(TRIM(audio_url)) > 0),
  CONSTRAINT ck_music_lyric CHECK (
    (lyric_url IS NULL AND lyric_type IS NULL)
    OR (lyric_url IS NOT NULL AND CHAR_LENGTH(TRIM(lyric_url)) > 0 AND lyric_type IS NOT NULL AND lyric_type IN ('lrc', 'json', 'txt'))
  )
) ENGINE=InnoDB COMMENT='站点背景音乐播放列表；是否有歌词由lyric_url判断';

CREATE TABLE blog_rss_subscription (
  subscription_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '订阅ID',
  user_id BIGINT NOT NULL COMMENT '订阅用户，从当前登录会话读取',
  email VARCHAR(254) NOT NULL COMMENT '订阅邮箱；应用归一化并通过邮件验证，不直接信任登录资料邮箱',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0待验证 1已订阅 2已退订；换邮箱必须重新验证',
  verified_at DATETIME(3) DEFAULT NULL COMMENT '当前邮箱验证成功时间',
  subscribe_time DATETIME(3) DEFAULT NULL COMMENT '最近激活订阅时间',
  unsubscribe_time DATETIME(3) DEFAULT NULL COMMENT '最近退订时间',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  PRIMARY KEY (subscription_id),
  UNIQUE KEY uk_rss_user (user_id),
  UNIQUE KEY uk_rss_email (email),
  KEY idx_rss_delivery (status, subscription_id),
  CONSTRAINT fk_rss_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE CASCADE,
  CONSTRAINT ck_rss_status CHECK (status IN (0, 1, 2)),
  CONSTRAINT ck_rss_email CHECK (CHAR_LENGTH(TRIM(email)) > 0),
  CONSTRAINT ck_rss_active CHECK (status <> 1 OR (verified_at IS NOT NULL AND subscribe_time IS NOT NULL AND unsubscribe_time IS NULL)),
  CONSTRAINT ck_rss_unsubscribe CHECK (status <> 2 OR unsubscribe_time IS NOT NULL)
) ENGINE=InnoDB COMMENT='RSS邮件推送订阅；XML Feed直接由公开文章生成；确认及退订票据由Redis管理';

-- 五、访问统计与操作审计。统计标识不授予登录、评论、点赞或订阅权限。
CREATE TABLE blog_visitor (
  visitor_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '统计访客ID，不是本站用户ID',
  fingerprint VARCHAR(150) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '匿名统计标识，不作为可信身份或账号封禁依据',
  ip_address VARCHAR(45) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '最近访问IP；仅从可信代理链提取',
  user_agent VARCHAR(1024) DEFAULT NULL COMMENT '最近访问的User-Agent，应用限制长度',
  country VARCHAR(64) DEFAULT NULL COMMENT '国家',
  province VARCHAR(64) DEFAULT NULL COMMENT '省份',
  city VARCHAR(64) DEFAULT NULL COMMENT '城市',
  is_blocked TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1限制此统计标识；不能替代sys_user.status',
  blocked_until DATETIME(3) DEFAULT NULL COMMENT '封禁到期时间；已封禁且NULL表示不自动到期',
  first_visit_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次访问时间',
  last_visit_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近访问时间',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  PRIMARY KEY (visitor_id),
  UNIQUE KEY uk_visitor_fingerprint (fingerprint),
  KEY idx_visitor_first_visit (first_visit_time, visitor_id),
  KEY idx_visitor_last_visit (last_visit_time DESC, visitor_id),
  KEY idx_visitor_blocked (is_blocked, blocked_until, visitor_id),
  CONSTRAINT ck_visitor_fingerprint CHECK (CHAR_LENGTH(TRIM(fingerprint)) > 0),
  CONSTRAINT ck_visitor_blocked CHECK (is_blocked IN (0, 1)),
  CONSTRAINT ck_visitor_block_time CHECK (is_blocked = 1 OR blocked_until IS NULL),
  CONSTRAINT ck_visitor_times CHECK (last_visit_time >= first_visit_time)
) ENGINE=InnoDB COMMENT='跨主页、博客和简历的匿名访客统计；访问次数按浏览明细聚合';

CREATE TABLE blog_page_view (
  view_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '浏览记录ID',
  visitor_id BIGINT DEFAULT NULL COMMENT '匿名访客ID；访客统计记录清理后为NULL',
  user_id BIGINT DEFAULT NULL COMMENT '访问时已登录的本站用户ID，由服务端读取；匿名为NULL',
  article_id BIGINT DEFAULT NULL COMMENT '文章详情访问对应的文章ID；其他页面为NULL',
  site_code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'home、blog或cv；由服务端核验站点',
  page_path VARCHAR(2048) NOT NULL COMMENT '页面路径；不记录Token等敏感查询参数',
  page_title VARCHAR(200) DEFAULT NULL COMMENT '访问时的页面标题',
  referer VARCHAR(2048) DEFAULT NULL COMMENT '来源URL；应用去除敏感查询参数',
  ip_address VARCHAR(45) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '访问IP；仅从可信代理链提取',
  user_agent VARCHAR(1024) DEFAULT NULL COMMENT '访问时的User-Agent',
  view_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '访问时间',
  PRIMARY KEY (view_id),
  KEY idx_view_time (view_time DESC, view_id),
  KEY idx_view_visitor (visitor_id, view_time DESC, view_id),
  KEY idx_view_user (user_id, view_time DESC),
  KEY idx_view_article (article_id, view_time DESC),
  KEY idx_view_site_path (site_code, page_path(191), view_time),
  CONSTRAINT fk_view_visitor FOREIGN KEY (visitor_id) REFERENCES blog_visitor (visitor_id) ON DELETE SET NULL,
  CONSTRAINT fk_view_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE SET NULL,
  CONSTRAINT fk_view_article FOREIGN KEY (article_id) REFERENCES blog_article (article_id) ON DELETE SET NULL,
  CONSTRAINT ck_view_site CHECK (site_code IN ('home', 'blog', 'cv')),
  CONSTRAINT ck_view_path CHECK (CHAR_LENGTH(TRIM(page_path)) > 0)
) ENGINE=InnoDB COMMENT='页面浏览明细；支持站点趋势、访客记录及按时间段文章排行';

CREATE TABLE sys_operation_log (
  log_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '操作日志ID',
  user_id BIGINT DEFAULT NULL COMMENT '操作人本站用户ID；账号物理清理后为NULL',
  operation_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作类型，如create、update、delete、grant',
  operation_target VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '目标模块或资源',
  target_id BIGINT DEFAULT NULL COMMENT '目标资源ID；批量或无数字ID的操作可为空',
  permission_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '本次检查的权限码快照，不授予权限',
  operation_data JSON DEFAULT NULL COMMENT '应用按白名单记录的脱敏操作摘要；禁止密码、Token及第三方密钥',
  result TINYINT NOT NULL COMMENT '0失败 1成功',
  error_message VARCHAR(500) DEFAULT NULL COMMENT '脱敏错误摘要；不保存原始异常堆栈或SQL',
  ip_address VARCHAR(45) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '操作IP；仅从可信代理链提取',
  operation_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
  PRIMARY KEY (log_id),
  KEY idx_log_time (operation_time DESC, log_id),
  KEY idx_log_user (user_id, operation_time DESC),
  KEY idx_log_target (operation_target, target_id, operation_time DESC),
  KEY idx_log_type (operation_type, operation_time DESC),
  CONSTRAINT fk_log_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE SET NULL,
  CONSTRAINT ck_log_result CHECK (result IN (0, 1))
) ENGINE=InnoDB COMMENT='管理操作审计，使用现有本站账号与权限码；不复制旧admin表';

-- 六、基础角色、初始管理员、权限与菜单。不包含文章或外部SQL业务数据。
START TRANSACTION;
INSERT INTO sys_role (role_id, role_code, role_name, description, data_scope) VALUES
  (1, 'admin', '管理员', '全部内容及系统授权管理；新增权限需要显式授权', 'ALL'),
  (2, 'author', '作者', '后台写作、管理本人文章与上传文件', 'SELF'),
  (3, 'member', '注册用户', '前台评论、点赞、本人留言及邮件订阅，无后台菜单', 'SELF');

-- 初始管理员的密码使用项目 BCrypt 编码器按成本 12 生成。
INSERT INTO sys_user (username, password_hash, nickname) VALUES
  ('yyzytx5201314', '$2a$12$9FfGVxieqB9LJNDF1bOIWOopsNDJsoCHsXwvVBjIW.dgGtfKy3ZF6', 'yyzytx5201314');
INSERT INTO sys_user_role (user_id, role_id)
  SELECT user_id, 1 FROM sys_user WHERE username = 'yyzytx5201314';

INSERT INTO sys_permission (permission_id, permission_code, permission_name, module) VALUES
  (1, 'admin:access', '进入管理后台', 'admin'),
  (10, 'blog:article:query', '查询管理文章', 'blog'),
  (11, 'blog:article:create', '创建文章', 'blog'),
  (12, 'blog:article:update', '编辑文章', 'blog'),
  (13, 'blog:article:delete', '回收文章', 'blog'),
  (14, 'blog:article:publish', '发布或下架文章', 'blog'),
  (15, 'blog:article:restore', '恢复回收站文章', 'blog'),
  (16, 'blog:article:purge', '永久删除回收站文章（无法恢复）', 'blog'),
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
  (113, 'sys:permission:delete', '删除权限', 'sys'),
  (120, 'home:profile:query', '查询主页资料', 'home'),
  (121, 'home:profile:update', '设置主页资料', 'home'),
  (130, 'home:social:query', '查询社交链接', 'home'),
  (131, 'home:social:create', '创建社交链接', 'home'),
  (132, 'home:social:update', '编辑社交链接', 'home'),
  (133, 'home:social:delete', '删除社交链接', 'home'),
  (140, 'cv:experience:query', '查询简历经历', 'cv'),
  (141, 'cv:experience:create', '创建简历经历', 'cv'),
  (142, 'cv:experience:update', '编辑简历经历', 'cv'),
  (143, 'cv:experience:delete', '删除简历经历', 'cv'),
  (150, 'cv:skill:query', '查询简历技能', 'cv'),
  (151, 'cv:skill:create', '创建简历技能', 'cv'),
  (152, 'cv:skill:update', '编辑简历技能', 'cv'),
  (153, 'cv:skill:delete', '删除简历技能', 'cv'),
  (160, 'blog:message:query', '查询管理留言', 'blog'),
  (161, 'blog:message:review', '审核留言', 'blog'),
  (162, 'blog:message:moderate', '管理删除留言', 'blog'),
  (163, 'blog:message:create', '前台发表或回复留言', 'blog'),
  (164, 'blog:message:updateOwn', '编辑本人留言', 'blog'),
  (165, 'blog:message:deleteOwn', '删除本人留言', 'blog'),
  (170, 'blog:music:query', '查询管理音乐', 'blog'),
  (171, 'blog:music:create', '创建音乐', 'blog'),
  (172, 'blog:music:update', '编辑音乐', 'blog'),
  (173, 'blog:music:delete', '删除音乐', 'blog'),
  (180, 'blog:rss:query', '查询邮件订阅', 'blog'),
  (181, 'blog:rss:update', '管理邮件订阅状态', 'blog'),
  (182, 'blog:rss:delete', '删除邮件订阅', 'blog'),
  (183, 'blog:rss:subscribe', '管理本人邮件订阅及邮箱确认', 'blog'),
  (184, 'blog:rss:unsubscribe', '取消本人邮件订阅', 'blog'),
  (190, 'blog:visitor:query', '查询访客统计', 'blog'),
  (191, 'blog:visitor:block', '封禁访客统计标识', 'blog'),
  (192, 'blog:visitor:unblock', '解封访客统计标识', 'blog'),
  (193, 'blog:visitor:delete', '删除访客统计记录', 'blog'),
  (200, 'blog:view:query', '查询浏览记录', 'blog'),
  (201, 'blog:view:delete', '删除浏览记录', 'blog'),
  (210, 'blog:statistics:query', '查询访问统计报表', 'blog'),
  (220, 'sys:log:query', '查询操作日志', 'sys'),
  (221, 'sys:log:delete', '清理操作日志', 'sys');

-- 管理员显式获得当前全部权限，作者及会员按白名单授权。
INSERT INTO sys_role_permission (role_id, permission_id)
  SELECT 1, permission_id FROM sys_permission;
INSERT INTO sys_role_permission (role_id, permission_id)
  SELECT 2, permission_id FROM sys_permission
  WHERE permission_id IN (1, 10, 11, 12, 13, 14, 15, 20, 30, 43, 44, 45, 60, 61, 62, 163, 164, 165, 183, 184);
INSERT INTO sys_role_permission (role_id, permission_id)
  SELECT 3, permission_id FROM sys_permission WHERE permission_id IN (43, 44, 45, 163, 164, 165, 183, 184);

-- component均为未来前端的约定键，当前工作区没有对应组件实现。
INSERT INTO sys_menu (menu_id, parent_id, menu_code, menu_name, menu_type, route_name, path, component, icon, permission_id, sort_order) VALUES
  (100, NULL, 'content', '内容管理', 'D', 'Content', '/content', 'Layout', 'document', NULL, 10),
  (200, NULL, 'system', '系统管理', 'D', 'System', '/system', 'Layout', 'setting', NULL, 20),
  (300, NULL, 'home', '主页管理', 'D', 'Home', '/home', 'Layout', 'home', NULL, 30),
  (400, NULL, 'cv', '简历管理', 'D', 'Cv', '/cv', 'Layout', 'document', NULL, 40),
  (500, NULL, 'statistics', '访问统计', 'D', 'Statistics', '/statistics', 'Layout', 'chart', NULL, 50);
INSERT INTO sys_menu (menu_id, parent_id, menu_code, menu_name, menu_type, route_name, path, component, icon, permission_id, sort_order) VALUES
  (110, 100, 'article', '文章管理', 'M', 'Article', '/content/articles', 'content/article/index', 'document', 10, 10),
  (120, 100, 'category', '分类管理', 'M', 'Category', '/content/categories', 'content/category/index', 'folder', 20, 20),
  (130, 100, 'tag', '标签管理', 'M', 'Tag', '/content/tags', 'content/tag/index', 'tag', 30, 30),
  (140, 100, 'comment', '评论管理', 'M', 'Comment', '/content/comments', 'content/comment/index', 'chat', 40, 40),
  (150, 100, 'link', '友链管理', 'M', 'FriendLink', '/content/links', 'content/link/index', 'link', 50, 50),
  (160, 100, 'file', '文件管理', 'M', 'File', '/content/files', 'content/file/index', 'picture', 60, 60),
  (170, 100, 'message', '留言管理', 'M', 'Message', '/content/messages', 'content/message/index', 'chat', 160, 70),
  (180, 100, 'music', '音乐管理', 'M', 'Music', '/content/music', 'content/music/index', 'music', 170, 80),
  (190, 100, 'rss', '邮件订阅', 'M', 'RssSubscription', '/content/subscriptions', 'content/rss/index', 'mail', 180, 90),
  (210, 200, 'user', '用户管理', 'M', 'User', '/system/users', 'system/user/index', 'user', 80, 10),
  (220, 200, 'role', '角色管理', 'M', 'Role', '/system/roles', 'system/role/index', 'lock', 90, 20),
  (230, 200, 'menu', '菜单管理', 'M', 'Menu', '/system/menus', 'system/menu/index', 'menu', 100, 30),
  (240, 200, 'permission', '权限管理', 'M', 'Permission', '/system/permissions', 'system/permission/index', 'key', 110, 40),
  (250, 200, 'config', '站点配置', 'M', 'SiteConfig', '/system/config', 'system/config/index', 'setting', 70, 50),
  (260, 200, 'operationLog', '操作日志', 'M', 'OperationLog', '/system/logs', 'system/log/index', 'document', 220, 60),
  (310, 300, 'homeProfile', '主页资料', 'M', 'HomeProfile', '/home/profile', 'home/profile/index', 'user', 120, 10),
  (320, 300, 'socialLink', '社交链接', 'M', 'SocialLink', '/home/social-links', 'home/social/index', 'link', 130, 20),
  (410, 400, 'experience', '简历经历', 'M', 'Experience', '/cv/experiences', 'cv/experience/index', 'document', 140, 10),
  (420, 400, 'skill', '简历技能', 'M', 'Skill', '/cv/skills', 'cv/skill/index', 'tag', 150, 20),
  (510, 500, 'statisticsOverview', '统计报表', 'M', 'StatisticsOverview', '/statistics/overview', 'statistics/overview/index', 'chart', 210, 10),
  (520, 500, 'visitor', '访客管理', 'M', 'Visitor', '/statistics/visitors', 'statistics/visitor/index', 'user', 190, 20),
  (530, 500, 'pageView', '浏览记录', 'M', 'PageView', '/statistics/views', 'statistics/view/index', 'document', 200, 30);

-- 每个后台操作生成对应按钮；前台评论/点赞/本人留言/订阅无需后台按钮。
INSERT INTO sys_menu (menu_id, parent_id, menu_code, menu_name, menu_type, permission_id, sort_order, is_visible)
SELECT 1000 + p.permission_id,
  CASE
    WHEN p.permission_id BETWEEN 11 AND 16 THEN 110
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
    WHEN p.permission_id = 121 THEN 310
    WHEN p.permission_id BETWEEN 131 AND 133 THEN 320
    WHEN p.permission_id BETWEEN 141 AND 143 THEN 410
    WHEN p.permission_id BETWEEN 151 AND 153 THEN 420
    WHEN p.permission_id BETWEEN 161 AND 162 THEN 170
    WHEN p.permission_id BETWEEN 171 AND 173 THEN 180
    WHEN p.permission_id BETWEEN 181 AND 182 THEN 190
    WHEN p.permission_id BETWEEN 191 AND 193 THEN 520
    WHEN p.permission_id = 201 THEN 530
    WHEN p.permission_id = 221 THEN 260
  END,
  p.permission_code, p.permission_name, 'B', p.permission_id, p.permission_id, 0
FROM sys_permission p
WHERE p.permission_id IN (11,12,13,14,15,16,21,22,23,31,32,33,41,42,51,52,53,61,62,71,81,82,83,91,92,93,94,101,102,103,111,112,113,
  121,131,132,133,141,142,143,151,152,153,161,162,171,172,173,181,182,191,192,193,201,221);

INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, menu_id FROM sys_menu;
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, m.menu_id FROM sys_menu m
WHERE m.menu_id = 100 OR EXISTS (
  SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = 2 AND rp.permission_id = m.permission_id
);

INSERT INTO blog_site_config (config_key, config_value, description, is_public) VALUES
  ('site', JSON_OBJECT('title', 'DriftingLeaves', 'description', '记录与分享', 'icp', '湘ICP备2026010056号', 'publicSecurityRecord', 'xxx', 'startDate', '2026-03-31'), '公开站点信息；备案及建站日期按提供的截图初始化', 1),
  ('comment', JSON_OBJECT('enabled', TRUE, 'reviewRequired', TRUE), '评论默认开启并审核；前端不得用本配置绕过服务端校验', 0),
  ('message', JSON_OBJECT('enabled', TRUE, 'reviewRequired', TRUE), '留言默认开启并审核；私密范围由服务端校验', 0);
COMMIT;
