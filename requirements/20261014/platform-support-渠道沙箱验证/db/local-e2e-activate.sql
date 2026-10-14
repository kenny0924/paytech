-- 渠道沙箱验证：在本机独立库 mor_platform_external_api 启用 14 条验证路由（阶段 5 本机端到端用）。
-- 前置：外部 API local-e2e/activate.sql 已执行（PLATFORM_ADMIN 已持有 EXTERNAL_API_READ / MANAGE）。
-- 只在 mor_platform_external_api 执行；同一连接、不用 --force。核对 14 后 COMMIT，否则 ROLLBACK。
START TRANSACTION;
SET @plt_verify_activation_time=UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE',version=version+1,update_time=@plt_verify_activation_time
 WHERE DATABASE()='mor_platform_external_api' AND route_id BETWEEN 900000000000071031 AND 900000000000071044
   AND channel='OPERATIONS' AND status='DISABLED' AND version=1;
SELECT ROW_COUNT() routes_14;
-- 结果为 14 时 COMMIT，否则 ROLLBACK。
