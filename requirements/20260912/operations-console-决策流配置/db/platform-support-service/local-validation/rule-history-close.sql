-- 恢复额外的旧规则集历史 GET 入口。
START TRANSACTION;
UPDATE t_gateway_route SET status='DISABLED',version=version+1,update_time=UTC_TIMESTAMP(6) WHERE route_id=900000000000012001 AND route_code='operations-risk-rule-set-versions' AND status='ACTIVE' AND version=2;
SELECT ROW_COUNT() AS changed_rows; -- 必须为 1，然后在同一连接 COMMIT；否则 ROLLBACK。
