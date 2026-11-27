-- 停止后端后，在已有 V2 数据库执行一次。先选中目标库。
-- 退订记录保留历史邮箱，只有待验证和已订阅记录占用地址。
ALTER TABLE blog_rss_subscription
  DROP INDEX uk_rss_email,
  ADD UNIQUE KEY uk_rss_email ((CASE WHEN status IN (0, 1) THEN email END));

-- 停用账号邮箱已变化的旧订阅，已有退订时间照常保留。
UPDATE blog_rss_subscription s
JOIN sys_user u ON u.user_id = s.user_id
SET s.status = 2,
    s.verified_at = NULL,
    s.unsubscribe_time = COALESCE(s.unsubscribe_time, UTC_TIMESTAMP(3))
WHERE NOT (CAST(s.email AS BINARY) <=> CAST(LOWER(TRIM(u.email)) AS BINARY))
  AND (s.status <> 2 OR s.verified_at IS NOT NULL);
