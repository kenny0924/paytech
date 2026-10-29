-- REQ-03 · mor_risk_decision 发布前只读预检（runbook ①）。在 READ ONLY 会话执行（SET SESSION TRANSACTION READ ONLY）。
SET NAMES utf8mb4;
-- 1) 三张新表不存在：期望 0
SELECT COUNT(*) AS `new_tables` FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('t_client_underwriting', 't_underwriting_rule_version', 't_underwriting_rule_threshold');
-- 2) 现有表数（发布后 +3）
SELECT COUNT(*) AS `tables_before` FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE';
-- 3) 同名索引 / 过程不存在：期望 0 / 0
SELECT COUNT(*) AS `same_name_indexes` FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
  AND (INDEX_NAME LIKE 'uk\_client\_underwriting%' OR INDEX_NAME LIKE 'idx\_client\_underwriting%' OR INDEX_NAME LIKE 'uk\_uw\_rule%');
SELECT COUNT(*) AS `rollback_proc` FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = DATABASE()
  AND ROUTINE_NAME = 'rq03_client_underwriting_rollback';
-- 4) 字符集 / 排序规则（新表沿用库默认）
SELECT DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = DATABASE();
-- 5) 应用账号库级 DML 覆盖新表：在 mor-test 另行执行 SHOW GRANTS FOR 'risk_decision_app'@'%'（阶段 6 读数：SELECT, INSERT, UPDATE, DELETE ON mor_risk_decision.*）。
--    不放在本文件内，以便离线演练库（无账号）也能执行本预检。
