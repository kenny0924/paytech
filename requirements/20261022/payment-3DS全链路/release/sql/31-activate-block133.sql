-- 渠道支付通知入口（块 133）mor-test 激活。目标库 mor_platform。语句取自 23-mor_platform-registration-block133.sql 文件头 Activation 段。
-- 期望影响 1 行。前置：23 已执行；channel-connector 与 api-gateway 新镜像 healthy（旧 Gateway 没有 CHANNEL_CALLBACK 安全链，请求落到 denyAll）。
-- 本文件不含 COMMIT：apply_sql.sh <备份目录> activate133-dry / activate133。回滚：90-rollback-deactivate.sql 的块 133 段。
START TRANSACTION;
UPDATE t_gateway_route SET status = 'ACTIVE', version = version + 1, update_time = UTC_TIMESTAMP(6)
 WHERE DATABASE()='mor_platform' AND route_id = 900000000000133401 AND status = 'DISABLED';
SET @cb_routes = ROW_COUNT();
SELECT @cb_routes AS routes_1;
SELECT IF(@cb_routes=1, 'counts ok', (SELECT 'mismatch' UNION ALL SELECT 'abort')) AS guard;
