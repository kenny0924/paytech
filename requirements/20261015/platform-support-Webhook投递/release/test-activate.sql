-- Client Webhook：mor-test 启用（阶段 7，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 前置：已执行 changes/2026-10-15-webhook-delivery.sql 与初始化 DML 末尾「Client Webhook management permissions」独立块并 COMMIT。
-- 启用 PLATFORM_ADMIN 的 2 个 Webhook 授权，并递增角色版本使会话重新计算授权。没有 Gateway 路由或菜单（页面另立需求）。
-- 预期影响行数：2 / 1。仅 mor-test；其他环境另行授权。
START TRANSACTION;
SET @whk_act_time = UTC_TIMESTAMP(6);
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@whk_act_time
 WHERE role_permission_id IN (900000000000090201, 900000000000090202) AND status='REVOKED';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@whk_act_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
