-- 联合查询注册块 144 · 停用（激活的逆操作；回滚时先于 -rollback.sql 执行，使快照先撤下 8 条路由与 2 个菜单）。重复执行为空操作。
SET NAMES utf8mb4;
SET time_zone = '+00:00';
START TRANSACTION;
UPDATE t_gateway_route SET status = 'DISABLED', version = version + 1, update_time = UTC_TIMESTAMP(6)
WHERE route_id BETWEEN 900000000000144401 AND 900000000000144408 AND status = 'ACTIVE';
UPDATE t_console_resource SET status = 'DISABLED', version = version + 1, update_time = UTC_TIMESTAMP(6)
WHERE resource_id IN (900000000000144301, 900000000000144302) AND status = 'ACTIVE';
COMMIT;
SELECT route_id, route_code, status, version FROM t_gateway_route WHERE route_id BETWEEN 900000000000144401 AND 900000000000144408 ORDER BY route_id;
