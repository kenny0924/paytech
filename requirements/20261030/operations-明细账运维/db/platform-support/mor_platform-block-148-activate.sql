-- operations 明细账运维 · 注册块 148 · 激活（ledger、account、ops-bff 与 ops-web 新镜像之后执行）。
-- 管理员授权 REVOKED → ACTIVE、菜单与 2 个按钮 DISABLED → ACTIVE、4 条路由 DISABLED → ACTIVE、管理员角色 version + 1（让会话权限缓存刷新）。
-- 重复执行：前三条为空操作，角色 version 再 + 1（无副作用）。执行后 Gateway 快照自动刷新（routeCount +4）。
SET NAMES utf8mb4;
SET time_zone = '+00:00';

START TRANSACTION;
UPDATE t_role_permission SET status = 'ACTIVE', version = version + 1, update_time = UTC_TIMESTAMP(6) WHERE role_permission_id = 900000000000148201 AND status = 'REVOKED';
UPDATE t_console_resource SET status = 'ACTIVE', version = version + 1, update_time = UTC_TIMESTAMP(6) WHERE resource_id BETWEEN 900000000000148301 AND 900000000000148303 AND status = 'DISABLED';
UPDATE t_gateway_route SET status = 'ACTIVE', version = version + 1, update_time = UTC_TIMESTAMP(6) WHERE route_id BETWEEN 900000000000148401 AND 900000000000148404 AND status = 'DISABLED';
UPDATE t_role SET version = version + 1, update_time = UTC_TIMESTAMP(6) WHERE role_id = 900000000000000510;
COMMIT;

SELECT role_permission_id, status, version FROM t_role_permission WHERE role_permission_id = 900000000000148201;
SELECT resource_id, resource_code, status, version FROM t_console_resource WHERE resource_id BETWEEN 900000000000148301 AND 900000000000148303 ORDER BY resource_id;
SELECT route_id, route_code, status, version FROM t_gateway_route WHERE route_id BETWEEN 900000000000148401 AND 900000000000148404 ORDER BY route_id;
