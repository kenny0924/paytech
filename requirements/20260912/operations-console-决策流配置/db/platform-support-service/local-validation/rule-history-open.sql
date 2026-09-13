-- 追加只读依赖，尚未执行；需纳入本机单独授权。
START TRANSACTION;
UPDATE t_gateway_route SET status='ACTIVE',version=version+1,update_time=UTC_TIMESTAMP(6) WHERE route_id=900000000000012001 AND route_code='operations-risk-rule-set-versions' AND status='DISABLED' AND version=1;
SELECT ROW_COUNT() AS changed_rows; -- 必须为 1，然后在同一连接 COMMIT；否则 ROLLBACK。
