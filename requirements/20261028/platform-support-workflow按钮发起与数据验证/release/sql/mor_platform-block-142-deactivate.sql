-- REQ-05 注册块 142 · 停用（回滚第一步，或只撤下三个按钮而保留新代码时使用）：ACTIVE → DISABLED；重复执行为空操作。
SET NAMES utf8mb4;
SET time_zone = '+00:00';
START TRANSACTION;
UPDATE t_gateway_route SET status = 'DISABLED', version = version + 1, update_time = UTC_TIMESTAMP(6)
WHERE route_id IN (900000000000142401, 900000000000142402, 900000000000142403) AND status = 'ACTIVE';
COMMIT;
SELECT route_id, route_code, status, version FROM t_gateway_route
WHERE route_id IN (900000000000142401, 900000000000142402, 900000000000142403) ORDER BY route_id;
