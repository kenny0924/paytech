-- Operations 通知管理：mor-test 激活（阶段 7，只在 test mor_platform 执行，客户端须 utf8mb4）
-- 前置：已执行 changes/2026-10-15-notification-request-create-time-index.sql 与初始化 DML 末尾的通知管理独立块并 COMMIT。
-- 激活 12 条路由、6 个菜单/按钮资源、PLATFORM_ADMIN 的 2 个授权，并递增角色版本使会话重新计算授权。
-- 预期影响行数：12 / 6 / 2 / 1。本机与其他环境不沿用本文件；先核对行数再 COMMIT。
START TRANSACTION;
SET @ntf_test_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@ntf_test_time
 WHERE route_id BETWEEN 900000000000086001 AND 900000000000086012 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@ntf_test_time
 WHERE resource_id BETWEEN 900000000000086301 AND 900000000000086306 AND status='DISABLED';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@ntf_test_time
 WHERE role_permission_id BETWEEN 900000000000086201 AND 900000000000086202 AND status='REVOKED';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@ntf_test_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
-- 回滚：同一组 ID 反向置 DISABLED / REVOKED，version+1，并再次递增角色 900000000000000510 的 version。
