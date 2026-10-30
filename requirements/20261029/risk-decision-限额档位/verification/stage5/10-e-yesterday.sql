-- REQ-04 阶段 5 本机联调测试数据（只用于 mor_risk_decision_v2e2e）：Client E 240674482170114049 昨日（UTC）PAYMENT_ATTEMPT_ALLOWED USD 300.00 / 6 笔，
-- 用于验证近 30 个 UTC 自然日额度（昨日计入 30 日、不计入当日）。代替"等待一天"的拨时数据，与自动测试同义。回滚：10-e-yesterday-rollback.sql。
SET time_zone = '+00:00';
INSERT INTO t_client_allowed_daily_total (daily_total_id, client_id, event_code, currency, event_date, amount, event_count, create_time, update_time)
VALUES (990000000000145001, 240674482170114049, 'PAYMENT_ATTEMPT_ALLOWED', 'USD', DATE_SUB(UTC_DATE(), INTERVAL 1 DAY), 300.00, 6,
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
