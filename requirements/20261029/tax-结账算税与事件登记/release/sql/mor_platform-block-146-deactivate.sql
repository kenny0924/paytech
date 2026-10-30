-- tax 结账算税与事件登记 · 注册块 146 · 停用（回滚第一步，激活的逆操作；runbook R5）。重复执行为空操作。
-- 先回退 operations-console-web（菜单入口）再执行；执行后 Gateway 快照自动刷新（routeCount -2）。文件写入路由不在此停用（无状态列，见 rollback）。
SET NAMES utf8mb4;
SET time_zone = '+00:00';

START TRANSACTION;
UPDATE t_gateway_route SET status = 'DISABLED', version = version + 1, update_time = UTC_TIMESTAMP(6) WHERE route_id BETWEEN 900000000000146401 AND 900000000000146402 AND status = 'ACTIVE';
UPDATE t_console_resource SET status = 'DISABLED', version = version + 1, update_time = UTC_TIMESTAMP(6) WHERE resource_id = 900000000000146301 AND status = 'ACTIVE';
UPDATE t_role_permission SET status = 'REVOKED', version = version + 1, update_time = UTC_TIMESTAMP(6) WHERE role_permission_id = 900000000000146201 AND status = 'ACTIVE';
UPDATE t_role SET version = version + 1, update_time = UTC_TIMESTAMP(6) WHERE role_id = 900000000000000510;
COMMIT;
