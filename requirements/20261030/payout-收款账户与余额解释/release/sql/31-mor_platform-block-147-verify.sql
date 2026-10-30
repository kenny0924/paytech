-- payout 收款账户与余额解释（S1）· 注册块 147 核对（写入后 / 激活后 / 停用后均可执行；READ ONLY；目标库 mor_platform）。
-- 块号 147：任务卡原写 146，但 146 已被 tax（requirements/20261029/tax-结账算税与事件登记）占用、145 = REQ-04、144 = 联合查询，本稿取 147；阶段 6 只读核对现网最大块号后替换（ID 区间 900000000000147001—900000000000147999）。
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- 1) 固定行数：permission 8 / admin grant 5 / resource 7 / route 19 / header 71 /
--    query_param 19 / guard 114 / template 4 / content 8 / notification_route 4
SELECT (SELECT COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000147101 AND 900000000000147108) AS `permissions_8`,
       (SELECT COUNT(*) FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000147201 AND 900000000000147205) AS `admin_grants_5`,
       (SELECT COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000147301 AND 900000000000147307) AS `resources_7`,
       (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000147401 AND 900000000000147419) AS `routes_19`,
       (SELECT COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000147501 AND 900000000000147571) AS `headers_71`,
       (SELECT COUNT(*) FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000147001 AND 900000000000147019) AS `query_params_19`,
       (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000147601 AND 900000000000147714) AS `guards_114`,
       (SELECT COUNT(*) FROM t_notification_template WHERE template_id BETWEEN 900000000000147801 AND 900000000000147804) AS `templates_4`,
       (SELECT COUNT(*) FROM t_notification_template_content WHERE content_id BETWEEN 900000000000147811 AND 900000000000147818) AS `contents_8`,
       (SELECT COUNT(*) FROM t_notification_route WHERE route_id BETWEEN 900000000000147821 AND 900000000000147824) AS `notification_routes_4_or_0_without_provider`,
       (SELECT COUNT(*) FROM t_notification_provider WHERE provider_code = 'SES_PRIMARY') AS `ses_primary_present`;
-- 2) 状态：休眠 = 授权 REVOKED / 资源与路由 DISABLED；激活 = 全部 ACTIVE；权限、模板与模板路由始终 ACTIVE
SELECT 'permission' AS `kind`, status, COUNT(*) AS `n` FROM t_permission WHERE permission_id BETWEEN 900000000000147101 AND 900000000000147108 GROUP BY status
UNION ALL SELECT 'admin_grant', status, COUNT(*) FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000147201 AND 900000000000147205 GROUP BY status
UNION ALL SELECT 'client_grant', rp.status, COUNT(*) FROM t_role_permission rp WHERE rp.permission_id IN (900000000000147101, 900000000000147102, 900000000000147103) GROUP BY rp.status
UNION ALL SELECT 'resource', status, COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000147301 AND 900000000000147307 GROUP BY status
UNION ALL SELECT 'client_route', status, COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000147401 AND 900000000000147407 GROUP BY status
UNION ALL SELECT 'operations_route', status, COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000147408 AND 900000000000147419 GROUP BY status
UNION ALL SELECT 'template', status, COUNT(*) FROM t_notification_template WHERE template_id BETWEEN 900000000000147801 AND 900000000000147804 GROUP BY status
UNION ALL SELECT 'template_route', status, COUNT(*) FROM t_notification_route WHERE route_id BETWEEN 900000000000147821 AND 900000000000147824 GROUP BY status;
-- 3) Client 系统角色矩阵：已开通 CLIENT 工作区的每个系统角色应持有的权限缺失数（期望 0）与多余授予数（期望 0）
SELECT COUNT(*) AS `missing_client_grants_0`
FROM t_role r JOIN t_access_tenant t ON t.tenant_id = r.tenant_id
JOIN (
  SELECT 'TENANT_OWNER' role_code, 900000000000147101 permission_id
  UNION ALL SELECT 'TENANT_OWNER', 900000000000147102
  UNION ALL SELECT 'TENANT_OWNER', 900000000000147103
  UNION ALL SELECT 'TENANT_ADMIN', 900000000000147101
  UNION ALL SELECT 'TENANT_ADMIN', 900000000000147102
  UNION ALL SELECT 'TENANT_ADMIN', 900000000000147103
  UNION ALL SELECT 'TENANT_DEVELOPER', 900000000000147101
  UNION ALL SELECT 'TENANT_DEVELOPER', 900000000000147103
  UNION ALL SELECT 'TENANT_VIEWER', 900000000000147101
  UNION ALL SELECT 'TENANT_VIEWER', 900000000000147103
) m ON m.role_code = r.role_code
WHERE t.tenant_type = 'CLIENT' AND t.owner_status <> 'PENDING' AND r.tenant_scope = 'TENANT' AND r.system_role = 1
  AND r.scope_key = CAST(t.tenant_id AS CHAR)
  AND NOT EXISTS (SELECT 1 FROM t_role_permission x WHERE x.role_id = r.role_id AND x.permission_id = m.permission_id);
SELECT COUNT(*) AS `unexpected_client_grants_0` FROM t_role_permission rp JOIN t_role r ON r.role_id = rp.role_id
WHERE rp.permission_id = 900000000000147102 AND r.role_code IN ('TENANT_DEVELOPER', 'TENANT_VIEWER') AND r.system_role = 1;
-- 4) 模板内容：每个模板 zh-CN 与 en 各一条，均路由到 SES_PRIMARY
SELECT t.template_code, GROUP_CONCAT(c.locale ORDER BY c.locale) AS `locales_en_zh-CN`, p.provider_code
FROM t_notification_template t JOIN t_notification_template_content c ON c.template_id = t.template_id
JOIN t_notification_route nr ON nr.notification_template_id = t.template_id JOIN t_notification_provider p ON p.provider_id = nr.primary_provider_id
WHERE t.template_id BETWEEN 900000000000147801 AND 900000000000147804 GROUP BY t.template_code, p.provider_code ORDER BY t.template_code;
-- 5) 路由形状：Client 与块 138 Client 路由、Operations 与块 143 同形（安全档、授权模式、Cookie、幂等、超时、目标）
SELECT route_id, route_code, http_method, security_profile_code, authorization_mode, permission_id, body_limit_bytes, target_id, status
FROM t_gateway_route WHERE route_id BETWEEN 900000000000147401 AND 900000000000147419 ORDER BY route_id;
-- 6) 全局路由计数（与 -precheck.sql 第 6 项对照）
SELECT SUM(status = 'ACTIVE') AS `active_routes`, SUM(status = 'DISABLED') AS `disabled_routes` FROM t_gateway_route;
