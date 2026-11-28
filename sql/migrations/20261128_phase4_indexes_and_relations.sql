-- DriftingLeavesV2 Phase 4 增量迁移脚本（MySQL 8.0.16+）
-- 适用对象：已初始化且尚无文章/评论/留言存量数据的 V2 数据库（空库直接导入 ../drifting_leaves_v2.sql）。
-- 执行方式：在目标库（如 USE driftingleaves;）中执行一次即可。

SET NAMES utf8mb4;

-- 1. 新增文章引用站内文件关联表（BE-BIZ-02）
CREATE TABLE blog_article_file (
  article_id BIGINT NOT NULL COMMENT '文章ID，关联blog_article.article_id',
  file_id BIGINT NOT NULL COMMENT '文件ID，关联blog_file.file_id',
  PRIMARY KEY (article_id, file_id),
  KEY idx_article_file_file (file_id, article_id),
  CONSTRAINT fk_article_file_article FOREIGN KEY (article_id) REFERENCES blog_article (article_id) ON DELETE CASCADE,
  CONSTRAINT fk_article_file_file FOREIGN KEY (file_id) REFERENCES blog_file (file_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='文章正文与封面引用的站内文件；删除文章时级联清理，删除文件时由引用检查及外键保护';

-- 2. 为评论表增加会话根评论 ID、索引与约束（与留言表保持一致）
ALTER TABLE blog_comment
  ADD COLUMN root_id BIGINT DEFAULT NULL
    COMMENT '所属会话根评论ID；根评论为NULL，回复指向同文章顶层根评论ID' AFTER parent_id,
  ADD KEY idx_comment_root (article_id, root_id, create_time, comment_id),
  ADD CONSTRAINT fk_comment_root FOREIGN KEY (root_id, article_id) REFERENCES blog_comment (comment_id, article_id) ON DELETE RESTRICT,
  ADD CONSTRAINT ck_comment_root CHECK ((parent_id IS NULL AND root_id IS NULL) OR (parent_id IS NOT NULL AND root_id IS NOT NULL));

-- 3. 为留言表增加会话根留言 ID、索引与约束（BE-BIZ-05）
ALTER TABLE blog_message
  ADD COLUMN root_id BIGINT DEFAULT NULL
    COMMENT '所属会话根留言ID；根留言为NULL，回复指向顶层根留言ID' AFTER parent_id,
  ADD KEY idx_message_root (root_id, create_time, message_id),
  ADD CONSTRAINT fk_message_root FOREIGN KEY (root_id) REFERENCES blog_message (message_id) ON DELETE RESTRICT,
  ADD CONSTRAINT ck_message_root CHECK ((parent_id IS NULL AND root_id IS NULL) OR (parent_id IS NOT NULL AND root_id IS NOT NULL));

-- 4. 为文章表补充后台更新时间排序索引（BE-BIZ-04）
ALTER TABLE blog_article
  ADD KEY idx_article_admin_update (deleted_at, is_top DESC, update_time DESC, article_id DESC);

-- 5. 为浏览明细表补充站点统计与文章排行覆盖索引（BE-BIZ-04）
ALTER TABLE blog_page_view
  ADD KEY idx_view_site_time_visitor (site_code, view_time, visitor_id),
  ADD KEY idx_view_site_time_article (site_code, view_time, article_id);

-- 回滚参考（如需退回旧版后端时手工执行）：
-- ALTER TABLE blog_page_view DROP INDEX idx_view_site_time_article, DROP INDEX idx_view_site_time_visitor;
-- ALTER TABLE blog_article DROP INDEX idx_article_admin_update;
-- ALTER TABLE blog_message DROP CHECK ck_message_root, DROP FOREIGN KEY fk_message_root, DROP INDEX idx_message_root, DROP COLUMN root_id;
-- ALTER TABLE blog_comment DROP CHECK ck_comment_root, DROP FOREIGN KEY fk_comment_root, DROP INDEX idx_comment_root, DROP COLUMN root_id;
-- DROP TABLE IF EXISTS blog_article_file;
