-- 块 141 启用（commercial：开通项前置门禁 REQ-01），只在 mor_platform 执行，客户端须 utf8mb4。
-- 前置：mor_platform-block-141.sql 已提交且休眠快照编译通过；commercial-service、operations-console-bff 候选镜像 healthy（ops-web 可在其后）。
-- 预期影响：routes 3。Gateway 约 5 秒内自动刷新快照，routeCount +3（阶段 6 预检 ACTIVE 613 → 616）。
START TRANSACTION;
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=UTC_TIMESTAMP(6)
 WHERE route_id IN (900000000000141401, 900000000000141402, 900000000000141403) AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
COMMIT;
