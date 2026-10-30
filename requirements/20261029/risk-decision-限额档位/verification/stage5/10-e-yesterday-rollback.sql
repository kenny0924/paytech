-- 回滚 10-e-yesterday.sql（只删本测试数据行）。
DELETE FROM t_client_allowed_daily_total WHERE daily_total_id = 990000000000145001;
