-- Platform 注册块 139（参考数据目录：运营控制台参考数据页与分类 MCC 搜索），
-- 只在 mor_platform 执行，客户端须 utf8mb4。由 gen_block139.py 依 block139-spec.json 生成，勿手改。
-- 来源：requirements/20261027/platform-support-参考数据目录与client-master迁移/technique.md 第 8.5 节（块 139）与 S-07；ops-fe 2026-10-27。
-- 前置：本需求 platform-support、operations-console-bff 新镜像已发布（休眠注册本身不依赖镜像）。
-- 执行后为休眠状态：permission ACTIVE、PLATFORM_ADMIN 授权 REVOKED、菜单 / 按钮 / 路由 DISABLED；启用见 block139-activate.sql。
-- 预期插入：permission 1、role_permission 1、console_resource 1、gateway_route 5、route_header 10、query_param 11、guard_rule 30，共 59 行。
-- 执行前：block139-precheck.sql 全部符合期望；发布前须用候选 Platform JAR 跑 assets/harness/block_compile_check.sh（五态 COMPILE OK）。
-- 新权限：REFERENCE_DATA_READ (REFERENCE_DATA, READ, LOW)。
-- 既有对象经变量引用：@b139_perm_client_onboarding_classification_edit。
-- ID（本文件）：permission 900000000000139101—900000000000139101、role_permission 900000000000139201—900000000000139201、console_resource 900000000000139301—900000000000139301、gateway_route 900000000000139401—900000000000139405、route_header 900000000000139501—900000000000139510、query_param 900000000000139601—900000000000139611、guard_rule 900000000000139901—900000000000139930。回滚只删以上区间（block139-rollback.sql）。
-- 本文件为 initial-data 变体：与 registration-block139.sql 行内容相同，只把失败即中止的守卫换成条件插入；逐字追加到 paytech/platform-support/docs/database/sql/platform-support-initial-data.sql（自分隔线起）。
-- ---------------------------------------------------------------------------
-- Reference data catalog, Operations part (2026-10-27, requirements/20261027/platform-support-参考数据目录与client-master迁移; technique.md 8.5).
-- Self-contained block: on an environment already initialized by platform-support-initial-data.sql, apply ONLY this block.
-- Dormant like earlier Operations features: permission ACTIVE, administrator grant REVOKED, menu and routes DISABLED.
-- New permission REFERENCE_DATA_READ (LOW) guards the read-only Reference Data page under 平台配置 (sort 95) and its four routes
-- (/reference-data-admin/v1/countries, country-subdivisions, industries, mccs). The classification MCC search
-- (/client-onboarding-admin/v1/mccs) reuses CLIENT_ONBOARDING_CLASSIFICATION_EDIT, resolved by code into a variable.
-- Shapes copy block 138 OPERATIONS GET routes: authorization + origin headers, body 0, SHARED_ROUTE 300/min + 50,
-- CLIENT_IP 30/min + 3, principal 30/min + 2 concurrent. The page loads its four lists one after another (principal concurrency 2).
-- Rows: permission 1, role_permission 1, console_resource 1, gateway_route 5, route_header 10, query_param 11, guard_rule 30 (59). IDs: see the Chinese header above.
-- initial-data variant: the existing objects below are created through the Platform management API after this file
-- runs, so on a fresh environment they are absent and every INSERT here is skipped (@b139_apply is NULL).
-- On an environment that already has them, all rows are inserted together. To register later on a fresh
-- environment, run requirements/20261027/platform-support-参考数据目录与client-master迁移/db/platform-support/registration-block139.sql (fails closed) after the onboarding registration.
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @b139_time = UTC_TIMESTAMP(6);
SET @b139_actor_id = 1;
SET @b139_role_id = 900000000000000510;
-- 既有对象（各环境 ID 不同）：先取到变量；任一为 NULL 时下一句产生两行子查询 → ERROR 1242 中止，事务不提交。
SET @b139_perm_client_onboarding_classification_edit = (SELECT permission_id FROM t_permission WHERE permission_code = 'CLIENT_ONBOARDING_CLASSIFICATION_EDIT');
SET @b139_apply = IF(@b139_perm_client_onboarding_classification_edit IS NOT NULL, 1, NULL);

INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) SELECT * FROM (VALUES
  ROW(900000000000139101, 'REFERENCE_DATA_READ', 'REFERENCE_DATA', 'READ', 'LOW', 'ACTIVE', 1, @b139_time, @b139_time)) AS b139_rows WHERE @b139_apply = 1;

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) SELECT * FROM (VALUES
  ROW(900000000000139201, @b139_role_id, 900000000000139101, 'REVOKED', 1, @b139_time, @b139_time)) AS b139_rows WHERE @b139_apply = 1;

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) SELECT * FROM (VALUES
  ROW(900000000000139301, 'OPERATIONS_REFERENCE_DATA', 'OPERATIONS', 'MENU', 900000000000000562, '参考数据', '/reference-data', 'OPERATIONS_REFERENCE_DATA', 900000000000139101, 95, 'DISABLED', 1, @b139_time, @b139_time)) AS b139_rows WHERE @b139_apply = 1;

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) SELECT * FROM (VALUES
  ROW(900000000000139401, 'operations-reference-data-countries', 'OPERATIONS', 'GET', '/api/operations/reference-data-admin/v1/countries', '/reference-data-admin/v1/countries', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000139101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @b139_actor_id, @b139_actor_id, @b139_time, @b139_time),
  ROW(900000000000139402, 'operations-reference-data-subdivisions', 'OPERATIONS', 'GET', '/api/operations/reference-data-admin/v1/country-subdivisions', '/reference-data-admin/v1/country-subdivisions', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000139101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @b139_actor_id, @b139_actor_id, @b139_time, @b139_time),
  ROW(900000000000139403, 'operations-reference-data-industries', 'OPERATIONS', 'GET', '/api/operations/reference-data-admin/v1/industries', '/reference-data-admin/v1/industries', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000139101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @b139_actor_id, @b139_actor_id, @b139_time, @b139_time),
  ROW(900000000000139404, 'operations-reference-data-mccs', 'OPERATIONS', 'GET', '/api/operations/reference-data-admin/v1/mccs', '/reference-data-admin/v1/mccs', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000139101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @b139_actor_id, @b139_actor_id, @b139_time, @b139_time),
  ROW(900000000000139405, 'client-onboarding-mcc-search', 'OPERATIONS', 'GET', '/api/operations/client-onboarding-admin/v1/mccs', '/client-onboarding-admin/v1/mccs', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', @b139_perm_client_onboarding_classification_edit, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @b139_actor_id, @b139_actor_id, @b139_time, @b139_time)) AS b139_rows WHERE @b139_apply = 1;

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) SELECT * FROM (VALUES
  ROW(900000000000139501, 900000000000139401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  ROW(900000000000139502, 900000000000139401, 'origin', 'FORWARD_REQUEST'),
  ROW(900000000000139503, 900000000000139402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  ROW(900000000000139504, 900000000000139402, 'origin', 'FORWARD_REQUEST'),
  ROW(900000000000139505, 900000000000139403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  ROW(900000000000139506, 900000000000139403, 'origin', 'FORWARD_REQUEST'),
  ROW(900000000000139507, 900000000000139404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  ROW(900000000000139508, 900000000000139404, 'origin', 'FORWARD_REQUEST'),
  ROW(900000000000139509, 900000000000139405, 'authorization', 'CORS_REQUEST_ALLOWED'),
  ROW(900000000000139510, 900000000000139405, 'origin', 'FORWARD_REQUEST')) AS b139_rows WHERE @b139_apply = 1;

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) SELECT * FROM (VALUES
  ROW(900000000000139601, 900000000000139401, 'status'),
  ROW(900000000000139602, 900000000000139402, 'countryCode'),
  ROW(900000000000139603, 900000000000139402, 'status'),
  ROW(900000000000139604, 900000000000139403, 'status'),
  ROW(900000000000139605, 900000000000139404, 'keyword'),
  ROW(900000000000139606, 900000000000139404, 'status'),
  ROW(900000000000139607, 900000000000139404, 'page'),
  ROW(900000000000139608, 900000000000139404, 'size'),
  ROW(900000000000139609, 900000000000139405, 'keyword'),
  ROW(900000000000139610, 900000000000139405, 'page'),
  ROW(900000000000139611, 900000000000139405, 'size')) AS b139_rows WHERE @b139_apply = 1;

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) SELECT * FROM (VALUES
  ROW(900000000000139901, 900000000000139401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  ROW(900000000000139902, 900000000000139401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  ROW(900000000000139903, 900000000000139401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  ROW(900000000000139904, 900000000000139401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  ROW(900000000000139905, 900000000000139401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  ROW(900000000000139906, 900000000000139401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  ROW(900000000000139907, 900000000000139402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  ROW(900000000000139908, 900000000000139402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  ROW(900000000000139909, 900000000000139402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  ROW(900000000000139910, 900000000000139402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  ROW(900000000000139911, 900000000000139402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  ROW(900000000000139912, 900000000000139402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  ROW(900000000000139913, 900000000000139403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  ROW(900000000000139914, 900000000000139403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  ROW(900000000000139915, 900000000000139403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  ROW(900000000000139916, 900000000000139403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  ROW(900000000000139917, 900000000000139403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  ROW(900000000000139918, 900000000000139403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  ROW(900000000000139919, 900000000000139404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  ROW(900000000000139920, 900000000000139404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  ROW(900000000000139921, 900000000000139404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  ROW(900000000000139922, 900000000000139404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  ROW(900000000000139923, 900000000000139404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  ROW(900000000000139924, 900000000000139404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  ROW(900000000000139925, 900000000000139405, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  ROW(900000000000139926, 900000000000139405, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  ROW(900000000000139927, 900000000000139405, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  ROW(900000000000139928, 900000000000139405, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  ROW(900000000000139929, 900000000000139405, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  ROW(900000000000139930, 900000000000139405, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2)) AS b139_rows WHERE @b139_apply = 1;

COMMIT;
