-- Operations 风控评估查询与解释：Platform 注册数据（2026-10-15 阶段 3 评审稿，待执行文件）
-- 目标库：Platform Support 业务库（本机 mor_platform_e2e / mor-test mor_platform）；手工执行需单独授权，应用启动不会执行。
-- 字符集：用 mysql --default-character-set=utf8mb4 执行；latin1 客户端会把中文写成乱码。
-- 32 行 INSERT；0 UPDATE / DELETE；不新增 Gateway Target，不改表结构。
-- 新路由与菜单资源为 DISABLED，PLATFORM_ADMIN 授权为 REVOKED；激活另见 activation-local.sql（本机）与阶段 6 的环境激活文件。
-- ID 段 900000000000111001～900000000000114018：2026-10-15 在本机 mor_platform_e2e、mor-test mor_platform 与仓库全部 SQL 中均未使用；执行前仍按下方预检核对。
-- 本文件不含 COMMIT：核对插入行数后手工 COMMIT，任何不符则 ROLLBACK。
SET @rev_time=UTC_TIMESTAMP(6);
SET @rev_admin=(SELECT role_id FROM t_role WHERE role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM' AND system_role=1 AND status='ACTIVE');
SET @rev_parent=(SELECT resource_id FROM t_console_resource WHERE resource_code='OPERATIONS_RISK_MANAGEMENT' AND channel='OPERATIONS' AND status='ACTIVE');
SET @rev_target=(SELECT target_id FROM t_gateway_target WHERE target_service_code='operations-console-bff' AND channel='OPERATIONS' AND status='ACTIVE');
SELECT @rev_admin admin_role_id,@rev_parent risk_menu_id,@rev_target target_id;
-- 预检：三个值均非空；以下计数均应为 0，否则停止。
SELECT (SELECT COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000111000 AND 900000000000114999 OR permission_code='RISK_EVALUATION_READ') permissions,
       (SELECT COUNT(*) FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000111000 AND 900000000000114999) role_permissions,
       (SELECT COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000111000 AND 900000000000114999 OR resource_code='OPERATIONS_RISK_EVALUATIONS') resources,
       (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000111000 AND 900000000000114999 OR route_code LIKE 'operations-risk-evaluation-%') routes,
       (SELECT COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000111000 AND 900000000000114999) headers,
       (SELECT COUNT(*) FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000111000 AND 900000000000114999) query_params,
       (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000111000 AND 900000000000114999) guard_rules;
START TRANSACTION;

INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000111101, 'RISK_EVALUATION_READ', 'RISK_EVALUATION', 'READ', 'HIGH', 'ACTIVE', 1, @rev_time, @rev_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (900000000000111201, @rev_admin, 900000000000111101, 'REVOKED', 1, @rev_time, @rev_time);

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000111301, 'OPERATIONS_RISK_EVALUATIONS', 'OPERATIONS', 'MENU', @rev_parent, '评估查询', '/risk/evaluations', 'OPERATIONS_RISK_EVALUATIONS', 900000000000111101, 80, 'DISABLED', 1, @rev_time, @rev_time);

INSERT INTO t_gateway_route (route_id,route_code,channel,http_method,external_path_template,downstream_path_template,target_id,security_profile_code,authorization_mode,permission_id,required_scope_code,request_cookie_mode,response_cookie_mode,body_limit_bytes,body_receive_timeout_millis,response_timeout_millis,status,version,created_by,updated_by,create_time,update_time) VALUES
  (900000000000111001, 'operations-risk-evaluation-detail', 'OPERATIONS', 'GET', '/api/operations/risk-decisions/v1/evaluations/{evaluationId}', '/risk-decisions/v1/evaluations/{evaluationId}', @rev_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000111101, NULL, 'DROP', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @rev_time, @rev_time),
  (900000000000111002, 'operations-risk-evaluation-find', 'OPERATIONS', 'GET', '/api/operations/risk-decisions/v1/evaluations', '/risk-decisions/v1/evaluations', @rev_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000111101, NULL, 'DROP', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @rev_time, @rev_time),
  (900000000000111003, 'operations-risk-evaluation-explanation', 'OPERATIONS', 'GET', '/api/operations/risk-decisions/v1/evaluations/{evaluationId}/explanation', '/risk-decisions/v1/evaluations/{evaluationId}/explanation', @rev_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000111101, NULL, 'DROP', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @rev_time, @rev_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000112001, 900000000000111001, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000112002, 900000000000111001, 'origin', 'FORWARD_REQUEST'),
  (900000000000112003, 900000000000111002, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000112004, 900000000000111002, 'origin', 'FORWARD_REQUEST'),
  (900000000000112005, 900000000000111003, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000112006, 900000000000111003, 'origin', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000113001, 900000000000111002, 'pointCode'),
  (900000000000113002, 900000000000111002, 'businessKey');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000114001, 900000000000111001, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000114002, 900000000000111001, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000114003, 900000000000111001, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000114004, 900000000000111001, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000114005, 900000000000111001, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000114006, 900000000000111001, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000114007, 900000000000111002, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000114008, 900000000000111002, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000114009, 900000000000111002, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000114010, 900000000000111002, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000114011, 900000000000111002, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000114012, 900000000000111002, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000114013, 900000000000111003, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000114014, 900000000000111003, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000114015, 900000000000111003, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000114016, 900000000000111003, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000114017, 900000000000111003, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000114018, 900000000000111003, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

-- 预期影响行数：t_permission 1、t_role_permission 1、t_console_resource 1、t_gateway_route 3、t_gateway_route_header 6、t_gateway_route_query_param 2、t_gateway_guard_rule 18；合计 32。
-- 核对无误后 COMMIT；否则 ROLLBACK。
