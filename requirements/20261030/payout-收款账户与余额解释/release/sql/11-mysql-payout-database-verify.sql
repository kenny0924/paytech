-- payout S1 · 建库与账号执行后核对（root 只读会话）。
-- 期望：mor_payout utf8mb4 / utf8mb4_0900_ai_ci；payout_app@% 未锁定；授权恰好两行
--   GRANT USAGE ON *.* … 与 GRANT SELECT, INSERT, UPDATE, DELETE ON `mor_payout`.* …；mor_payout 内 0 张表（12 之前）或 3 张表（12 之后）。
SELECT schema_name, default_character_set_name, default_collation_name FROM information_schema.schemata WHERE schema_name = 'mor_payout';
SELECT user, host, plugin, account_locked FROM mysql.user WHERE user = 'payout_app';
SHOW GRANTS FOR 'payout_app'@'%';
SELECT COUNT(*) AS `tables_in_mor_payout` FROM information_schema.tables WHERE table_schema = 'mor_payout';
