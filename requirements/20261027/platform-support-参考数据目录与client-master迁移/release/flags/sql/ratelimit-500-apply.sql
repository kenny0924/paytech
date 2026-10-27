-- 国旗批量上传期间临时放宽运营公开上传两条路由的限流（用户 2026-10-27 授权：每分钟 500 次）。只在 mor_platform 执行。
-- 原值：AUTHENTICATED_PRINCIPAL 10/60s、CLIENT_IP 30/60s、SHARED_ROUTE 300/60s；并发规则不变。回退见 ratelimit-500-rollback.sql。
SET NAMES utf8mb4;
START TRANSACTION;
UPDATE t_gateway_guard_rule SET capacity = 500, refill_tokens = 500
 WHERE guard_rule_id IN (900000000000136719, 900000000000136721, 900000000000136723, 900000000000136725, 900000000000136727, 900000000000136729)
   AND rule_type = 'RATE_LIMIT' AND refill_period_millis = 60000;
SELECT ROW_COUNT() AS updated_guards;
UPDATE t_gateway_route SET version = version + 1, update_time = UTC_TIMESTAMP(6) WHERE route_id IN (900000000000136404, 900000000000136405);
COMMIT;
