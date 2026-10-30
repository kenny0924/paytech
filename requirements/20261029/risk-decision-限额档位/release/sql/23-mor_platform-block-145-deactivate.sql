-- REQ-04 · 注册块 145 · 停用（回滚第一步：激活的逆操作；之后可保留休眠，或再执行 92 删除）。重复执行为空操作。
-- 先回退 operations-console-web（菜单入口）再执行；执行后 Gateway 快照自动刷新（路由数 -11）。
SET NAMES utf8mb4;
SET time_zone = '+00:00';

START TRANSACTION;
UPDATE t_gateway_route SET status = 'DISABLED', version = version + 1, update_time = UTC_TIMESTAMP(6)
WHERE route_id BETWEEN 900000000000145401 AND 900000000000145411 AND status = 'ACTIVE';
UPDATE t_console_resource SET status = 'DISABLED', version = version + 1, update_time = UTC_TIMESTAMP(6)
WHERE resource_id BETWEEN 900000000000145301 AND 900000000000145304 AND status = 'ACTIVE';
UPDATE t_role_permission SET status = 'REVOKED', version = version + 1, update_time = UTC_TIMESTAMP(6)
WHERE role_permission_id BETWEEN 900000000000145201 AND 900000000000145204 AND status = 'ACTIVE';
COMMIT;
