-- 已有 V2 数据库升级；先备份，在目标 V2 库手动执行一次，不重导初始化脚本。
-- 历史评论、登录地域保留 NULL；已经脱敏的历史 IP 无法还原。
ALTER TABLE sys_user
  ADD COLUMN last_login_ip VARCHAR(45) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '最近成功登录的完整IP，仅管理端可见' AFTER last_login_time,
  ADD COLUMN last_login_location VARCHAR(192) DEFAULT NULL COMMENT '最近成功登录的省市或国家' AFTER last_login_ip;

ALTER TABLE blog_comment
  ADD COLUMN location VARCHAR(192) DEFAULT NULL COMMENT '发布时的省市或国家，不含完整IP' AFTER content,
  ADD COLUMN user_agent_os VARCHAR(64) DEFAULT NULL COMMENT '发布时的操作系统类型' AFTER location,
  ADD COLUMN user_agent_browser VARCHAR(64) DEFAULT NULL COMMENT '发布时的浏览器类型' AFTER user_agent_os;
