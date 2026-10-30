-- =====================================================================================================================
-- 联合查询注册块 144 · 激活（Platform 与 operations-console-bff 新镜像部署之后执行；执行后重新发布 Gateway 快照）
-- 把 8 条路由与 2 个菜单由 DISABLED 改为 ACTIVE；键类型本就是 ACTIVE。重复执行为空操作。由开发者审核后手工执行。
-- =====================================================================================================================
SET NAMES utf8mb4;
SET time_zone = '+00:00';

START TRANSACTION;
UPDATE t_gateway_route SET status = 'ACTIVE', version = version + 1, update_time = UTC_TIMESTAMP(6)
WHERE route_id BETWEEN 900000000000144401 AND 900000000000144408 AND status = 'DISABLED';
UPDATE t_console_resource SET status = 'ACTIVE', version = version + 1, update_time = UTC_TIMESTAMP(6)
WHERE resource_id IN (900000000000144301, 900000000000144302) AND status = 'DISABLED';
COMMIT;

SELECT route_id, route_code, status, version FROM t_gateway_route
WHERE route_id BETWEEN 900000000000144401 AND 900000000000144408 ORDER BY route_id;
SELECT resource_id, resource_code, status, version FROM t_console_resource
WHERE resource_id IN (900000000000144301, 900000000000144302) ORDER BY resource_id;
