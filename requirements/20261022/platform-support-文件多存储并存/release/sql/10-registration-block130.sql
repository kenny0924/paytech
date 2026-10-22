-- 存储管理页注册块 130（休眠）：mor-test 阶段 7 第 5 步在 mor_platform 执行（root，客户端 utf8mb4）。
-- 内容逐字取自 paytech/platform-support/docs/database/sql/platform-support-initial-data.sql 末尾「Operations file storage console」块；
-- 自带事务，152 行（权限 2、授权 2、资源 2、路由 12、路由头 62、限流 72）。前置：00-precheck 块 130 为空。
-- 回滚：块头所列 ID 范围 900000000000130101—900000000000130799，子表先删（guard_rule → route_header → gateway_route →
--   console_resource → role_permission → permission）；激活状态先用 ../test-deactivate.sql 关闭。
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- Operations file storage console (2026-10-22, requirements/20261022/platform-support-文件多存储并存, R7).
-- Self-contained block: on an environment already initialized by platform-support-initial-data.sql, apply ONLY this block.
-- Requires the file storage tables (changes/2026-10-22-file-storage-management.sql) for the Platform code it activates.
-- Blocks 127 (workflow), 128 (webhook) and 129 (channel-connector) are reserved by parallel requirements; this block does
-- not depend on them and may be applied before or after them.
-- Dormant like earlier Operations features: permissions ACTIVE, administrator grants REVOKED, menu/button/routes
-- DISABLED; activate them in a separate reviewed change. BFF routes forward to operations-console-bff
-- (/storage-admin/v1/**), which calls FileStorageManagementApi on platform-support-service; operator identity comes from
-- the Gateway actor headers. Create-instance and replace-credential bodies carry storage secrets: Gateway access logs never
-- record bodies, and operations-console-bff lists /storage-admin as a metadata-only logging prefix. The bucket check
-- route carries no body (body limit 0, no content-type header).
-- The page sits under 平台配置 (OPERATIONS_PLATFORM_CONFIGURATION 900000000000000562), sort 80 after 币种.
-- Rows: permission 2, role_permission 2, console_resource 2, gateway_route 12, route_header 62, guard_rule 72 (152).
-- ID block 130: 1301xx permission, 1302xx role_permission, 1303xx console_resource, 1304xx route,
-- 1305xx route_header, 1307xx guard_rule.
-- Rollback: delete the rows by the 900000000000130101-900000000000130799 IDs below (children first: guard_rule,
-- route_header, gateway_route, console_resource, role_permission, permission).
-- Activation (NOT executed here; run as a separate reviewed change, expected rows 12 / 2 / 2 / 1):
--   START TRANSACTION;
--   SET @file_storage_activation_time = UTC_TIMESTAMP(6);
--   UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@file_storage_activation_time
--    WHERE route_id BETWEEN 900000000000130401 AND 900000000000130412 AND status='DISABLED';
--   UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@file_storage_activation_time
--    WHERE resource_id BETWEEN 900000000000130301 AND 900000000000130302 AND status='DISABLED';
--   UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@file_storage_activation_time
--    WHERE role_permission_id BETWEEN 900000000000130201 AND 900000000000130202 AND status='REVOKED';
--   UPDATE t_role SET version=version+1, update_time=@file_storage_activation_time WHERE role_id=900000000000000510;
--   COMMIT;
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @file_storage_console_time = UTC_TIMESTAMP(6);
SET @file_storage_console_actor_id = 1;
SET @file_storage_console_target_id = 900000000000000001;
SET @file_storage_console_role_id = 900000000000000510;

INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000130101, 'FILE_STORAGE_READ', 'FILE_STORAGE', 'READ', 'LOW', 'ACTIVE', 1, @file_storage_console_time, @file_storage_console_time),
  (900000000000130102, 'FILE_STORAGE_MANAGE', 'FILE_STORAGE', 'MANAGE', 'HIGH', 'ACTIVE', 1, @file_storage_console_time, @file_storage_console_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (900000000000130201, @file_storage_console_role_id, 900000000000130101, 'REVOKED', 1, @file_storage_console_time, @file_storage_console_time),
  (900000000000130202, @file_storage_console_role_id, 900000000000130102, 'REVOKED', 1, @file_storage_console_time, @file_storage_console_time);

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000130301, 'OPERATIONS_FILE_STORAGE', 'OPERATIONS', 'MENU', 900000000000000562, '存储管理', '/file-storage', 'OPERATIONS_FILE_STORAGE', 900000000000130101, 80, 'DISABLED', 1, @file_storage_console_time, @file_storage_console_time),
  (900000000000130302, 'OPERATIONS_FILE_STORAGE_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000130301, '维护', NULL, NULL, 900000000000130102, 10, 'DISABLED', 1, @file_storage_console_time, @file_storage_console_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000130401, 'operations-file-storage-instance-list', 'OPERATIONS', 'GET', '/api/operations/storage-admin/v1/instances', '/storage-admin/v1/instances', @file_storage_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000130101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @file_storage_console_actor_id, @file_storage_console_actor_id, @file_storage_console_time, @file_storage_console_time),
  (900000000000130402, 'operations-file-storage-instance-create', 'OPERATIONS', 'POST', '/api/operations/storage-admin/v1/instances', '/storage-admin/v1/instances', @file_storage_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000130102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @file_storage_console_actor_id, @file_storage_console_actor_id, @file_storage_console_time, @file_storage_console_time),
  (900000000000130403, 'operations-file-storage-instance-update', 'OPERATIONS', 'PUT', '/api/operations/storage-admin/v1/instances/{instanceCode}', '/storage-admin/v1/instances/{instanceCode}', @file_storage_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000130102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @file_storage_console_actor_id, @file_storage_console_actor_id, @file_storage_console_time, @file_storage_console_time),
  (900000000000130404, 'operations-file-storage-instance-credential', 'OPERATIONS', 'PUT', '/api/operations/storage-admin/v1/instances/{instanceCode}/credential', '/storage-admin/v1/instances/{instanceCode}/credential', @file_storage_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000130102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @file_storage_console_actor_id, @file_storage_console_actor_id, @file_storage_console_time, @file_storage_console_time),
  (900000000000130405, 'operations-file-storage-instance-status', 'OPERATIONS', 'PATCH', '/api/operations/storage-admin/v1/instances/{instanceCode}/status', '/storage-admin/v1/instances/{instanceCode}/status', @file_storage_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000130102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @file_storage_console_actor_id, @file_storage_console_actor_id, @file_storage_console_time, @file_storage_console_time),
  (900000000000130406, 'operations-file-storage-bucket-create', 'OPERATIONS', 'POST', '/api/operations/storage-admin/v1/instances/{instanceCode}/buckets', '/storage-admin/v1/instances/{instanceCode}/buckets', @file_storage_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000130102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @file_storage_console_actor_id, @file_storage_console_actor_id, @file_storage_console_time, @file_storage_console_time),
  (900000000000130407, 'operations-file-storage-bucket-check', 'OPERATIONS', 'POST', '/api/operations/storage-admin/v1/instances/{instanceCode}/buckets/{bucketName}/check', '/storage-admin/v1/instances/{instanceCode}/buckets/{bucketName}/check', @file_storage_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000130102, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @file_storage_console_actor_id, @file_storage_console_actor_id, @file_storage_console_time, @file_storage_console_time),
  (900000000000130408, 'operations-file-storage-bucket-status', 'OPERATIONS', 'PATCH', '/api/operations/storage-admin/v1/instances/{instanceCode}/buckets/{bucketName}/status', '/storage-admin/v1/instances/{instanceCode}/buckets/{bucketName}/status', @file_storage_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000130102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @file_storage_console_actor_id, @file_storage_console_actor_id, @file_storage_console_time, @file_storage_console_time),
  (900000000000130409, 'operations-file-storage-route-list', 'OPERATIONS', 'GET', '/api/operations/storage-admin/v1/routes', '/storage-admin/v1/routes', @file_storage_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000130101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @file_storage_console_actor_id, @file_storage_console_actor_id, @file_storage_console_time, @file_storage_console_time),
  (900000000000130410, 'operations-file-storage-route-create', 'OPERATIONS', 'POST', '/api/operations/storage-admin/v1/routes', '/storage-admin/v1/routes', @file_storage_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000130102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @file_storage_console_actor_id, @file_storage_console_actor_id, @file_storage_console_time, @file_storage_console_time),
  (900000000000130411, 'operations-file-storage-route-update', 'OPERATIONS', 'PUT', '/api/operations/storage-admin/v1/routes/{sourceSystem}', '/storage-admin/v1/routes/{sourceSystem}', @file_storage_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000130102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @file_storage_console_actor_id, @file_storage_console_actor_id, @file_storage_console_time, @file_storage_console_time),
  (900000000000130412, 'operations-file-storage-route-delete', 'OPERATIONS', 'DELETE', '/api/operations/storage-admin/v1/routes/{sourceSystem}', '/storage-admin/v1/routes/{sourceSystem}', @file_storage_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000130102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @file_storage_console_actor_id, @file_storage_console_actor_id, @file_storage_console_time, @file_storage_console_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000130501, 900000000000130401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000130502, 900000000000130401, 'origin', 'FORWARD_REQUEST'),
  (900000000000130503, 900000000000130402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000130504, 900000000000130402, 'origin', 'FORWARD_REQUEST'),
  (900000000000130505, 900000000000130402, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000130506, 900000000000130402, 'content-type', 'FORWARD_REQUEST'),
  (900000000000130507, 900000000000130402, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000130508, 900000000000130402, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000130509, 900000000000130403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000130510, 900000000000130403, 'origin', 'FORWARD_REQUEST'),
  (900000000000130511, 900000000000130403, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000130512, 900000000000130403, 'content-type', 'FORWARD_REQUEST'),
  (900000000000130513, 900000000000130403, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000130514, 900000000000130403, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000130515, 900000000000130404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000130516, 900000000000130404, 'origin', 'FORWARD_REQUEST'),
  (900000000000130517, 900000000000130404, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000130518, 900000000000130404, 'content-type', 'FORWARD_REQUEST'),
  (900000000000130519, 900000000000130404, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000130520, 900000000000130404, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000130521, 900000000000130405, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000130522, 900000000000130405, 'origin', 'FORWARD_REQUEST'),
  (900000000000130523, 900000000000130405, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000130524, 900000000000130405, 'content-type', 'FORWARD_REQUEST'),
  (900000000000130525, 900000000000130405, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000130526, 900000000000130405, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000130527, 900000000000130406, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000130528, 900000000000130406, 'origin', 'FORWARD_REQUEST'),
  (900000000000130529, 900000000000130406, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000130530, 900000000000130406, 'content-type', 'FORWARD_REQUEST'),
  (900000000000130531, 900000000000130406, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000130532, 900000000000130406, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000130533, 900000000000130407, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000130534, 900000000000130407, 'origin', 'FORWARD_REQUEST'),
  (900000000000130535, 900000000000130407, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000130536, 900000000000130407, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000130537, 900000000000130408, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000130538, 900000000000130408, 'origin', 'FORWARD_REQUEST'),
  (900000000000130539, 900000000000130408, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000130540, 900000000000130408, 'content-type', 'FORWARD_REQUEST'),
  (900000000000130541, 900000000000130408, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000130542, 900000000000130408, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000130543, 900000000000130409, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000130544, 900000000000130409, 'origin', 'FORWARD_REQUEST'),
  (900000000000130545, 900000000000130410, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000130546, 900000000000130410, 'origin', 'FORWARD_REQUEST'),
  (900000000000130547, 900000000000130410, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000130548, 900000000000130410, 'content-type', 'FORWARD_REQUEST'),
  (900000000000130549, 900000000000130410, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000130550, 900000000000130410, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000130551, 900000000000130411, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000130552, 900000000000130411, 'origin', 'FORWARD_REQUEST'),
  (900000000000130553, 900000000000130411, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000130554, 900000000000130411, 'content-type', 'FORWARD_REQUEST'),
  (900000000000130555, 900000000000130411, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000130556, 900000000000130411, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000130557, 900000000000130412, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000130558, 900000000000130412, 'origin', 'FORWARD_REQUEST'),
  (900000000000130559, 900000000000130412, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000130560, 900000000000130412, 'content-type', 'FORWARD_REQUEST'),
  (900000000000130561, 900000000000130412, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000130562, 900000000000130412, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000130701, 900000000000130401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000130702, 900000000000130401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000130703, 900000000000130401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000130704, 900000000000130401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000130705, 900000000000130401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000130706, 900000000000130401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000130707, 900000000000130402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000130708, 900000000000130402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000130709, 900000000000130402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000130710, 900000000000130402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000130711, 900000000000130402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000130712, 900000000000130402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000130713, 900000000000130403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000130714, 900000000000130403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000130715, 900000000000130403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000130716, 900000000000130403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000130717, 900000000000130403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000130718, 900000000000130403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000130719, 900000000000130404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000130720, 900000000000130404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000130721, 900000000000130404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000130722, 900000000000130404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000130723, 900000000000130404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000130724, 900000000000130404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000130725, 900000000000130405, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000130726, 900000000000130405, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000130727, 900000000000130405, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000130728, 900000000000130405, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000130729, 900000000000130405, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000130730, 900000000000130405, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000130731, 900000000000130406, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000130732, 900000000000130406, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000130733, 900000000000130406, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000130734, 900000000000130406, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000130735, 900000000000130406, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000130736, 900000000000130406, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000130737, 900000000000130407, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000130738, 900000000000130407, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000130739, 900000000000130407, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000130740, 900000000000130407, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000130741, 900000000000130407, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000130742, 900000000000130407, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000130743, 900000000000130408, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000130744, 900000000000130408, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000130745, 900000000000130408, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000130746, 900000000000130408, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000130747, 900000000000130408, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000130748, 900000000000130408, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000130749, 900000000000130409, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000130750, 900000000000130409, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000130751, 900000000000130409, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000130752, 900000000000130409, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000130753, 900000000000130409, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000130754, 900000000000130409, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000130755, 900000000000130410, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000130756, 900000000000130410, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000130757, 900000000000130410, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000130758, 900000000000130410, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000130759, 900000000000130410, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000130760, 900000000000130410, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000130761, 900000000000130411, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000130762, 900000000000130411, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000130763, 900000000000130411, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000130764, 900000000000130411, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000130765, 900000000000130411, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000130766, 900000000000130411, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000130767, 900000000000130412, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000130768, 900000000000130412, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000130769, 900000000000130412, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000130770, 900000000000130412, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000130771, 900000000000130412, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000130772, 900000000000130412, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;
