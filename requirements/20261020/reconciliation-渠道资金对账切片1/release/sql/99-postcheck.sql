-- 对账切片 1 · mor-test 执行后只读核对（root，utf8mb4）。期望值写在每行 k 的说明里。
-- mor_reconciliation：15 张表（12 业务 + t_compensation_event + 两张消费幂等表），均 0 行（验收前）。
SELECT 'recon_tables(=15)' AS k, COUNT(*) AS v FROM information_schema.tables WHERE table_schema = 'mor_reconciliation'
UNION ALL SELECT 'recon_expected_names(=15)', COUNT(*) FROM information_schema.tables WHERE table_schema = 'mor_reconciliation'
   AND table_name IN ('t_clearing_account', 't_clearing_account_mid', 't_platform_txn', 't_channel_statement', 't_channel_statement_line',
     't_cash_account', 't_bank_statement', 't_bank_statement_line', 't_expected_bank_movement', 't_bank_match', 't_recon_exception',
     't_recon_resolution', 't_consumed_event', 't_consumed_aggregate_version', 't_compensation_event')
-- mor_billing：引用表 7 列，两列可空
UNION ALL SELECT 'billing_ref_columns(=7)', COUNT(*) FROM information_schema.columns
 WHERE table_schema = 'mor_billing' AND table_name = 't_billing_fact_ref'
UNION ALL SELECT 'billing_ref_new_nullable(=2)', COUNT(*) FROM information_schema.columns
 WHERE table_schema = 'mor_billing' AND table_name = 't_billing_fact_ref'
   AND column_name IN ('external_reference_type', 'external_transaction_id') AND is_nullable = 'YES'
-- mor_vault：3 行
UNION ALL SELECT 'vault_rows(=3)', (SELECT COUNT(*) FROM mor_vault.t_vault_data_type WHERE data_type_id = 9100000000000730)
   + (SELECT COUNT(*) FROM mor_vault.t_vault_access_rule WHERE access_rule_id = 9100000000000730)
   + (SELECT COUNT(*) FROM mor_vault.t_vault_policy_change WHERE policy_change_id = 9100000000000730)
-- mor_platform 块 123：注册 3 / 3 / 10 / 38 / 160 / 70 / 228；激活后路由 38、资源 10、授权 3 为 ACTIVE
UNION ALL SELECT 'p_permission(=3)', COUNT(*) FROM mor_platform.t_permission WHERE permission_id BETWEEN 900000000000123000 AND 900000000000123999
UNION ALL SELECT 'p_role_permission(=3)', COUNT(*) FROM mor_platform.t_role_permission WHERE role_permission_id BETWEEN 900000000000123000 AND 900000000000123999
UNION ALL SELECT 'p_console_resource(=10)', COUNT(*) FROM mor_platform.t_console_resource WHERE resource_id BETWEEN 900000000000123000 AND 900000000000123999
UNION ALL SELECT 'p_gateway_route(=38)', COUNT(*) FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000123000 AND 900000000000123999
UNION ALL SELECT 'p_route_header(=160)', COUNT(*) FROM mor_platform.t_gateway_route_header WHERE route_id BETWEEN 900000000000123000 AND 900000000000123999
UNION ALL SELECT 'p_query_param(=70)', COUNT(*) FROM mor_platform.t_gateway_route_query_param WHERE route_id BETWEEN 900000000000123000 AND 900000000000123999
UNION ALL SELECT 'p_guard_rule(=228)', COUNT(*) FROM mor_platform.t_gateway_guard_rule WHERE route_id BETWEEN 900000000000123000 AND 900000000000123999
UNION ALL SELECT 'p_route_active(=38 激活后)', COUNT(*) FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000123401 AND 900000000000123438 AND status = 'ACTIVE'
UNION ALL SELECT 'p_resource_active(=10 激活后)', COUNT(*) FROM mor_platform.t_console_resource WHERE resource_id BETWEEN 900000000000123301 AND 900000000000123310 AND status = 'ACTIVE'
UNION ALL SELECT 'p_grant_active(=3 激活后)', COUNT(*) FROM mor_platform.t_role_permission WHERE role_permission_id BETWEEN 900000000000123201 AND 900000000000123203 AND status = 'ACTIVE'
UNION ALL SELECT 'platform_admin_version', version FROM mor_platform.t_role WHERE role_id = 900000000000000510;
