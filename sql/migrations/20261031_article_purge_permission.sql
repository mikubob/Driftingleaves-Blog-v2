-- 已使用 V2 基线的库只执行此增量；不重建表，不清理业务数据。
-- 在确认目标为 V2 库后执行。显式给内置管理员角色授权，不授予作者或会员。
USE drifting_leaves_v2;
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO sys_permission (permission_code, permission_name, module)
SELECT 'blog:article:purge', '永久删除回收站文章（无法恢复）', 'blog'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='blog:article:purge');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1,p.permission_id FROM sys_permission p WHERE p.permission_code='blog:article:purge'
AND NOT EXISTS (SELECT 1 FROM sys_role_permission r WHERE r.role_id=1 AND r.permission_id=p.permission_id);

INSERT INTO sys_menu (parent_id, menu_code, menu_name, menu_type, permission_id, sort_order, is_visible)
SELECT m.menu_id,p.permission_code,p.permission_name,'B',p.permission_id,16,0
FROM sys_menu m JOIN sys_permission p ON p.permission_code='blog:article:purge'
WHERE m.menu_code='article' AND NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.menu_code='blog:article:purge');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1,m.menu_id FROM sys_menu m WHERE m.menu_code='blog:article:purge'
AND NOT EXISTS (SELECT 1 FROM sys_role_menu r WHERE r.role_id=1 AND r.menu_id=m.menu_id);

COMMIT;
