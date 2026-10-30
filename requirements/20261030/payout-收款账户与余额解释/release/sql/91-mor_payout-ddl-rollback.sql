-- payout S1 · mor_payout DDL 回滚（root，库 mor_payout）。只用于首建失败、或尚未产生任何业务数据就决定撤回时；technique 第 9 节：一旦有数据，
--   回退 = 停 payout-service + 保留库，不执行本文件。先停 payout-service（防止回滚过程中写入），之后可接 92-mysql-payout-database-rollback.sql。
-- 保护（失败即中止，不删任何表）：① 三张表只存在一部分 → 人工核查；② 任一表有行（DROP 前确认无数据）。拒绝方式：标量子查询返回两行 → ERROR 1242。
-- 重复执行：三表都不存在时为空操作。
USE `mor_payout`;
SET @payout_tables = (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'mor_payout'
  AND table_name IN ('t_beneficiary_account', 't_beneficiary_account_event', 't_compensation_event'));
SET @payout_partial_guard = (SELECT 1 UNION ALL SELECT 1 FROM DUAL WHERE @payout_tables NOT IN (0, 3));
SET @payout_count_sql = IF(@payout_tables = 3,
  'SELECT (SELECT COUNT(*) FROM t_beneficiary_account) + (SELECT COUNT(*) FROM t_beneficiary_account_event) + (SELECT COUNT(*) FROM t_compensation_event) INTO @payout_rows',
  'SELECT 0 INTO @payout_rows');
PREPARE payout_count_stmt FROM @payout_count_sql;
EXECUTE payout_count_stmt;
DEALLOCATE PREPARE payout_count_stmt;
SELECT @payout_tables AS `tables_before`, @payout_rows AS `rows_before`;
SET @payout_data_guard = (SELECT 1 UNION ALL SELECT 1 FROM DUAL WHERE @payout_rows <> 0);
DROP TABLE IF EXISTS `t_compensation_event`, `t_beneficiary_account_event`, `t_beneficiary_account`;
SELECT COUNT(*) AS `tables_after` FROM information_schema.tables WHERE table_schema = 'mor_payout';
