-- Platform 注册块 138补充件（全球合规入驻与资格切片 1 · CLIENT：Client 门户辖区、采集 Schema、声明、需求清单与撤回），
-- 只在 mor_platform 执行，客户端须 utf8mb4。由 gen_block138.py 依 block138-client-spec.json 生成，勿手改。
-- 来源：requirements/20261027/client-master-全球合规入驻与资格切片1/technique.md 第 2.1—2.3 节；db/platform-support/registration-block138.draft.md；主会话 2026-10-27 决定 1—6。
-- 前置：本需求 client-master、compliance、client-console-bff、operations-console-bff 新镜像已发布（休眠注册本身不依赖镜像）。
-- 执行后为休眠状态：permission ACTIVE、PLATFORM_ADMIN 授权 REVOKED、菜单 / 按钮 / 路由 DISABLED；启用见 block138-activate.sql。
-- 预期插入：permission 0、role_permission 0、console_resource 0、gateway_route 5、route_header 13、query_param 4、guard_rule 30，共 52 行。
-- 执行前：block138-precheck.sql 全部符合期望；发布前须用候选 Platform JAR 跑 assets/harness/block_compile_check.sh（五态 COMPILE OK）。
-- 新权限：。
-- 既有对象经变量引用：@b138_client_target。
-- ID（本文件）：permission 900000000000138101—900000000000138100、role_permission 900000000000138201—900000000000138200、console_resource 900000000000138301—900000000000138300、gateway_route 900000000000138441—900000000000138445、route_header 900000000000138581—900000000000138593、query_param 900000000000138651—900000000000138654、guard_rule 900000000000138901—900000000000138930。回滚只删以上区间（block138-client-rollback.sql）。
-- ---------------------------------------------------------------------------
-- Global onboarding and qualification slice 1, CLIENT addendum (2026-10-27, requirements/20261027/client-master-全球合规入驻与资格切片1).
-- Apply after the Operations part of block 138; own ID ranges (routes 441+, headers 581+, query 651+, guards 901+), no permissions or
-- console resources. Routes forward to client-console-bff (target resolved by target_service_code) with the existing onboarding mapping
-- /api/client/onboarding/v1 -> /client-onboarding/v1; CLIENT_JWT + HUMAN_AUTHENTICATED. Shapes copy the live onboarding routes: GET
-- authorization CORS; writes add content-type (CORS + forward), idempotency-key (CORS only) and origin forward; body 16384 for reads and 65536 for writes (like c16 / c12); guards
-- SHARED_ROUTE 600/min + 50, CLIENT_IP 120/min + 10, principal 60/min + 5. The review result keeps the existing route.
-- Rows: permission 0, role_permission 0, console_resource 0, gateway_route 5, route_header 13, query_param 4, guard_rule 30 (52). IDs: see the Chinese header above.
-- Fails closed: if an existing object is missing, the guard statement raises ERROR 1242 and nothing is committed.
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @b138_time = UTC_TIMESTAMP(6);
SET @b138_actor_id = 1;
SET @b138_role_id = 900000000000000510;
-- 既有对象（各环境 ID 不同）：先取到变量；任一为 NULL 时下一句产生两行子查询 → ERROR 1242 中止，事务不提交。
SET @b138_client_target = (SELECT target_id FROM t_gateway_target WHERE target_service_code = 'client-console-bff' AND channel = 'CLIENT');
SET @b138_lookup_guard = (SELECT 1 UNION ALL SELECT 1 FROM DUAL WHERE @b138_client_target IS NULL);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000138441, 'client-onboarding-c17', 'CLIENT', 'GET', '/api/client/onboarding/v1/jurisdictions', '/client-onboarding/v1/jurisdictions', @b138_client_target, 'CLIENT_JWT', 'HUMAN_AUTHENTICATED', NULL, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'DISABLED', 1, @b138_actor_id, @b138_actor_id, @b138_time, @b138_time),
  (900000000000138442, 'client-onboarding-c18', 'CLIENT', 'GET', '/api/client/onboarding/v1/form-schema', '/client-onboarding/v1/form-schema', @b138_client_target, 'CLIENT_JWT', 'HUMAN_AUTHENTICATED', NULL, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'DISABLED', 1, @b138_actor_id, @b138_actor_id, @b138_time, @b138_time),
  (900000000000138443, 'client-onboarding-c19', 'CLIENT', 'POST', '/api/client/onboarding/v1/applications/{applicationId}/declarations', '/client-onboarding/v1/applications/{applicationId}/declarations', @b138_client_target, 'CLIENT_JWT', 'HUMAN_AUTHENTICATED', NULL, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @b138_actor_id, @b138_actor_id, @b138_time, @b138_time),
  (900000000000138444, 'client-onboarding-c20', 'CLIENT', 'GET', '/api/client/onboarding/v1/applications/{applicationId}/requirements', '/client-onboarding/v1/applications/{applicationId}/requirements', @b138_client_target, 'CLIENT_JWT', 'HUMAN_AUTHENTICATED', NULL, NULL, 'DROP', 'NONE', 'NONE', 16384, 5000, 10000, 'DISABLED', 1, @b138_actor_id, @b138_actor_id, @b138_time, @b138_time),
  (900000000000138445, 'client-onboarding-c21', 'CLIENT', 'POST', '/api/client/onboarding/v1/applications/{applicationId}/withdrawal', '/client-onboarding/v1/applications/{applicationId}/withdrawal', @b138_client_target, 'CLIENT_JWT', 'HUMAN_AUTHENTICATED', NULL, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @b138_actor_id, @b138_actor_id, @b138_time, @b138_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000138581, 900000000000138441, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000138582, 900000000000138442, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000138583, 900000000000138443, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000138584, 900000000000138443, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000138585, 900000000000138443, 'content-type', 'FORWARD_REQUEST'),
  (900000000000138586, 900000000000138443, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000138587, 900000000000138443, 'origin', 'FORWARD_REQUEST'),
  (900000000000138588, 900000000000138444, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000138589, 900000000000138445, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000138590, 900000000000138445, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000138591, 900000000000138445, 'content-type', 'FORWARD_REQUEST'),
  (900000000000138592, 900000000000138445, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000138593, 900000000000138445, 'origin', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000138651, 900000000000138442, 'applicationId'),
  (900000000000138652, 900000000000138442, 'jurisdiction'),
  (900000000000138653, 900000000000138442, 'partyType'),
  (900000000000138654, 900000000000138442, 'organizationType');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000138901, 900000000000138441, 'RATE_LIMIT', 'SHARED_ROUTE', 600, 600, 60000, NULL),
  (900000000000138902, 900000000000138441, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000138903, 900000000000138441, 'RATE_LIMIT', 'CLIENT_IP', 120, 120, 60000, NULL),
  (900000000000138904, 900000000000138441, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 10),
  (900000000000138905, 900000000000138441, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 60, 60, 60000, NULL),
  (900000000000138906, 900000000000138441, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 5),
  (900000000000138907, 900000000000138442, 'RATE_LIMIT', 'SHARED_ROUTE', 600, 600, 60000, NULL),
  (900000000000138908, 900000000000138442, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000138909, 900000000000138442, 'RATE_LIMIT', 'CLIENT_IP', 120, 120, 60000, NULL),
  (900000000000138910, 900000000000138442, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 10),
  (900000000000138911, 900000000000138442, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 60, 60, 60000, NULL),
  (900000000000138912, 900000000000138442, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 5),
  (900000000000138913, 900000000000138443, 'RATE_LIMIT', 'SHARED_ROUTE', 600, 600, 60000, NULL),
  (900000000000138914, 900000000000138443, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000138915, 900000000000138443, 'RATE_LIMIT', 'CLIENT_IP', 120, 120, 60000, NULL),
  (900000000000138916, 900000000000138443, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 10),
  (900000000000138917, 900000000000138443, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 60, 60, 60000, NULL),
  (900000000000138918, 900000000000138443, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 5),
  (900000000000138919, 900000000000138444, 'RATE_LIMIT', 'SHARED_ROUTE', 600, 600, 60000, NULL),
  (900000000000138920, 900000000000138444, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000138921, 900000000000138444, 'RATE_LIMIT', 'CLIENT_IP', 120, 120, 60000, NULL),
  (900000000000138922, 900000000000138444, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 10),
  (900000000000138923, 900000000000138444, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 60, 60, 60000, NULL),
  (900000000000138924, 900000000000138444, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 5),
  (900000000000138925, 900000000000138445, 'RATE_LIMIT', 'SHARED_ROUTE', 600, 600, 60000, NULL),
  (900000000000138926, 900000000000138445, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000138927, 900000000000138445, 'RATE_LIMIT', 'CLIENT_IP', 120, 120, 60000, NULL),
  (900000000000138928, 900000000000138445, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 10),
  (900000000000138929, 900000000000138445, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 60, 60, 60000, NULL),
  (900000000000138930, 900000000000138445, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 5);

COMMIT;
