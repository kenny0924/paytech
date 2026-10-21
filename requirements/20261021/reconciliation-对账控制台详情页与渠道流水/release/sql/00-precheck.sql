-- 对账控制台详情页与渠道流水 · mor-test 执行前只读核对（root，utf8mb4，只有 SELECT）。期望值写在每行 k 的说明里。
-- 块 126：七张注册表按 ID 段、路由码、资源码、外部路径均未占用。
SELECT 'b126_permission(=0)' AS k, COUNT(*) AS v FROM mor_platform.t_permission WHERE permission_id BETWEEN 900000000000126000 AND 900000000000126999
UNION ALL SELECT 'b126_role_permission(=0)', COUNT(*) FROM mor_platform.t_role_permission WHERE role_permission_id BETWEEN 900000000000126000 AND 900000000000126999
UNION ALL SELECT 'b126_console_resource(=0)', COUNT(*) FROM mor_platform.t_console_resource WHERE resource_id BETWEEN 900000000000126000 AND 900000000000126999
UNION ALL SELECT 'b126_gateway_route(=0)', COUNT(*) FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000126000 AND 900000000000126999
UNION ALL SELECT 'b126_route_header(=0)', COUNT(*) FROM mor_platform.t_gateway_route_header WHERE route_header_id BETWEEN 900000000000126000 AND 900000000000126999 OR route_id BETWEEN 900000000000126000 AND 900000000000126999
UNION ALL SELECT 'b126_query_param(=0)', COUNT(*) FROM mor_platform.t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000126000 AND 900000000000126999 OR route_id BETWEEN 900000000000126000 AND 900000000000126999
UNION ALL SELECT 'b126_guard_rule(=0)', COUNT(*) FROM mor_platform.t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000126000 AND 900000000000126999 OR route_id BETWEEN 900000000000126000 AND 900000000000126999
UNION ALL SELECT 'b126_route_codes(=0)', COUNT(*) FROM mor_platform.t_gateway_route WHERE route_code IN ('operations-finance-recon-channel-line-search',
   'operations-finance-recon-bank-statement-detail', 'operations-finance-recon-bank-statement-line-detail')
UNION ALL SELECT 'b126_route_paths(=0)', COUNT(*) FROM mor_platform.t_gateway_route WHERE http_method = 'GET' AND external_path_template IN (
   '/api/operations/finance/reconciliation/v1/channel-statement-lines',
   '/api/operations/finance/reconciliation/v1/bank-statements/{bankStatementId}',
   '/api/operations/finance/reconciliation/v1/bank-statement-lines/{bankStatementLineId}')
UNION ALL SELECT 'b126_resource_code(=0)', COUNT(*) FROM mor_platform.t_console_resource WHERE resource_code = 'OPERATIONS_FINANCE_RECON_CHANNEL_LINES'
   OR component_key = 'OPERATIONS_FINANCE_RECON_CHANNEL_LINES' OR frontend_route = '/finance/reconciliation/channel-lines'
-- 块 123（父菜单、权限、Target）已激活：路由 38、资源 10、授权 3 为 ACTIVE。
UNION ALL SELECT 'b123_route_active(=38)', COUNT(*) FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000123401 AND 900000000000123438 AND status = 'ACTIVE'
UNION ALL SELECT 'b123_resource_active(=10)', COUNT(*) FROM mor_platform.t_console_resource WHERE resource_id BETWEEN 900000000000123301 AND 900000000000123310 AND status = 'ACTIVE'
UNION ALL SELECT 'b123_grant_active(=3)', COUNT(*) FROM mor_platform.t_role_permission WHERE role_permission_id BETWEEN 900000000000123201 AND 900000000000123203 AND status = 'ACTIVE'
UNION ALL SELECT 'b123_parent_menu_active(=1)', COUNT(*) FROM mor_platform.t_console_resource WHERE resource_id = 900000000000123301 AND status = 'ACTIVE'
UNION ALL SELECT 'b123_read_permission(=1)', COUNT(*) FROM mor_platform.t_permission WHERE permission_id = 900000000000123101
UNION ALL SELECT 'target_900000000000000001(=1)', COUNT(*) FROM mor_platform.t_gateway_target WHERE target_id = 900000000000000001
UNION ALL SELECT 'platform_admin_version(记录；激活后 +1)', version FROM mor_platform.t_role WHERE role_id = 900000000000000510
-- mor_reconciliation：15 张表在；演示数据行数（验收只读，不改）。
UNION ALL SELECT 'recon_tables(=15)', COUNT(*) FROM information_schema.tables WHERE table_schema = 'mor_reconciliation'
UNION ALL SELECT 'recon_clearing_account(rows)', COUNT(*) FROM mor_reconciliation.t_clearing_account
UNION ALL SELECT 'recon_cash_account(rows)', COUNT(*) FROM mor_reconciliation.t_cash_account
UNION ALL SELECT 'recon_channel_statement(rows)', COUNT(*) FROM mor_reconciliation.t_channel_statement
UNION ALL SELECT 'recon_channel_statement_line(rows)', COUNT(*) FROM mor_reconciliation.t_channel_statement_line
UNION ALL SELECT 'recon_platform_txn(rows)', COUNT(*) FROM mor_reconciliation.t_platform_txn
UNION ALL SELECT 'recon_bank_statement(rows)', COUNT(*) FROM mor_reconciliation.t_bank_statement
UNION ALL SELECT 'recon_bank_statement_line(rows)', COUNT(*) FROM mor_reconciliation.t_bank_statement_line
UNION ALL SELECT 'recon_bank_match(rows)', COUNT(*) FROM mor_reconciliation.t_bank_match
UNION ALL SELECT 'recon_exception(rows)', COUNT(*) FROM mor_reconciliation.t_recon_exception
UNION ALL SELECT 'recon_exception_open(rows)', COUNT(*) FROM mor_reconciliation.t_recon_exception WHERE status IN ('OPEN', 'UNDER_REVIEW');
