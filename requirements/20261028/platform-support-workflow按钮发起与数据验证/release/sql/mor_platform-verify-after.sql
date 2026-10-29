-- REQ-05 · 前滚 DDL 后只读核对（runbook ⑤）。
SET NAMES utf8mb4;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLLATION_NAME FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND ((TABLE_NAME = 't_workflow_instance' AND COLUMN_NAME IN ('business_key','active_slot'))
   OR (TABLE_NAME = 't_workflow_action_run' AND COLUMN_NAME IN ('last_check_time','check_result','check_detail','check_failure_count','last_check_success_time')))
ORDER BY TABLE_NAME, ORDINAL_POSITION;
SELECT INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND INDEX_NAME IN ('uk_workflow_inst_active','idx_workflow_action_check') GROUP BY INDEX_NAME, NON_UNIQUE;
-- 期望：running_with_key = slotted，non_running_slotted = 0
SELECT SUM(status = 'RUNNING' AND business_key IS NOT NULL) AS running_with_key, SUM(active_slot = 1) AS slotted,
       SUM(status <> 'RUNNING' AND active_slot IS NOT NULL) AS non_running_slotted FROM t_workflow_instance;
