-- payout 收款账户与余额解释（S1）· 注册块 147 · 执行前只读预检。READ ONLY 会话执行，目标库 mor_platform。
-- 块号 147：任务卡原写 146，但 146 已被 tax（requirements/20261029/tax-结账算税与事件登记）占用、145 = REQ-04、144 = 联合查询，本稿取 147；阶段 6 只读核对现网最大块号后替换（ID 区间 900000000000147001—900000000000147999）。
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- 1) 块 147 各表区间未占用：期望全 0
SELECT (SELECT COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000147001 AND 900000000000147999) AS `permissions`,
       (SELECT COUNT(*) FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000147001 AND 900000000000147999) AS `role_permissions`,
       (SELECT COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000147001 AND 900000000000147999) AS `resources`,
       (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000147001 AND 900000000000147999) AS `routes`,
       (SELECT COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000147001 AND 900000000000147999) AS `headers`,
       (SELECT COUNT(*) FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000147001 AND 900000000000147999) AS `query_params`,
       (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000147001 AND 900000000000147999) AS `guards`,
       (SELECT COUNT(*) FROM t_notification_template WHERE template_id BETWEEN 900000000000147001 AND 900000000000147999) AS `templates`,
       (SELECT COUNT(*) FROM t_notification_template_content WHERE content_id BETWEEN 900000000000147001 AND 900000000000147999) AS `contents`,
       (SELECT COUNT(*) FROM t_notification_route WHERE route_id BETWEEN 900000000000147001 AND 900000000000147999) AS `notification_routes`;
-- 2) 编码、路径与（资源类型, 动作）未占用：期望全 0
SELECT (SELECT COUNT(*) FROM t_permission WHERE permission_code IN ('CLIENT_PAYOUT_ACCOUNT_READ', 'CLIENT_PAYOUT_ACCOUNT_MANAGE', 'CLIENT_BALANCE_READ', 'PAYOUT_BENEFICIARY_READ', 'PAYOUT_BENEFICIARY_SUBMIT', 'PAYOUT_BENEFICIARY_REVIEW', 'PAYOUT_BENEFICIARY_REVEAL', 'PAYOUT_BENEFICIARY_MANAGE') OR (resource_type = 'CLIENT_PAYOUT_ACCOUNT' AND action_code = 'READ') OR (resource_type = 'CLIENT_PAYOUT_ACCOUNT' AND action_code = 'MANAGE') OR (resource_type = 'CLIENT_BALANCE' AND action_code = 'READ') OR (resource_type = 'PAYOUT_BENEFICIARY' AND action_code = 'READ') OR (resource_type = 'PAYOUT_BENEFICIARY' AND action_code = 'SUBMIT') OR (resource_type = 'PAYOUT_BENEFICIARY' AND action_code = 'REVIEW') OR (resource_type = 'PAYOUT_BENEFICIARY' AND action_code = 'REVEAL') OR (resource_type = 'PAYOUT_BENEFICIARY' AND action_code = 'MANAGE')) AS `permission_codes`,
       (SELECT COUNT(*) FROM t_console_resource WHERE resource_code IN ('OPERATIONS_FINANCE_PAYOUT', 'OPERATIONS_FINANCE_PAYOUT_BENEFICIARY_REVIEWS', 'OPERATIONS_FINANCE_PAYOUT_OWNERS', 'OPERATIONS_FINANCE_PAYOUT_BENEFICIARY_REVIEW', 'OPERATIONS_FINANCE_PAYOUT_BENEFICIARY_REVEAL', 'OPERATIONS_FINANCE_PAYOUT_BENEFICIARY_SUBMIT', 'OPERATIONS_FINANCE_PAYOUT_BENEFICIARY_MANAGE') OR frontend_route IN ('/finance/payout/beneficiary-reviews', '/finance/payout/owners')) AS `resource_codes`,
       (SELECT COUNT(*) FROM t_gateway_route WHERE route_code IN ('client-payout-beneficiary-list', 'client-payout-beneficiary-submit', 'client-payout-beneficiary-detail', 'client-payout-beneficiary-default', 'client-payout-beneficiary-close', 'client-payout-beneficiary-requirements', 'client-payout-balances', 'operations-payout-admin-review-list', 'operations-payout-admin-beneficiary-list', 'operations-payout-admin-beneficiary-detail', 'operations-payout-admin-beneficiary-submit', 'operations-payout-admin-maker-review', 'operations-payout-admin-checker-decision', 'operations-payout-admin-reveal', 'operations-payout-admin-suspend', 'operations-payout-admin-resume', 'operations-payout-admin-close', 'operations-payout-admin-balances', 'operations-payout-admin-beneficiary-requirements') OR external_path_template LIKE '/api/client/payout/v1/%'
          OR external_path_template LIKE '/api/operations/payout-admin/v1/%') AS `route_codes_or_paths`,
       (SELECT COUNT(*) FROM t_notification_template WHERE template_code IN ('PAYOUT_BENEFICIARY_VERIFIED_EMAIL', 'PAYOUT_BENEFICIARY_REJECTED_EMAIL', 'PAYOUT_BENEFICIARY_SUSPENDED_EMAIL', 'PAYOUT_BENEFICIARY_CLOSED_EMAIL')) AS `template_codes`;
-- 3) 依赖：管理员角色、财务菜单、Operations 目标、Client BFF 目标存在且 ACTIVE（期望各 1 行）；SES_PRIMARY 可缺（缺时不写模板路由）
SELECT role_id, role_code, status, version FROM t_role WHERE role_id = 900000000000000510;
SELECT resource_id, resource_code, resource_type, status FROM t_console_resource WHERE resource_id = 900000000000097301;
SELECT target_id, target_service_code, channel, status FROM t_gateway_target WHERE target_id = 900000000000000001
  OR (target_service_code = 'client-console-bff' AND channel = 'CLIENT');
SELECT provider_id, provider_code, provider_type, status FROM t_notification_provider WHERE provider_code = 'SES_PRIMARY';
-- 4) 财务菜单现有子项与排序（新组「出款」sort 85：对账 80 之后）
SELECT resource_id, resource_code, sort_order, status FROM t_console_resource WHERE parent_resource_id = 900000000000097301 ORDER BY sort_order;
-- 5) Client 系统角色回填规模：已开通 CLIENT 工作区数与其四个系统角色数（B2 预计行数 = OWNER/ADMIN 角色数 × 3 + 开发者/只读角色数 × 2）
SELECT r.role_code, COUNT(*) AS `roles` FROM t_role r JOIN t_access_tenant t ON t.tenant_id = r.tenant_id
WHERE t.tenant_type = 'CLIENT' AND t.owner_status <> 'PENDING' AND r.tenant_scope = 'TENANT' AND r.system_role = 1
  AND r.scope_key = CAST(t.tenant_id AS CHAR) GROUP BY r.role_code ORDER BY r.role_code;
SELECT COALESCE(MAX(role_permission_id), 9099999999999999999) AS `high_range_max` FROM t_role_permission WHERE role_permission_id >= 9100000000000000000;
-- 6) 现网路由计数（休眠写入后 DISABLED +19，激活后 ACTIVE +19）
SELECT SUM(status = 'ACTIVE') AS `active_routes`, SUM(status = 'DISABLED') AS `disabled_routes`, MAX(route_id) AS `max_route` FROM t_gateway_route;
-- 7) 现网最大块号（按权限主键推断），用于替换块号
SELECT MAX(FLOOR((permission_id - 900000000000000000) / 1000)) AS `max_block` FROM t_permission WHERE permission_id BETWEEN 900000000000001000 AND 900000000000999999;
