-- 已有 V2 数据库升级；先备份，在目标 V2 库手动执行一次，不重导初始化脚本。
-- 历史留言没有保存发布时的 IP 和 User-Agent，新增字段保留 NULL。
ALTER TABLE blog_message
  ADD COLUMN location VARCHAR(192) DEFAULT NULL COMMENT '发布时的省市或国家，不含完整IP' AFTER content,
  ADD COLUMN user_agent_os VARCHAR(64) DEFAULT NULL COMMENT '发布时的操作系统类型' AFTER location,
  ADD COLUMN user_agent_browser VARCHAR(64) DEFAULT NULL COMMENT '发布时的浏览器类型' AFTER user_agent_os;
