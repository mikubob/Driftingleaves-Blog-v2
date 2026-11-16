-- 为已初始化的数据库增加友链申请字段和额度表。空库直接使用初始化脚本。
-- DDL 会隐式提交，重复执行前先检查列和表是否已创建。
ALTER TABLE blog_friend_link
  ADD COLUMN applicant_id BIGINT DEFAULT NULL COMMENT '本人申请的账号ID，后台直接创建时为空' AFTER link_id,
  ADD UNIQUE KEY uk_friend_link_applicant (applicant_id),
  ADD CONSTRAINT fk_friend_link_applicant FOREIGN KEY (applicant_id) REFERENCES sys_user (user_id) ON DELETE SET NULL;

CREATE TABLE blog_friend_link_quota (
  user_id BIGINT NOT NULL COMMENT '提交友链申请的账号ID',
  submission_date DATE NOT NULL COMMENT '最近一次成功提交的上海自然日',
  submission_count TINYINT NOT NULL COMMENT '该日成功提交次数',
  PRIMARY KEY (user_id),
  CONSTRAINT fk_friend_link_quota_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE CASCADE,
  CONSTRAINT ck_friend_link_quota_count CHECK (submission_count BETWEEN 1 AND 3)
) ENGINE=InnoDB COMMENT='友链申请次数，删除友链不返还次数';
