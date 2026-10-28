-- 块 140 启用（card：BIN 库页面重设计与号段列表），只在 mor_platform 执行，客户端须 utf8mb4。
-- 前置：10-platform-block140-registration.sql 已提交；card-service、operations-console-bff 候选镜像 healthy（ops-web 可在其后）。
-- 预期影响：routes 1。Gateway 约 5 秒内自动刷新快照，routeCount +1（2026-10-27 预检时 612 → 613）。
START TRANSACTION;
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=UTC_TIMESTAMP(6)
 WHERE route_id = 900000000000140401 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
COMMIT;
