-- REQ-01 开通项前置门禁 · mor_commercial 迁移前后只读核对（READ ONLY 会话执行，可重复执行，迁移前、迁移后、回滚后各跑一次）。
-- 阶段 6 现网读数（2026-10-28 23:0x +04）：t_agreement_capability 15 行，全部 status=ENABLED，均属 CLIENT/ACTIVE 合同；
--   execution_status 列不存在；idx_agreement_capability_lookup = (entity_no, capability_code, status)；另有 idx_agreement_capability_code；
--   t_consumed_event / t_consumed_aggregate_version 不存在。
-- 迁移后期望：15 行全部 ACTIVE + ENABLED；lookup 索引第 3 列 execution_status；新增 idx_agreement_capability_execution；两张消费幂等表存在且 0 行。
-- 回滚后期望：回到迁移前读数（两张消费幂等表保留，回滚脚本不删除）。
SELECT 'has_execution_status' AS t, COUNT(*) AS n FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'mor_commercial' AND TABLE_NAME = 't_agreement_capability' AND COLUMN_NAME = 'execution_status';
SELECT 'capability_rows' AS t, COUNT(*) AS n FROM mor_commercial.t_agreement_capability;
SELECT 'capability_by_status' AS t, a.agreement_type, a.status AS agreement_status, c.status, COUNT(*) AS n
  FROM mor_commercial.t_agreement_capability c JOIN mor_commercial.t_agreement a USING (agreement_id)
 GROUP BY a.agreement_type, a.status, c.status ORDER BY 2, 3, 4;
SELECT 'index' AS t, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = 'mor_commercial' AND TABLE_NAME = 't_agreement_capability' GROUP BY INDEX_NAME ORDER BY INDEX_NAME;
SELECT 'consumed_tables' AS t, TABLE_NAME, TABLE_ROWS FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'mor_commercial' AND TABLE_NAME IN ('t_consumed_event', 't_consumed_aggregate_version') ORDER BY TABLE_NAME;
