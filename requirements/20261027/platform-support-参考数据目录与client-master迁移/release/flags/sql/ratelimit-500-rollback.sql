-- 恢复运营公开上传两条路由的原限流值。只在 mor_platform 执行。
SET NAMES utf8mb4;
START TRANSACTION;
UPDATE t_gateway_guard_rule SET capacity = 10, refill_tokens = 10 WHERE guard_rule_id IN (900000000000136723, 900000000000136729);
UPDATE t_gateway_guard_rule SET capacity = 30, refill_tokens = 30 WHERE guard_rule_id IN (900000000000136721, 900000000000136727);
UPDATE t_gateway_guard_rule SET capacity = 300, refill_tokens = 300 WHERE guard_rule_id IN (900000000000136719, 900000000000136725);
UPDATE t_gateway_route SET version = version + 1, update_time = UTC_TIMESTAMP(6) WHERE route_id IN (900000000000136404, 900000000000136405);
COMMIT;
