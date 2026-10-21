-- 对账切片 1 · mor-test 数据回滚片段（root，utf8mb4）。按 release.md 第 6 节逐段执行，不要整文件一次执行。
-- 每段前提：对应服务已回到上一版镜像（compose-rollback.yaml）或已停止；平台段先执行 ../../db/platform-support/test-deactivate.sql。

-- R-billing（mor_billing）：两列在 billing 需求 B 之前不会有数据；先核对为 0 再删列，ref_type 注释改回原文。
SELECT COUNT(*) AS non_null_rows FROM mor_billing.t_billing_fact_ref
 WHERE external_reference_type IS NOT NULL OR external_transaction_id IS NOT NULL;   -- 期望 0
ALTER TABLE mor_billing.t_billing_fact_ref
  DROP COLUMN `external_transaction_id`, DROP COLUMN `external_reference_type`,
  MODIFY COLUMN `ref_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '引用的外部业务对象类型。允许值：ORDER、ORDER_CONTEXT、PAYMENT_INTENT、PAYMENT_ATTEMPT、PAYMENT_CHANNEL_ACCOUNT、MID、REFUND_REQUEST、CHANNEL_REFUND、DUPLICATE_ITEM、DISPUTE_NOTICE、DISPUTE_CASE、TAX_SNAPSHOT、CLEARING_ACCOUNT、SETTLEMENT_BATCH、SETTLEMENT_ITEM、CASH_ACCOUNT、PAYOUT、TAX_PAYMENT、TICKET、FX_DEAL、FX_QUOTE。';

-- R-reconciliation（mor_reconciliation）：删新 15 张表后用备份恢复旧 18 张空表（之后的验收数据随之丢弃）。
-- DROP TABLE mor_reconciliation.t_bank_match, mor_reconciliation.t_bank_statement_line, mor_reconciliation.t_bank_statement,
--   mor_reconciliation.t_expected_bank_movement, mor_reconciliation.t_recon_resolution, mor_reconciliation.t_recon_exception,
--   mor_reconciliation.t_channel_statement_line, mor_reconciliation.t_channel_statement, mor_reconciliation.t_platform_txn,
--   mor_reconciliation.t_clearing_account_mid, mor_reconciliation.t_clearing_account, mor_reconciliation.t_cash_account,
--   mor_reconciliation.t_compensation_event, mor_reconciliation.t_consumed_event, mor_reconciliation.t_consumed_aggregate_version;
-- 然后（宿主机）：docker exec -i -e MYSQL_PWD=<root> mor-test-mysql-1 mysql -uroot mor_reconciliation < <备份目录>/mor_reconciliation.sql

-- R-vault（mor_vault）：只在 reconciliation 已回滚、库内没有 CASH_ACCOUNT_NUMBER_V1 记录引用时执行；Vault 按刷新间隔重载。
SELECT COUNT(*) AS cash_records FROM mor_vault.t_vault_record WHERE data_type = 'CASH_ACCOUNT_NUMBER_V1';   -- 非 0 时只停用规则，不删类型
START TRANSACTION;
DELETE FROM mor_vault.t_vault_access_rule WHERE access_rule_id = 9100000000000730;
DELETE FROM mor_vault.t_vault_data_type WHERE data_type_id = 9100000000000730;
DELETE FROM mor_vault.t_vault_policy_change WHERE policy_change_id = 9100000000000730;
-- 期望 1 / 1 / 1，核对后 COMMIT
COMMIT;

-- R-platform（mor_platform）：先执行 ../../db/platform-support/test-deactivate.sql（38 / 10 / 3 / 1）。休眠行可保留；需要删除时（子表先删）：
-- START TRANSACTION;
-- DELETE FROM mor_platform.t_gateway_guard_rule WHERE route_id BETWEEN 900000000000123401 AND 900000000000123438;          -- 228
-- DELETE FROM mor_platform.t_gateway_route_query_param WHERE route_id BETWEEN 900000000000123401 AND 900000000000123438;   -- 70
-- DELETE FROM mor_platform.t_gateway_route_header WHERE route_id BETWEEN 900000000000123401 AND 900000000000123438;        -- 160
-- DELETE FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000123401 AND 900000000000123438;               -- 38
-- DELETE FROM mor_platform.t_console_resource WHERE resource_id BETWEEN 900000000000123301 AND 900000000000123310 ORDER BY resource_id DESC; -- 10
-- DELETE FROM mor_platform.t_role_permission WHERE role_permission_id BETWEEN 900000000000123201 AND 900000000000123203;   -- 3
-- DELETE FROM mor_platform.t_permission WHERE permission_id BETWEEN 900000000000123101 AND 900000000000123103;             -- 3
-- COMMIT;
