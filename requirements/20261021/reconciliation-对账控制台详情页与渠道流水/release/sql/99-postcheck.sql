-- 对账控制台详情页与渠道流水 · mor-test 执行后只读核对（root，utf8mb4）。期望值写在每行 k 的说明里。
-- 块 126 注册：资源 1、路由 3、Header 6、Query 10、限流 18（共 38）；无权限、无授权行。激活后路由 3、资源 1 为 ACTIVE。
SELECT 'b126_console_resource(=1)' AS k, COUNT(*) AS v FROM mor_platform.t_console_resource WHERE resource_id BETWEEN 900000000000126000 AND 900000000000126999
UNION ALL SELECT 'b126_gateway_route(=3)', COUNT(*) FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000126000 AND 900000000000126999
UNION ALL SELECT 'b126_route_header(=6)', COUNT(*) FROM mor_platform.t_gateway_route_header WHERE route_id BETWEEN 900000000000126000 AND 900000000000126999
UNION ALL SELECT 'b126_query_param(=10)', COUNT(*) FROM mor_platform.t_gateway_route_query_param WHERE route_id BETWEEN 900000000000126000 AND 900000000000126999
UNION ALL SELECT 'b126_guard_rule(=18)', COUNT(*) FROM mor_platform.t_gateway_guard_rule WHERE route_id BETWEEN 900000000000126000 AND 900000000000126999
UNION ALL SELECT 'b126_permission(=0)', COUNT(*) FROM mor_platform.t_permission WHERE permission_id BETWEEN 900000000000126000 AND 900000000000126999
UNION ALL SELECT 'b126_role_permission(=0)', COUNT(*) FROM mor_platform.t_role_permission WHERE role_permission_id BETWEEN 900000000000126000 AND 900000000000126999
UNION ALL SELECT 'b126_route_active(=3 激活后)', COUNT(*) FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000126401 AND 900000000000126403 AND status = 'ACTIVE'
UNION ALL SELECT 'b126_resource_active(=1 激活后)', COUNT(*) FROM mor_platform.t_console_resource WHERE resource_id = 900000000000126301 AND status = 'ACTIVE'
UNION ALL SELECT 'b126_resource_parent_sort(=123301/45)', CONCAT(parent_resource_id, '/', sort_order) FROM mor_platform.t_console_resource WHERE resource_id = 900000000000126301
-- 块 123 不受影响
UNION ALL SELECT 'b123_route_active(=38)', COUNT(*) FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000123401 AND 900000000000123438 AND status = 'ACTIVE'
UNION ALL SELECT 'b123_resource_active(=10)', COUNT(*) FROM mor_platform.t_console_resource WHERE resource_id BETWEEN 900000000000123301 AND 900000000000123310 AND status = 'ACTIVE'
UNION ALL SELECT 'b123_grant_active(=3)', COUNT(*) FROM mor_platform.t_role_permission WHERE role_permission_id BETWEEN 900000000000123201 AND 900000000000123203 AND status = 'ACTIVE'
UNION ALL SELECT 'platform_admin_version(= 执行前 +1)', version FROM mor_platform.t_role WHERE role_id = 900000000000000510
-- reconciliation 数据只读，不应变化（与 00-precheck 输出比较）
UNION ALL SELECT 'recon_tables(=15)', COUNT(*) FROM information_schema.tables WHERE table_schema = 'mor_reconciliation'
UNION ALL SELECT 'recon_channel_statement_line(= 执行前)', COUNT(*) FROM mor_reconciliation.t_channel_statement_line
UNION ALL SELECT 'recon_bank_match(= 执行前)', COUNT(*) FROM mor_reconciliation.t_bank_match
UNION ALL SELECT 'recon_exception(= 执行前)', COUNT(*) FROM mor_reconciliation.t_recon_exception;
