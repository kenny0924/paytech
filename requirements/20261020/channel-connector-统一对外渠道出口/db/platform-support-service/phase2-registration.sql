-- 统一对外渠道出口第二段：Platform 注册数据（块 129）mor-test 增量 DML（只在 mor-test mor_platform 执行，root，客户端须 utf8mb4）
-- 状态：未执行；执行另行授权（阶段 7）。块 129 的唯一执行来源是 paytech/platform-support/docs/database/sql/platform-support-initial-data.sql
-- 末尾同名块，本文件第 2 段与其逐字一致（初始化文件内为休眠形态），第 3 段为 mor-test 激活。
-- 内容：新增权限 CHANNEL_CALL_GRANT_MANAGE（绑定 PLATFORM_ADMIN）、按钮「调用授权」、4 条 Route
-- （调用授权列表 / 新建 / 启停，渠道出站地址整组替换）及其请求头、Query 白名单、限流；渠道列表 Route 增加 channelType Query；
-- 停用接入方 5 条 CRUD Route（71006–71010）与「接入方」页面资源及其按钮（71303、71305）；菜单「渠道与账号」改名「渠道」。
-- 前置：
--   1. channel-connector 已完成第二段数据迁移，channel-connector、operations-console-bff、operations-console-web 已是第二段版本
--      （新 Route 指向的 BFF 接口必须先存在；停用的接入方 CRUD 在 BFF 第二段版本中已下线）。
--   2. 第 1 段只读核对全部符合期望值；任一不符停止，不执行第 2、3 段。
-- 可重复性：第 2 段插入使用固定主键，不可重复执行（重复执行会因主键冲突失败并整段回滚）；第 2 段的 UPDATE 与第 3 段的激活
-- 都带原状态条件，重复执行影响 0 行。
-- 执行后：必须在 Operations「网关配置」重新发布 Gateway Snapshot，新 Route 与停用才对动态 Gateway 生效（本文件不发布）。
-- 回滚：第 3 段反向（新 Route、按钮置 DISABLED，授权置 REVOKED）；第 2 段按块头注释的 ID 段删除子表到父表，
--       92302 显示名恢复「渠道与账号」，71006–71010、71303、71305 恢复 ACTIVE（各 version+1）；然后递增 PLATFORM_ADMIN 版本并重新发布 Snapshot。

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 第 1 段：只读核对（期望值写在 k 中）
-- ---------------------------------------------------------------------------
SELECT 'b129_permission(=0)' AS k, COUNT(*) AS v FROM t_permission WHERE permission_id BETWEEN 900000000000129000 AND 900000000000129999 OR permission_code = 'CHANNEL_CALL_GRANT_MANAGE' OR (resource_type = 'CHANNEL_CALL_GRANT' AND action_code = 'MANAGE')
UNION ALL SELECT 'b129_role_permission(=0)', COUNT(*) FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000129000 AND 900000000000129999
UNION ALL SELECT 'b129_console_resource(=0)', COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000129000 AND 900000000000129999 OR resource_code = 'OPERATIONS_CHANNEL_CALL_GRANT_MANAGE'
UNION ALL SELECT 'b129_gateway_route(=0)', COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000129000 AND 900000000000129999
  OR route_code IN ('operations-channel-call-grant-list', 'operations-channel-call-grant-create', 'operations-channel-call-grant-status', 'operations-channel-channel-endpoints')
  OR external_path_template IN ('/api/operations/channel-admin/v1/channel-call-grants', '/api/operations/channel-admin/v1/channel-call-grants/{callGrantId}/status', '/api/operations/channel-admin/v1/channels/{channelId}/endpoints')
UNION ALL SELECT 'b129_route_header(=0)', COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000129000 AND 900000000000129999
UNION ALL SELECT 'b129_query_param(=0)', COUNT(*) FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000129000 AND 900000000000129999 OR (route_id = 900000000000092401 AND parameter_name = 'channelType')
UNION ALL SELECT 'b129_guard_rule(=0)', COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000129000 AND 900000000000129999
UNION ALL SELECT 'b92_read_manage_permission_active(=2)', COUNT(*) FROM t_permission WHERE permission_id IN (900000000000092101, 900000000000092102) AND status = 'ACTIVE'
UNION ALL SELECT 'b92_channel_menu_active(=1，名称 渠道与账号)', COUNT(*) FROM t_console_resource WHERE resource_id = 900000000000092302 AND status = 'ACTIVE' AND display_name = '渠道与账号'
UNION ALL SELECT 'b92_channel_list_route_active(=1)', COUNT(*) FROM t_gateway_route WHERE route_id = 900000000000092401 AND status = 'ACTIVE'
UNION ALL SELECT 'b92_admin_grants_active(=2)', COUNT(*) FROM t_role_permission WHERE role_id = 900000000000000510 AND permission_id IN (900000000000092101, 900000000000092102) AND status = 'ACTIVE'
UNION ALL SELECT 'b71_integration_crud_routes(=5，记录其中 ACTIVE 数)', COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000071006 AND 900000000000071010
  AND route_code IN ('operations-external-api-integration-list', 'operations-external-api-integration-detail', 'operations-external-api-integration-create', 'operations-external-api-integration-update', 'operations-external-api-integration-delete')
UNION ALL SELECT 'b71_integration_crud_routes_active(记录；第 2 段停用这些行)', COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000071006 AND 900000000000071010 AND status = 'ACTIVE'
UNION ALL SELECT 'b71_integration_resources(=2，记录其中 ACTIVE 数)', COUNT(*) FROM t_console_resource WHERE resource_id IN (900000000000071303, 900000000000071305)
  AND resource_code IN ('OPERATIONS_EXTERNAL_INTEGRATIONS', 'OPERATIONS_EXTERNAL_INTEGRATION_MANAGE')
UNION ALL SELECT 'b71_integration_resources_active(记录；第 2 段停用这些行)', COUNT(*) FROM t_console_resource WHERE resource_id IN (900000000000071303, 900000000000071305) AND status = 'ACTIVE'
UNION ALL SELECT 'target_900000000000000001(=1)', COUNT(*) FROM t_gateway_target WHERE target_id = 900000000000000001
UNION ALL SELECT 'platform_admin_version(记录；第 3 段后 +1)', version FROM t_role WHERE role_id = 900000000000000510;

-- ---------------------------------------------------------------------------
-- 第 2 段：块 129（与 platform-support-initial-data.sql 末尾同名块逐字一致）
-- ---------------------------------------------------------------------------
-- Operations channel console phase 2: service channel call grants and channel outbound endpoints (2026-10-22,
-- requirements/20261020/channel-connector-统一对外渠道出口, task P2 section 5).
-- Self-contained block: on an environment already holding the channel console block 92 and the external API console block 71
-- above, apply ONLY this block. Dormant like block 92: the new permission is ACTIVE, the administrator grant REVOKED, the new
-- button and routes DISABLED; mor-test applies the same rows plus activation in
-- requirements/20261020/channel-connector-统一对外渠道出口/db/platform-support-service/phase2-registration.sql.
-- New permission CHANNEL_CALL_GRANT_MANAGE (resource CHANNEL_CALL_GRANT, action MANAGE) guards creating, enabling and disabling
-- grants (caller service x channel x standard API) and backs the new button 调用授权 on the channel page (component
-- OPERATIONS_CHANNEL_CHANNELS); the grant list reuses CHANNEL_CONFIG_READ (900000000000092101) and the endpoint replacement
-- reuses CHANNEL_CONFIG_MANAGE (900000000000092102). Headers, query whitelist and guard rules copy the block 92 GET / write
-- routes; the endpoint replacement takes a 65536-byte body (16 endpoints x 512-character base URLs exceed 8192).
-- The channel list route 900000000000092401 gains the optional channelType query parameter (PAYMENT / SERVICE).
-- The five integration CRUD routes (900000000000071006-900000000000071010) and the integration page resources
-- OPERATIONS_EXTERNAL_INTEGRATIONS (900000000000071303) with its button (900000000000071305) are disabled, not deleted: channels
-- replace integrations; the /external-api-admin/v1/integrations/{integrationCode}/... routes stay. The menu 渠道与账号
-- (900000000000092302) is renamed 渠道.
-- Rows: permission 1, role_permission 1, console_resource 1 (+3 updates), gateway_route 4 (+5 updates), route_header 20,
-- query_param 7, guard_rule 24 (58 inserted). ID block 129: 1291xx permission, 1292xx role_permission, 1293xx console_resource,
-- 1294xx route, 1295xx route_header, 1290xx query_param, 1297xx guard_rule.
-- Rollback: delete the rows by the 900000000000129001-900000000000129999 IDs below (children first: guard_rule, query_param,
-- route_header, gateway_route, console_resource, role_permission, permission); restore display_name 渠道与账号 on 92302 and the
-- previous status of 71006-71010, 71303 and 71305.
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @channel_grant_time = UTC_TIMESTAMP(6);
SET @channel_grant_actor_id = 1;
SET @channel_grant_target_id = 900000000000000001;
SET @channel_grant_role_id = 900000000000000510;

INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000129101, 'CHANNEL_CALL_GRANT_MANAGE', 'CHANNEL_CALL_GRANT', 'MANAGE', 'HIGH', 'ACTIVE', 1, @channel_grant_time, @channel_grant_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (900000000000129201, @channel_grant_role_id, 900000000000129101, 'REVOKED', 1, @channel_grant_time, @channel_grant_time);

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000129301, 'OPERATIONS_CHANNEL_CALL_GRANT_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000092302, '调用授权', NULL, NULL, 900000000000129101, 30, 'DISABLED', 1, @channel_grant_time, @channel_grant_time);

UPDATE t_console_resource SET display_name = '渠道', version = version + 1, update_time = @channel_grant_time
 WHERE resource_id = 900000000000092302 AND display_name = '渠道与账号';
UPDATE t_console_resource SET status = 'DISABLED', version = version + 1, update_time = @channel_grant_time
 WHERE resource_id IN (900000000000071303, 900000000000071305) AND status = 'ACTIVE';
UPDATE t_gateway_route SET status = 'DISABLED', version = version + 1, updated_by = @channel_grant_actor_id, update_time = @channel_grant_time
 WHERE route_id BETWEEN 900000000000071006 AND 900000000000071010 AND status = 'ACTIVE';

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000129401, 'operations-channel-call-grant-list', 'OPERATIONS', 'GET', '/api/operations/channel-admin/v1/channel-call-grants', '/channel-admin/v1/channel-call-grants', @channel_grant_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000092101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @channel_grant_actor_id, @channel_grant_actor_id, @channel_grant_time, @channel_grant_time),
  (900000000000129402, 'operations-channel-call-grant-create', 'OPERATIONS', 'POST', '/api/operations/channel-admin/v1/channel-call-grants', '/channel-admin/v1/channel-call-grants', @channel_grant_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000129101, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @channel_grant_actor_id, @channel_grant_actor_id, @channel_grant_time, @channel_grant_time),
  (900000000000129403, 'operations-channel-call-grant-status', 'OPERATIONS', 'POST', '/api/operations/channel-admin/v1/channel-call-grants/{callGrantId}/status', '/channel-admin/v1/channel-call-grants/{callGrantId}/status', @channel_grant_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000129101, NULL, 'DROP', 'NONE', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @channel_grant_actor_id, @channel_grant_actor_id, @channel_grant_time, @channel_grant_time),
  (900000000000129404, 'operations-channel-channel-endpoints', 'OPERATIONS', 'PUT', '/api/operations/channel-admin/v1/channels/{channelId}/endpoints', '/channel-admin/v1/channels/{channelId}/endpoints', @channel_grant_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000092102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @channel_grant_actor_id, @channel_grant_actor_id, @channel_grant_time, @channel_grant_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000129501, 900000000000129401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000129502, 900000000000129401, 'origin', 'FORWARD_REQUEST'),
  (900000000000129503, 900000000000129402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000129504, 900000000000129402, 'origin', 'FORWARD_REQUEST'),
  (900000000000129505, 900000000000129402, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000129506, 900000000000129402, 'content-type', 'FORWARD_REQUEST'),
  (900000000000129507, 900000000000129402, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000129508, 900000000000129402, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000129509, 900000000000129403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000129510, 900000000000129403, 'origin', 'FORWARD_REQUEST'),
  (900000000000129511, 900000000000129403, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000129512, 900000000000129403, 'content-type', 'FORWARD_REQUEST'),
  (900000000000129513, 900000000000129403, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000129514, 900000000000129403, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000129515, 900000000000129404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000129516, 900000000000129404, 'origin', 'FORWARD_REQUEST'),
  (900000000000129517, 900000000000129404, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000129518, 900000000000129404, 'content-type', 'FORWARD_REQUEST'),
  (900000000000129519, 900000000000129404, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000129520, 900000000000129404, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000129001, 900000000000129401, 'channelId'),
  (900000000000129002, 900000000000129401, 'callerService'),
  (900000000000129003, 900000000000129401, 'standardApiCode'),
  (900000000000129004, 900000000000129401, 'grantStatus'),
  (900000000000129005, 900000000000129401, 'page'),
  (900000000000129006, 900000000000129401, 'size'),
  (900000000000129007, 900000000000092401, 'channelType');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000129701, 900000000000129401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000129702, 900000000000129401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000129703, 900000000000129401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000129704, 900000000000129401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000129705, 900000000000129401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000129706, 900000000000129401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000129707, 900000000000129402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000129708, 900000000000129402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000129709, 900000000000129402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000129710, 900000000000129402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000129711, 900000000000129402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000129712, 900000000000129402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000129713, 900000000000129403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000129714, 900000000000129403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000129715, 900000000000129403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000129716, 900000000000129403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000129717, 900000000000129403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000129718, 900000000000129403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000129719, 900000000000129404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000129720, 900000000000129404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000129721, 900000000000129404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000129722, 900000000000129404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000129723, 900000000000129404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000129724, 900000000000129404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;

-- ---------------------------------------------------------------------------
-- 第 3 段：mor-test 激活（新增 4 条 Route、按钮「调用授权」与 PLATFORM_ADMIN 授权），递增 PLATFORM_ADMIN 角色版本使会话重新计算导航。
-- 预期影响行数：4 / 1 / 1 / 1。
-- ---------------------------------------------------------------------------
START TRANSACTION;
SET @channel_grant_act_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status = 'ACTIVE', version = version + 1, update_time = @channel_grant_act_time
 WHERE route_id BETWEEN 900000000000129401 AND 900000000000129404 AND status = 'DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status = 'ACTIVE', version = version + 1, update_time = @channel_grant_act_time
 WHERE resource_id = 900000000000129301 AND status = 'DISABLED';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status = 'ACTIVE', version = version + 1, update_time = @channel_grant_act_time
 WHERE role_permission_id = 900000000000129201 AND status = 'REVOKED';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version = version + 1, update_time = @channel_grant_act_time WHERE role_id = 900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
