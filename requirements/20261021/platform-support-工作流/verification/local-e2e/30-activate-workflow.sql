-- 工作流阶段 5：本机 e2e 激活（只在本机 mor_platform_v2e2e 执行，客户端须 utf8mb4；不用于 mor-test 或其他环境）。
-- 执行顺序（同一库）：
--   1. paytech/platform-support/docs/database/sql/changes/2026-10-22-workflow.sql          10 张表
--   2. paytech/platform-support/docs/database/sql/changes/2026-10-22-workflow-comment.sql  两列注释
--   3. platform-support-initial-data.sql 中「Operations workflow console」独立块（块 127，364 行，休眠）
--   4. 本文件：开启 28 条路由、10 个菜单组/菜单/按钮、PLATFORM_ADMIN 的 7 个授权，并递增角色版本使会话重新计算授权。
-- 预期影响行数：28 / 10 / 7 / 1；不符时 ROLLBACK。
-- 回滚：同一组 ID 反向置 DISABLED / REVOKED，version+1，再递增角色 900000000000000510 的 version；
--       整体回滚为按块 127 文件头的顺序删除 900000000000127001-900000000000127999 并 DROP 10 张表，或用执行前备份恢复。
START TRANSACTION;
SET @wf_e2e_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@wf_e2e_time
 WHERE DATABASE()='mor_platform_v2e2e' AND route_id BETWEEN 900000000000127401 AND 900000000000127428 AND status='DISABLED';
SELECT ROW_COUNT() AS routes_28;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@wf_e2e_time
 WHERE DATABASE()='mor_platform_v2e2e' AND resource_id BETWEEN 900000000000127301 AND 900000000000127310 AND status='DISABLED';
SELECT ROW_COUNT() AS resources_10;
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@wf_e2e_time
 WHERE DATABASE()='mor_platform_v2e2e' AND role_permission_id BETWEEN 900000000000127201 AND 900000000000127207 AND status='REVOKED';
SELECT ROW_COUNT() AS grants_7;
UPDATE t_role SET version=version+1, update_time=@wf_e2e_time
 WHERE DATABASE()='mor_platform_v2e2e' AND role_id=900000000000000510;
SELECT ROW_COUNT() AS role_1;
-- 核对四个数后 COMMIT，否则 ROLLBACK。
