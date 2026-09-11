-- 阶段 3 评审稿，未执行；仅连接已批准环境的 mor_platform。
-- 增加 16 行：Permission/RolePermission/ConsoleResource/Route 各 1，Header 6，Guard 6。
-- 为已有 PLATFORM_ADMIN 增加权限后，其 Role.version 增加 1；只读角色不授权。
-- Route 与按钮先 DISABLED；阶段 6/7 再审阅并执行具体启用清单，不在本文件自动曝光。
-- 先备份受影响配置，暂停控制面配置写入。mysql --batch 执行，禁止 --force / 自动忽略错误。
SET @risk_platform_saved_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'STRICT_ALL_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION,NO_ZERO_DATE,NO_ZERO_IN_DATE';
CREATE TEMPORARY TABLE risk_rule_set_platform_gate (checkpoint VARCHAR(100) NOT NULL);
INSERT INTO risk_rule_set_platform_gate
SELECT IF(DATABASE() = 'mor_platform', 'approved database name', NULL);
START TRANSACTION;
INSERT INTO risk_rule_set_platform_gate
SELECT IF((SELECT COUNT(*) FROM t_role WHERE role_id = 900000000000000510
  AND role_code = 'PLATFORM_ADMIN' AND tenant_scope = 'PLATFORM'
  AND tenant_id IS NULL AND scope_key = 'PLATFORM' AND status = 'ACTIVE' AND system_role = 1) = 1,
  'exact existing administrator role', NULL);
SELECT version INTO @risk_admin_role_version FROM t_role
WHERE role_id = 900000000000000510 FOR UPDATE;
INSERT INTO risk_rule_set_platform_gate
SELECT IF((SELECT COUNT(*) FROM t_console_resource WHERE resource_id = 900000000000000566
  AND resource_code = 'OPERATIONS_RISK_RULE_CONFIGURATION' AND channel = 'OPERATIONS'
  AND resource_type = 'MENU') = 1
  AND (SELECT COUNT(*) FROM t_gateway_target WHERE target_id = 900000000000000001
       AND channel = 'OPERATIONS' AND status = 'ACTIVE') = 1
  AND (SELECT COUNT(*) FROM t_gateway_route WHERE route_id = 900000000000001027
       AND route_code = 'operations-risk-rule-set-save' AND target_id = 900000000000000001
       AND channel = 'OPERATIONS' AND http_method = 'POST'
       AND downstream_path_template = '/decision-rule-sets/v1/rule-sets') = 1,
  'existing menu and Risk BFF target verified', NULL);
INSERT INTO risk_rule_set_platform_gate
SELECT IF(NOT EXISTS (SELECT 1 FROM t_permission
  WHERE permission_id = 900000000000000541 OR permission_code = 'RISK_RULE_SET_DELETE')
  AND NOT EXISTS (SELECT 1 FROM t_role_permission WHERE role_permission_id = 900000000000000587
      OR (role_id = 900000000000000510 AND permission_id = 900000000000000541))
  AND NOT EXISTS (SELECT 1 FROM t_console_resource WHERE resource_id = 900000000000000571
      OR resource_code = 'OPERATIONS_RISK_RULE_SET_DELETE')
  AND NOT EXISTS (SELECT 1 FROM t_gateway_route WHERE route_id = 900000000000001033
      OR route_code = 'operations-risk-rule-set-delete')
  AND NOT EXISTS (SELECT 1 FROM t_gateway_route_header
      WHERE route_header_id BETWEEN 900000000000005140 AND 900000000000005145)
  AND NOT EXISTS (SELECT 1 FROM t_gateway_guard_rule
      WHERE guard_rule_id BETWEEN 900000000000006164 AND 900000000000006169),
  'all reserved IDs and codes absent', NULL);
-- 核对完整 Catalog 的模板等价冲突属于执行前人工/现有控制面校验门禁，不能只看 Path 字面值。
SET @risk_rule_set_registration_time = UTC_TIMESTAMP(6);
INSERT INTO t_permission
(permission_id, permission_code, resource_type, action_code, risk_level, status, version, create_time, update_time)
VALUES (900000000000000541, 'RISK_RULE_SET_DELETE', 'RISK_DECISION_CONFIGURATION', 'DELETE', 'HIGH',
        'ACTIVE', 1, @risk_rule_set_registration_time, @risk_rule_set_registration_time);
INSERT INTO t_role_permission
(role_permission_id, role_id, permission_id, status, version, create_time, update_time)
VALUES (900000000000000587, 900000000000000510, 900000000000000541, 'ACTIVE', 1,
        @risk_rule_set_registration_time, @risk_rule_set_registration_time);
UPDATE t_role SET version = version + 1, update_time = @risk_rule_set_registration_time
WHERE role_id = 900000000000000510 AND version = @risk_admin_role_version AND status = 'ACTIVE';
INSERT INTO risk_rule_set_platform_gate SELECT IF(ROW_COUNT() = 1, 'administrator role CAS updated', NULL);
INSERT INTO t_console_resource
(resource_id, resource_code, channel, resource_type, parent_resource_id, display_name, frontend_route, component_key,
 permission_id, sort_order, status, version, create_time, update_time)
VALUES (900000000000000571, 'OPERATIONS_RISK_RULE_SET_DELETE', 'OPERATIONS', 'BUTTON', 900000000000000566,
        '删除规则集', NULL, NULL, 900000000000000541, 50, 'DISABLED', 1,
        @risk_rule_set_registration_time, @risk_rule_set_registration_time);
INSERT INTO t_gateway_route
(route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id,
 security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode,
 response_cookie_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version,
 created_by, updated_by, create_time, update_time)
VALUES (900000000000001033, 'operations-risk-rule-set-delete', 'OPERATIONS', 'DELETE',
        '/api/operations/decision-rule-sets/v1/rule-sets/{ruleSetId}', '/decision-rule-sets/v1/rule-sets/{ruleSetId}',
        900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000541, NULL, 'DROP', 'NONE',
        16384, 5000, 10000, 'DISABLED', 1, 1, 1,
        @risk_rule_set_registration_time, @risk_rule_set_registration_time);
INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use) VALUES
    (900000000000005140, 900000000000001033, 'authorization', 'CORS_REQUEST_ALLOWED'),
    (900000000000005141, 900000000000001033, 'origin', 'FORWARD_REQUEST'),
    (900000000000005142, 900000000000001033, 'content-type', 'CORS_REQUEST_ALLOWED'),
    (900000000000005143, 900000000000001033, 'content-type', 'FORWARD_REQUEST'),
    (900000000000005144, 900000000000001033, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
    (900000000000005145, 900000000000001033, 'idempotency-key', 'FORWARD_REQUEST');
INSERT INTO t_gateway_guard_rule
(guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit) VALUES
    (900000000000006164, 900000000000001033, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
    (900000000000006165, 900000000000001033, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
    (900000000000006166, 900000000000001033, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL),
    (900000000000006167, 900000000000001033, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
    (900000000000006168, 900000000000001033, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
    (900000000000006169, 900000000000001033, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);
INSERT INTO risk_rule_set_platform_gate
SELECT IF((SELECT COUNT(*) FROM t_gateway_route_header WHERE route_id = 900000000000001033) = 6
  AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE route_id = 900000000000001033) = 6
  AND NOT EXISTS (SELECT 1 FROM t_gateway_route_query_param WHERE route_id = 900000000000001033),
  'route has complete headers and guards and no query parameters', NULL);
COMMIT;
DROP TEMPORARY TABLE risk_rule_set_platform_gate;
SET SESSION sql_mode = @risk_platform_saved_sql_mode;
-- created_by/updated_by = 1 延续人工种子数据的工程归属，不伪造真实 Operations 操作人。
-- 新环境的单一初始化 DML 在阶段 4 同步这些定义；初始化角色直接包含新授权，不重复执行本增量。
-- 本文件不改变旧 Candidate Route/Permission，不新增编辑 Permission，也不新增 Gateway Target。
