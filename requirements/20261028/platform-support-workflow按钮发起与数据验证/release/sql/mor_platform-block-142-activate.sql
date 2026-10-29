-- =====================================================================================================================
-- REQ-05 注册块 142 · 激活（Platform 与 operations-console-bff 新镜像部署之后执行；执行后重新发布 Gateway 快照）
-- 只把三条路由由 DISABLED 改为 ACTIVE；重复执行为空操作。由开发者审核后手工执行。
-- =====================================================================================================================
SET NAMES utf8mb4;
SET time_zone = '+00:00';

START TRANSACTION;
UPDATE t_gateway_route SET status = 'ACTIVE', version = version + 1, update_time = UTC_TIMESTAMP(6)
WHERE route_id IN (900000000000142401, 900000000000142402, 900000000000142403) AND status = 'DISABLED';
COMMIT;

SELECT route_id, route_code, status, version FROM t_gateway_route
WHERE route_id IN (900000000000142401, 900000000000142402, 900000000000142403) ORDER BY route_id;
