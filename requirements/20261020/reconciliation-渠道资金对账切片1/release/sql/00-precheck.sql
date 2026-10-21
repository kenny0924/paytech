-- 对账切片 1 · mor-test 执行前只读核对（root，客户端 utf8mb4；不含任何写语句）。
-- 运行：docker exec -e MYSQL_PWD=<root> mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t < 00-precheck.sql
-- 期望（2026-10-21 阶段 6 实测，见 release.md 第 3 节）：
--   1) mor_reconciliation 18 张旧表、每张 0 行；没有 t_clearing_account；
--   2) mor_billing.t_billing_fact_ref 5 列、0 行，无 external_reference_type / external_transaction_id；
--   3) mor_platform 块 123 七张表 0 行，财务菜单 900000000000097301 ACTIVE，PLATFORM_ADMIN 角色版本记录下来（激活后 +1）；
--   4) mor_vault 数据类型 / 访问规则 / 策略变更三表无 ID 9100000000000730、无 CASH_ACCOUNT_NUMBER_V1、无 reconciliation-service 规则。

SELECT 'recon_tables' AS k, COUNT(*) AS v FROM information_schema.tables WHERE table_schema = 'mor_reconciliation'
UNION ALL SELECT 'recon_new_table_present', COUNT(*) FROM information_schema.tables
 WHERE table_schema = 'mor_reconciliation' AND table_name = 't_clearing_account';

SELECT 't_bank_recon_match' AS t, COUNT(*) AS n FROM mor_reconciliation.t_bank_recon_match
UNION ALL SELECT 't_bank_statement', COUNT(*) FROM mor_reconciliation.t_bank_statement
UNION ALL SELECT 't_bank_statement_item', COUNT(*) FROM mor_reconciliation.t_bank_statement_item
UNION ALL SELECT 't_cash_account', COUNT(*) FROM mor_reconciliation.t_cash_account
UNION ALL SELECT 't_cash_account_binding', COUNT(*) FROM mor_reconciliation.t_cash_account_binding
UNION ALL SELECT 't_cash_account_binding_ver', COUNT(*) FROM mor_reconciliation.t_cash_account_binding_ver
UNION ALL SELECT 't_channel_account', COUNT(*) FROM mor_reconciliation.t_channel_account
UNION ALL SELECT 't_channel_cost_alloc', COUNT(*) FROM mor_reconciliation.t_channel_cost_alloc
UNION ALL SELECT 't_channel_cost_confirm', COUNT(*) FROM mor_reconciliation.t_channel_cost_confirm
UNION ALL SELECT 't_channel_debit_item', COUNT(*) FROM mor_reconciliation.t_channel_debit_item
UNION ALL SELECT 't_channel_open_item', COUNT(*) FROM mor_reconciliation.t_channel_open_item
UNION ALL SELECT 't_channel_settle_batch', COUNT(*) FROM mor_reconciliation.t_channel_settle_batch
UNION ALL SELECT 't_channel_settle_item', COUNT(*) FROM mor_reconciliation.t_channel_settle_item
UNION ALL SELECT 't_channel_transaction_match', COUNT(*) FROM mor_reconciliation.t_channel_transaction_match
UNION ALL SELECT 't_compensation_event', COUNT(*) FROM mor_reconciliation.t_compensation_event
UNION ALL SELECT 't_duplicate_capture_item', COUNT(*) FROM mor_reconciliation.t_duplicate_capture_item
UNION ALL SELECT 't_recon_exception', COUNT(*) FROM mor_reconciliation.t_recon_exception
UNION ALL SELECT 't_recon_resolution', COUNT(*) FROM mor_reconciliation.t_recon_resolution;

SELECT 'billing_ref_columns' AS k, COUNT(*) AS v FROM information_schema.columns
 WHERE table_schema = 'mor_billing' AND table_name = 't_billing_fact_ref'
UNION ALL SELECT 'billing_ref_new_columns', COUNT(*) FROM information_schema.columns
 WHERE table_schema = 'mor_billing' AND table_name = 't_billing_fact_ref'
   AND column_name IN ('external_reference_type', 'external_transaction_id')
UNION ALL SELECT 'billing_ref_rows', COUNT(*) FROM mor_billing.t_billing_fact_ref;

SELECT 'permission' AS t, COUNT(*) AS n FROM mor_platform.t_permission
 WHERE permission_id BETWEEN 900000000000123000 AND 900000000000123999 OR permission_code LIKE 'FINANCE_RECONCILIATION%'
UNION ALL SELECT 'role_permission', COUNT(*) FROM mor_platform.t_role_permission
 WHERE role_permission_id BETWEEN 900000000000123000 AND 900000000000123999
UNION ALL SELECT 'console_resource', COUNT(*) FROM mor_platform.t_console_resource
 WHERE resource_id BETWEEN 900000000000123000 AND 900000000000123999 OR resource_code LIKE 'OPERATIONS_FINANCE_RECON%'
UNION ALL SELECT 'gateway_route', COUNT(*) FROM mor_platform.t_gateway_route
 WHERE route_id BETWEEN 900000000000123000 AND 900000000000123999 OR route_code LIKE 'operations-finance-recon-%'
    OR external_path_template LIKE '/api/operations/finance/reconciliation%'
UNION ALL SELECT 'route_header', COUNT(*) FROM mor_platform.t_gateway_route_header
 WHERE route_header_id BETWEEN 900000000000123000 AND 900000000000123999 OR route_id BETWEEN 900000000000123000 AND 900000000000123999
UNION ALL SELECT 'query_param', COUNT(*) FROM mor_platform.t_gateway_route_query_param
 WHERE route_query_param_id BETWEEN 900000000000123000 AND 900000000000123999 OR route_id BETWEEN 900000000000123000 AND 900000000000123999
UNION ALL SELECT 'guard_rule', COUNT(*) FROM mor_platform.t_gateway_guard_rule
 WHERE guard_rule_id BETWEEN 900000000000123000 AND 900000000000123999 OR route_id BETWEEN 900000000000123000 AND 900000000000123999
UNION ALL SELECT 'finance_menu_97301_active', COUNT(*) FROM mor_platform.t_console_resource
 WHERE resource_id = 900000000000097301 AND status = 'ACTIVE'
UNION ALL SELECT 'gateway_target_1', COUNT(*) FROM mor_platform.t_gateway_target WHERE target_id = 900000000000000001
UNION ALL SELECT 'platform_admin_version', version FROM mor_platform.t_role WHERE role_id = 900000000000000510;

SELECT 'vault_data_type' AS k, COUNT(*) AS v FROM mor_vault.t_vault_data_type
 WHERE data_type_id = 9100000000000730 OR data_type_code = 'CASH_ACCOUNT_NUMBER_V1'
UNION ALL SELECT 'vault_access_rule', COUNT(*) FROM mor_vault.t_vault_access_rule
 WHERE access_rule_id = 9100000000000730 OR caller_service = 'reconciliation-service'
UNION ALL SELECT 'vault_policy_change', COUNT(*) FROM mor_vault.t_vault_policy_change
 WHERE policy_change_id = 9100000000000730 OR request_id = 'vault-policy-recon-cash-account-protect'
UNION ALL SELECT 'vault_policy_version_max', MAX(policy_version_no) FROM mor_vault.t_vault_policy_change;
