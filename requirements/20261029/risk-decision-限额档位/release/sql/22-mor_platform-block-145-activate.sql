-- =====================================================================================================================
-- REQ-04 注册块 145 · 激活（risk-decision、operations-console-bff 与 operations-console-web 新镜像部署之后执行；执行后重新发布 Gateway 快照）
-- 管理员授权 4 行 REVOKED → ACTIVE，菜单与按钮 4 行、路由 11 行 DISABLED → ACTIVE；重复执行为空操作。由开发者审核后手工执行。
-- =====================================================================================================================
SET NAMES utf8mb4;
SET time_zone = '+00:00';

START TRANSACTION;
UPDATE t_role_permission SET status = 'ACTIVE', version = version + 1, update_time = UTC_TIMESTAMP(6)
WHERE role_permission_id BETWEEN 900000000000145201 AND 900000000000145204 AND status = 'REVOKED';
UPDATE t_console_resource SET status = 'ACTIVE', version = version + 1, update_time = UTC_TIMESTAMP(6)
WHERE resource_id BETWEEN 900000000000145301 AND 900000000000145304 AND status = 'DISABLED';
UPDATE t_gateway_route SET status = 'ACTIVE', version = version + 1, update_time = UTC_TIMESTAMP(6)
WHERE route_id BETWEEN 900000000000145401 AND 900000000000145411 AND status = 'DISABLED';
COMMIT;

SELECT role_permission_id, status, version FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000145201 AND 900000000000145204 ORDER BY role_permission_id;
SELECT resource_id, resource_code, status, version FROM t_console_resource WHERE resource_id BETWEEN 900000000000145301 AND 900000000000145304 ORDER BY resource_id;
SELECT route_id, route_code, status, version FROM t_gateway_route WHERE route_id BETWEEN 900000000000145401 AND 900000000000145411 ORDER BY route_id;
