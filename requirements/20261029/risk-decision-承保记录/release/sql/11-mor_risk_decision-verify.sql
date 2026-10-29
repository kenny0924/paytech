-- REQ-03 · mor_risk_decision 前滚后核对（runbook ①；READ ONLY 会话可执行）。
SET NAMES utf8mb4;
-- 期望 3
SELECT COUNT(*) AS `new_tables` FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('t_client_underwriting', 't_underwriting_rule_version', 't_underwriting_rule_threshold');
-- 期望 3 个唯一键 + 2 个普通索引（t_client_underwriting）
SELECT INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS `columns` FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_client_underwriting' GROUP BY INDEX_NAME, NON_UNIQUE ORDER BY INDEX_NAME;
-- 期望 58
SELECT COUNT(*) AS `underwriting_columns` FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_client_underwriting';
-- 期望 290001 / 1 / ACTIVE / 1 与 290101 / USD / 50000.00000000
SELECT rule_version_id, version_no, status, active_slot, excluded_mcc_codes FROM t_underwriting_rule_version;
SELECT threshold_id, rule_version_id, currency, monthly_volume_limit FROM t_underwriting_rule_threshold;
-- 发布时为 0；验收后为验收产生的行数
SELECT status, COUNT(*) AS `n` FROM t_client_underwriting GROUP BY status;
