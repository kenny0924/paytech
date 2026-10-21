-- 对账切片 1 · mor-test mor_reconciliation 旧表删除（root；第 4 节 SQL-2 的前半段，紧接着执行 ../db/reconciliation/ddl.sql）。
-- 前置：00-precheck.sql 显示 18 张旧表全部 0 行；backup.sh 已备份整库（mor_reconciliation.sql）；reconciliation-service 已停止。
-- 只删表、不删库：保留 reconciliation_app 对 mor_reconciliation.* 的库级 SELECT/INSERT/UPDATE/DELETE 授权（本机 2026-10-20 同做法）。
-- 每张表先断言为空：任何一张非空则 SIGNAL 中止，后续 DROP 不执行（mysql 客户端遇错停止）。
-- 回滚：DROP 新 15 张表后 `mysql mor_reconciliation < <备份目录>/mor_reconciliation.sql`（库内无业务数据）。
SET NAMES utf8mb4;
USE `mor_reconciliation`;

DROP PROCEDURE IF EXISTS `recon_slice1_assert_empty`;
DELIMITER //
CREATE PROCEDURE `recon_slice1_assert_empty`()
BEGIN
  DECLARE total BIGINT DEFAULT 0;
  SELECT (SELECT COUNT(*) FROM t_bank_recon_match) + (SELECT COUNT(*) FROM t_bank_statement)
       + (SELECT COUNT(*) FROM t_bank_statement_item) + (SELECT COUNT(*) FROM t_cash_account)
       + (SELECT COUNT(*) FROM t_cash_account_binding) + (SELECT COUNT(*) FROM t_cash_account_binding_ver)
       + (SELECT COUNT(*) FROM t_channel_account) + (SELECT COUNT(*) FROM t_channel_cost_alloc)
       + (SELECT COUNT(*) FROM t_channel_cost_confirm) + (SELECT COUNT(*) FROM t_channel_debit_item)
       + (SELECT COUNT(*) FROM t_channel_open_item) + (SELECT COUNT(*) FROM t_channel_settle_batch)
       + (SELECT COUNT(*) FROM t_channel_settle_item) + (SELECT COUNT(*) FROM t_channel_transaction_match)
       + (SELECT COUNT(*) FROM t_compensation_event) + (SELECT COUNT(*) FROM t_duplicate_capture_item)
       + (SELECT COUNT(*) FROM t_recon_exception) + (SELECT COUNT(*) FROM t_recon_resolution)
    INTO total;
  IF total <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'mor_reconciliation old tables are not empty; abort';
  END IF;
END//
DELIMITER ;
CALL `recon_slice1_assert_empty`();
DROP PROCEDURE `recon_slice1_assert_empty`;

DROP TABLE `t_bank_recon_match`, `t_bank_statement`, `t_bank_statement_item`, `t_cash_account`, `t_cash_account_binding`,
  `t_cash_account_binding_ver`, `t_channel_account`, `t_channel_cost_alloc`, `t_channel_cost_confirm`, `t_channel_debit_item`,
  `t_channel_open_item`, `t_channel_settle_batch`, `t_channel_settle_item`, `t_channel_transaction_match`, `t_compensation_event`,
  `t_duplicate_capture_item`, `t_recon_exception`, `t_recon_resolution`;

-- 期望 0
SELECT COUNT(*) AS tables_left FROM information_schema.tables WHERE table_schema = 'mor_reconciliation';
