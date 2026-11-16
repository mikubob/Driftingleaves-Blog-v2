-- 已有 V2 数据库先具备申请人字段，再执行一次本脚本。
ALTER TABLE blog_friend_link
  ADD COLUMN source VARCHAR(11) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'UNKNOWN'
    COMMENT '来源：APPLICATION博客申请、ADMIN后台创建、UNKNOWN历史来源不明' AFTER applicant_id,
  ADD CONSTRAINT ck_friend_link_source CHECK (source IN ('APPLICATION', 'ADMIN', 'UNKNOWN'));

-- 仅回填仍保留账号关联的申请，历史来源不明的记录和已有时间均保留。
UPDATE blog_friend_link
SET source = 'APPLICATION', update_time = update_time
WHERE applicant_id IS NOT NULL AND source = 'UNKNOWN';
