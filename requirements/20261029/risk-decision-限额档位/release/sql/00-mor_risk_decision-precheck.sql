-- REQ-04 · mor_risk_decision 发布前只读预检（runbook ③）。在 READ ONLY 会话执行。
SET NAMES utf8mb4;
-- 1) 三张新表不存在：期望 0
SELECT COUNT(*) AS `new_tables` FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('t_client_limit_tier', 't_client_limit_tier_change', 't_client_allowed_daily_total');
-- 2) 现有表数（阶段 6 读数 57，发布后 60）
SELECT COUNT(*) AS `tables_before` FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE';
-- 3) 名单 ID 300001—300004 与编码 TIER_* 未占用；没有 TIER 用途名单：期望 0 / 0 / 0
SELECT COUNT(*) AS `list_ids_taken` FROM t_risk_list WHERE list_id BETWEEN 300001 AND 300004;
SELECT COUNT(*) AS `tier_codes_taken` FROM t_risk_list WHERE list_code IN ('TIER_VERIFICATION', 'TIER_OBSERVATION', 'TIER_STANDARD', 'TIER_RESTRICTED');
SELECT COUNT(*) AS `tier_purpose_lists` FROM t_risk_list WHERE list_purpose = 'TIER';
-- 4) 同名索引 / 过程不存在：期望 0 / 0
SELECT COUNT(*) AS `same_name_indexes` FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
  AND (INDEX_NAME LIKE 'uk\_client\_limit\_tier%' OR INDEX_NAME LIKE 'idx\_client\_limit\_tier%' OR INDEX_NAME LIKE 'uk\_client\_allowed%');
SELECT COUNT(*) AS `rollback_proc` FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = DATABASE()
  AND ROUTINE_NAME = 'rq04_client_limit_tier_rollback';
-- 5) 五处被改注释的列现状（类型与可空不变，只改注释）
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
  AND ((TABLE_NAME = 't_velocity_feature_definition' AND COLUMN_NAME IN ('metric', 'window_seconds', 'event_code'))
    OR (TABLE_NAME = 't_risk_list' AND COLUMN_NAME = 'list_purpose') OR (TABLE_NAME = 't_risk_list_entry' AND COLUMN_NAME = 'source_type'))
  ORDER BY TABLE_NAME, COLUMN_NAME;
-- 6) 没有使用新代码值的旧数据：期望 0 / 0
SELECT COUNT(*) AS `daily_sum_defs` FROM t_velocity_feature_definition WHERE metric = 'DAILY_SUM_AMOUNT';
SELECT COUNT(*) AS `allowed_events` FROM t_velocity_event WHERE event_code IN ('PAYMENT_ATTEMPT_ALLOWED', 'CHECKOUT_ALLOWED');
-- 7) 字符集 / 排序规则
SELECT DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = DATABASE();
-- 8) 应用账号库级 DML：在 mor-test 另行执行 SHOW GRANTS FOR 'risk_decision_app'@'%'（不放在本文件内，以便离线演练库也能执行）。
