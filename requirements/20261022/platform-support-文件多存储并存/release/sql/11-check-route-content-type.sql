-- 文件多存储并存 · 块 130 修正：桶检查路由 130407 补登记 content-type（阶段 7 发现，2026-10-22，只在 mor-test mor_platform 执行，客户端 utf8mb4）
-- 原因：operations-console-bff 对所有写方法要求 JSON Content-Type（无请求体也一样），块 130 原稿让桶检查路由不登记 content-type，
--   Gateway 不转发该头，BFF 返回 415 OPERATIONS_UNSUPPORTED_MEDIA_TYPE。做法同 Webhook 测试路由 900000000000090407（正文上限 0，登记 content-type）。
-- 初始化 DML（platform-support-initial-data.sql 块 130）已同步加入这两行，块 130 共 154 行。
-- 影响：t_gateway_route_header 插入 2 行；t_gateway_route 130407 版本 +1，促使 Gateway Snapshot 重新编译该路由。
-- 回滚：DELETE FROM t_gateway_route_header WHERE route_header_id IN (900000000000130563, 900000000000130564);
--       UPDATE t_gateway_route SET version=version+1, update_time=UTC_TIMESTAMP(6) WHERE route_id=900000000000130407;
START TRANSACTION;
SET @file_storage_fix_time = UTC_TIMESTAMP(6);
INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000130563, 900000000000130407, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000130564, 900000000000130407, 'content-type', 'FORWARD_REQUEST');
SELECT ROW_COUNT() AS headers;
UPDATE t_gateway_route SET version=version+1, update_time=@file_storage_fix_time WHERE route_id=900000000000130407;
SELECT ROW_COUNT() AS route;
COMMIT;
