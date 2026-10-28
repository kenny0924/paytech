-- 块 140 停用（启用的逆操作），只在 mor_platform 执行，客户端须 utf8mb4。注册行保留。
-- 预期影响：routes 1；Gateway 快照 routeCount −1。
START TRANSACTION;
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=UTC_TIMESTAMP(6)
 WHERE route_id = 900000000000140401 AND status='ACTIVE';
SELECT ROW_COUNT() AS routes;
COMMIT;
