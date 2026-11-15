-- 文件上传用途变更。图片 ID 映射来自 DriftingLeaves.sql，仅用于该导出对应的 V2 数据库。
-- DDL 会隐式提交；再次执行前用 SHOW CREATE TABLE blog_file 确认列是否已添加。
ALTER TABLE blog_file
  ADD COLUMN upload_usage VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ATTACHMENT'
    COMMENT '上传时指定的单一用途' AFTER mime_type,
  ADD CONSTRAINT ck_file_upload_usage CHECK (upload_usage IN (
    'ARTICLE_COVER', 'ARTICLE_IMAGE', 'MUSIC_COVER', 'AVATAR', 'LINK_AVATAR',
    'EXPERIENCE_LOGO', 'ICON', 'IMAGE', 'SONG', 'LYRIC', 'ATTACHMENT'
  ));

-- 部署新版上传逻辑前执行回填。条件保留已经设置的具体用途。
START TRANSACTION;
UPDATE blog_file
SET upload_usage = CASE
  WHEN mime_type IN ('image/png', 'image/jpeg') THEN 'IMAGE'
  WHEN mime_type IN ('audio/mpeg', 'audio/wav', 'audio/ogg', 'audio/flac') THEN 'SONG'
  ELSE 'LYRIC'
END
WHERE upload_usage = 'ATTACHMENT'
  AND (
    mime_type IN ('image/png', 'image/jpeg', 'audio/mpeg', 'audio/wav', 'audio/ogg', 'audio/flac')
    OR (mime_type = 'text/plain' AND SUBSTRING_INDEX(object_key, '.', -1) IN ('lrc', 'txt'))
    OR (mime_type = 'application/json' AND SUBSTRING_INDEX(object_key, '.', -1) = 'json')
  );

-- 按导出中已保存的引用标记 7 张图片。保留已设置的具体用途。
UPDATE blog_file
SET upload_usage = CASE file_id
  WHEN 10 THEN 'MUSIC_COVER'
  WHEN 13 THEN 'ARTICLE_COVER'
  WHEN 16 THEN 'AVATAR'
  WHEN 17 THEN 'EXPERIENCE_LOGO'
  WHEN 18 THEN 'EXPERIENCE_LOGO'
  WHEN 19 THEN 'AVATAR'
  WHEN 20 THEN 'ARTICLE_IMAGE'
END
WHERE file_id IN (10, 13, 16, 17, 18, 19, 20)
  AND upload_usage = 'IMAGE'
  AND mime_type IN ('image/png', 'image/jpeg');
COMMIT;

-- 数据量小，直接逐条核对。相同文件名仍按 file_id 区分。
SELECT file_id, original_name, mime_type, upload_usage
FROM blog_file
ORDER BY file_id;
